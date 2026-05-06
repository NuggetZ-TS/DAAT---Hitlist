package com.example.daat.data.repository

import android.net.Uri
import com.example.daat.data.model.ChallengeStatus
import com.example.daat.data.model.Group
import com.example.daat.data.model.Snipe
import com.example.daat.data.model.SnipeChallenge
import com.example.daat.data.model.SnipeStatus
import com.example.daat.data.model.User
import com.example.daat.logic.ScoringManager
import com.example.daat.logic.VerificationUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseGameRepository : GameRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // ── Auth ──────────────────────────────────────────────────────

    override fun getCurrentUser(): Flow<User?> = callbackFlow {
        val authListener = FirebaseAuth.AuthStateListener { fa ->
            if (fa.currentUser?.uid == null) trySend(null)
        }
        auth.addAuthStateListener(authListener)
        if (auth.currentUser?.uid == null) trySend(null)

        var userListener: com.google.firebase.firestore.ListenerRegistration? = null
        val dataListener = FirebaseAuth.AuthStateListener { fa ->
            userListener?.remove()
            val uid = fa.currentUser?.uid ?: return@AuthStateListener
            userListener = db.collection("users").document(uid)
                .addSnapshotListener { snap, _ ->
                    if (snap != null && snap.exists())
                        trySend(snap.toObject(User::class.java))
                    else
                        trySend(null)
                }
        }
        auth.addAuthStateListener(dataListener)

        awaitClose {
            auth.removeAuthStateListener(authListener)
            auth.removeAuthStateListener(dataListener)
            userListener?.remove()
        }
    }

    override suspend fun signInAnonymously(): Result<Unit> = try {
        val uid = auth.signInAnonymously().await().user?.uid ?: throw Exception("Auth failed")
        db.collection("users").document(uid).set(
            User(id = uid, name = "Guest", username = "@guest_${uid.takeLast(4)}", groupIds = listOf("global"))
        ).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun signInWithGoogle(idToken: String): Result<SignInResult> = try {
        val cred = GoogleAuthProvider.getCredential(idToken, null)
        val fu = auth.signInWithCredential(cred).await().user ?: throw Exception("Google Auth failed")
        val doc = db.collection("users").document(fu.uid).get().await()
        if (doc.exists())
            Result.success(SignInResult.Success(doc.toObject(User::class.java)!!))
        else
            Result.success(SignInResult.NeedsRegistration(fu.uid, fu.email, fu.displayName))
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun completeRegistration(userId: String, username: String, name: String): Result<Unit> = try {
        db.collection("users").document(userId).set(
            User(
                id = userId,
                name = name,
                username = if (username.startsWith("@")) username else "@$username",
                groupIds = listOf("global")
            )
        ).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun signOut(): Result<Unit> { auth.signOut(); return Result.success(Unit) }

    // ── Game ──────────────────────────────────────────────────────

    override fun getCurrentTarget(userId: String): Flow<User?> = callbackFlow {
        val listener = db.collection("users").document(userId)
            .addSnapshotListener { snap, _ ->
                val tid = snap?.toObject(User::class.java)?.currentTargetId
                if (tid != null)
                    db.collection("users").document(tid).get()
                        .addOnSuccessListener { trySend(it.toObject(User::class.java)?.toPublicProfile()) }
                else trySend(null)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Leaderboard fix: Firestore compound queries (whereArrayContains + orderBy)
     * require a composite index that may not exist, causing silent empty results.
     * We fetch by groupId only and sort in-memory instead.
     */
    override fun getLeaderboard(groupId: String): Flow<List<User>> = callbackFlow {
        val listener = db.collection("users")
            .whereArrayContains("groupIds", groupId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val users = snap.documents
                    .mapNotNull { it.toObject(User::class.java)?.toPublicProfile() }
                    .sortedByDescending { it.totalScore }
                trySend(users)
            }
        awaitClose { listener.remove() }
    }

    override fun getSnipeFeed(): Flow<List<Snipe>> = callbackFlow {
        val currentUid = auth.currentUser?.uid
        val listener = db.collection("snipes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                val snipes = snap?.documents?.mapNotNull { doc ->
                    val snipe = doc.toObject(Snipe::class.java) ?: return@mapNotNull null
                    // Derive isLikedByMe from the likedBy list
                    snipe.copy(isLikedByMe = currentUid != null && snipe.likedBy.contains(currentUid))
                } ?: emptyList()
                trySend(snipes)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun updateLocation(userId: String, latitude: Double, longitude: Double): Result<Unit> = try {
        db.collection("users").document(userId).update(
            mapOf("latitude" to latitude, "longitude" to longitude, "lastLocationUpdate" to System.currentTimeMillis())
        ).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun submitSnipe(
        hunterId: String, targetId: String, imageUrl: String,
        hunterLat: Double, hunterLon: Double, hunterHeading: Double,
        capturedAt: Long, groupId: String
    ): Result<Int> = try {
        if (groupId.isNotEmpty()) {
            val group = db.collection("groups").document(groupId).get().await()
                .toObject(Group::class.java) ?: throw Exception("GROUP_NOT_FOUND")
            if (!group.members.contains(targetId)) throw Exception("NOT_IN_GROUP")
        }

        // Fetch raw target doc (with coordinates)
        val target = db.collection("users").document(targetId).get().await()
            .toObject(User::class.java) ?: throw Exception("TARGET_NOT_FOUND")

        val verification = VerificationUtils.verifySnipe(
            hunterLat = hunterLat, hunterLon = hunterLon, hunterHeading = hunterHeading,
            targetLat = target.latitude, targetLon = target.longitude, capturedAt = capturedAt
        )
        if (!verification.success) throw Exception(verification.errorCode)

        val fileName = "snipes/${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child(fileName)
        ref.putFile(Uri.parse(imageUrl)).await()
        val downloadUrl = ref.downloadUrl.await().toString()

        val hunter = db.collection("users").document(hunterId).get().await()
            .toObject(User::class.java) ?: throw Exception("HUNTER_NOT_FOUND")

        val points = ScoringManager.calculatePoints(
            distanceMeters = verification.distanceMeters,
            streak = hunter.currentStreak,
            targetAssignedAt = hunter.targetAssignedAt,
            capturedAt = capturedAt
        )

        val snipeId = UUID.randomUUID().toString()
        db.collection("snipes").document(snipeId).set(
            Snipe(
                id = snipeId, hunterId = hunterId, targetId = targetId,
                groupId = groupId, timestamp = capturedAt, imageUrl = downloadUrl,
                status = SnipeStatus.VERIFIED, pointsAwarded = points
            )
        ).await()

        db.collection("users").document(hunterId).update(
            mapOf(
                "totalScore" to (hunter.totalScore + points),
                "currentStreak" to (hunter.currentStreak + 1),
                "currentTargetId" to null,
                "latitude" to hunterLat, "longitude" to hunterLon,
                "lastLocationUpdate" to System.currentTimeMillis()
            )
        ).await()

        Result.success(points)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun assignDailyTargets(groupId: String): Result<Unit> = try {
        val group = db.collection("groups").document(groupId).get().await()
            .toObject(Group::class.java) ?: throw Exception("Group not found")
        val ids = group.members.shuffled()
        if (ids.size < 2) throw Exception("Need at least 2 members")
        db.runBatch { batch ->
            ids.forEachIndexed { i, hId ->
                batch.update(db.collection("users").document(hId),
                    mapOf("currentTargetId" to ids[(i + 1) % ids.size],
                        "targetAssignedAt" to System.currentTimeMillis()))
            }
        }.await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    /** Public profile — no coordinates. Safe for UI display. */
    override fun getUserById(userId: String): Flow<User?> = callbackFlow {
        val listener = db.collection("users").document(userId)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.toObject(User::class.java)?.toPublicProfile())
            }
        awaitClose { listener.remove() }
    }

    /**
     * Raw document — includes lat/lng. Only used internally for
     * distance/bearing calculations toward the active target.
     */
    override fun getUserByIdRaw(userId: String): Flow<User?> = callbackFlow {
        val listener = db.collection("users").document(userId)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.toObject(User::class.java))
            }
        awaitClose { listener.remove() }
    }

    /**
     * Like fix: uses a Firestore transaction to atomically toggle the
     * current user's ID in the likedBy array and keep the likes count
     * in sync. Prevents multiple likes from the same user.
     */
    override suspend fun toggleLike(snipeId: String, userId: String): Result<Unit> = try {
        val snipeRef = db.collection("snipes").document(snipeId)
        db.runTransaction { tx ->
            val snap = tx.get(snipeRef)
            @Suppress("UNCHECKED_CAST")
            val likedBy = snap.get("likedBy") as? List<String> ?: emptyList()
            if (likedBy.contains(userId)) {
                tx.update(snipeRef, "likedBy", FieldValue.arrayRemove(userId))
                tx.update(snipeRef, "likes", FieldValue.increment(-1))
            } else {
                tx.update(snipeRef, "likedBy", FieldValue.arrayUnion(userId))
                tx.update(snipeRef, "likes", FieldValue.increment(1))
            }
        }.await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    // ── Groups ────────────────────────────────────────────────────

    override fun getUserGroups(userId: String): Flow<List<Group>> = callbackFlow {
        val listener = db.collection("groups")
            .whereArrayContains("members", userId)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.mapNotNull { it.toObject(Group::class.java) } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    override suspend fun createGroup(name: String, adminId: String): Result<String> = try {
        val code = (1..6).map { ('A'..'Z').random() }.joinToString("")
        val ref = db.collection("groups").document()
        ref.set(Group(id = ref.id, name = name, inviteCode = code, adminId = adminId, members = listOf(adminId))).await()
        db.runTransaction { tx ->
            val uRef = db.collection("users").document(adminId)
            val u = tx.get(uRef).toObject(User::class.java)
            tx.update(uRef, "groupIds", (u?.groupIds ?: emptyList()) + ref.id)
        }.await()
        Result.success(code)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun joinGroup(inviteCode: String, userId: String): Result<Unit> {
        return try {
            val code = inviteCode.uppercase().trim()
            val gSnap = db.collection("groups").whereEqualTo("inviteCode", code).limit(1).get().await()
            val gDoc = gSnap.documents.firstOrNull()
                ?: return Result.failure(Exception("Invalid invite code"))
            db.runTransaction { tx ->
                val g = tx.get(gDoc.reference).toObject(Group::class.java) ?: throw Exception("Group gone")
                val u = tx.get(db.collection("users").document(userId)).toObject(User::class.java) ?: throw Exception("User not found")
                if (!g.members.contains(userId)) {
                    tx.update(gDoc.reference, "members", g.members + userId)
                    tx.update(db.collection("users").document(userId), "groupIds", (u.groupIds + gDoc.id).distinct())
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun leaveGroup(groupId: String, userId: String): Result<Unit> {
        if (groupId == "global") return Result.failure(Exception("Cannot leave global group"))
        return try {
            val gRef = db.collection("groups").document(groupId)
            val uRef = db.collection("users").document(userId)
            db.runTransaction { tx ->
                val g = tx.get(gRef).toObject(Group::class.java) ?: throw Exception("Group not found")
                val u = tx.get(uRef).toObject(User::class.java) ?: throw Exception("User not found")
                val newMembers = g.members - userId
                if (g.adminId == userId) {
                    if (newMembers.isNotEmpty()) tx.update(gRef, "adminId", newMembers.first())
                    else { tx.delete(gRef); return@runTransaction }
                }
                tx.update(gRef, "members", newMembers)
                tx.update(uRef, "groupIds", u.groupIds - groupId)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override fun getGroupMembers(groupId: String): Flow<List<User>> = callbackFlow {
        val listener = db.collection("groups").document(groupId)
            .addSnapshotListener { gSnap, _ ->
                val ids = gSnap?.get("members") as? List<String> ?: emptyList()
                if (ids.isEmpty()) { trySend(emptyList()); return@addSnapshotListener }
                db.collection("users").whereIn("id", ids.take(30)).get()
                    .addOnSuccessListener { snap ->
                        trySend(snap.documents.mapNotNull { it.toObject(User::class.java)?.toPublicProfile() })
                    }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun kickMember(groupId: String, targetUserId: String, adminId: String): Result<Unit> = try {
        val gRef = db.collection("groups").document(groupId)
        val tRef = db.collection("users").document(targetUserId)
        db.runTransaction { tx ->
            val g = tx.get(gRef).toObject(Group::class.java) ?: throw Exception("Group not found")
            if (g.adminId != adminId) throw Exception("Not authorized")
            if (targetUserId == adminId) throw Exception("Admin cannot kick themselves")
            val u = tx.get(tRef).toObject(User::class.java) ?: throw Exception("User not found")
            tx.update(gRef, "members", g.members - targetUserId)
            tx.update(tRef, "groupIds", u.groupIds - groupId)
        }.await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun deleteGroup(groupId: String, adminId: String): Result<Unit> {
        if (groupId == "global") return Result.failure(Exception("Cannot delete global group"))
        return try {
            val g = db.collection("groups").document(groupId).get().await()
                .toObject(Group::class.java) ?: throw Exception("Group not found")
            if (g.adminId != adminId) throw Exception("Not authorized")
            db.runBatch { batch ->
                g.members.forEach { batch.update(db.collection("users").document(it), "groupIds", FieldValue.arrayRemove(groupId)) }
                batch.delete(db.collection("groups").document(groupId))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun renameGroup(groupId: String, newName: String, adminId: String): Result<Unit> = try {
        val gRef = db.collection("groups").document(groupId)
        val g = gRef.get().await().toObject(Group::class.java) ?: throw Exception("Group not found")
        if (g.adminId != adminId) throw Exception("Not authorized")
        gRef.update("name", newName).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun transferAdmin(groupId: String, newAdminId: String, currentAdminId: String): Result<Unit> = try {
        val gRef = db.collection("groups").document(groupId)
        val g = gRef.get().await().toObject(Group::class.java) ?: throw Exception("Group not found")
        if (g.adminId != currentAdminId) throw Exception("Not authorized")
        if (!g.members.contains(newAdminId)) throw Exception("New admin must be a member")
        gRef.update("adminId", newAdminId).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    // ── Challenges ────────────────────────────────────────────────

    override suspend fun submitChallenge(
        snipe: Snipe,
        challengerId: String,
        challengerName: String
    ): Result<Unit> = try {
        if (snipe.hasBeenChallenged) throw Exception("Already challenged")
        if (snipe.targetId != challengerId) throw Exception("Only the target may challenge")

        val challengeId = UUID.randomUUID().toString()
        val challenge = SnipeChallenge(
            id = challengeId,
            snipeId = snipe.id,
            groupId = snipe.groupId,
            challengerId = challengerId,
            hunterId = snipe.hunterId,
            pointsAtStake = snipe.pointsAwarded,
            timestamp = System.currentTimeMillis(),
            status = ChallengeStatus.PENDING,
            imageUrl = snipe.imageUrl,
            hunterName = "",         // resolved in ViewModel from getUserById
            challengerName = challengerName
        )

        db.runBatch { batch ->
            batch.set(db.collection("challenges").document(challengeId), challenge)
            batch.update(db.collection("snipes").document(snipe.id), "hasBeenChallenged", true)
        }.await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    /**
     * Gets all PENDING challenges for groups where the given user is admin.
     * We query challenges by groupId list from the groups the user admins.
     */
    override fun getPendingChallenges(adminId: String): Flow<List<SnipeChallenge>> = callbackFlow {
        // First get the groups this user admins
        val groupsListener = db.collection("groups")
            .whereEqualTo("adminId", adminId)
            .addSnapshotListener { groupSnap, _ ->
                val groupIds = groupSnap?.documents?.mapNotNull { it.id } ?: emptyList()
                if (groupIds.isEmpty()) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                // Then get pending challenges for those groups
                db.collection("challenges")
                    .whereIn("groupId", groupIds.take(10))
                    .whereEqualTo("status", ChallengeStatus.PENDING.name)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener { cSnap ->
                        trySend(cSnap.documents.mapNotNull { it.toObject(SnipeChallenge::class.java) })
                    }
                    .addOnFailureListener { trySend(emptyList()) }
            }
        awaitClose { groupsListener.remove() }
    }

    override suspend fun upholdChallenge(challenge: SnipeChallenge, adminId: String): Result<Unit> = try {
        // Verify admin owns the group
        val group = db.collection("groups").document(challenge.groupId).get().await()
            .toObject(Group::class.java) ?: throw Exception("Group not found")
        if (group.adminId != adminId) throw Exception("Not authorized")

        db.collection("challenges").document(challenge.id)
            .update("status", ChallengeStatus.UPHELD.name).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun overturnChallenge(challenge: SnipeChallenge, adminId: String): Result<Unit> = try {
        val group = db.collection("groups").document(challenge.groupId).get().await()
            .toObject(Group::class.java) ?: throw Exception("Group not found")
        if (group.adminId != adminId) throw Exception("Not authorized")

        // Reverse points from hunter
        val hunterRef = db.collection("users").document(challenge.hunterId)
        val snipeRef = db.collection("snipes").document(challenge.snipeId)
        val challengeRef = db.collection("challenges").document(challenge.id)

        db.runTransaction { tx ->
            val hunter = tx.get(hunterRef).toObject(User::class.java)
                ?: throw Exception("Hunter not found")
            val newScore = (hunter.totalScore - challenge.pointsAtStake).coerceAtLeast(0)
            val newStreak = (hunter.currentStreak - 1).coerceAtLeast(0)
            tx.update(hunterRef, mapOf("totalScore" to newScore, "currentStreak" to newStreak))
            tx.update(snipeRef, "status", SnipeStatus.OVERTURNED.name)
            tx.update(challengeRef, "status", ChallengeStatus.OVERTURNED.name)
        }.await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}