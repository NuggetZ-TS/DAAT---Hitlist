package com.example.daat.data.repository

import android.net.Uri
import android.util.Log
import com.example.daat.data.model.Group
import com.example.daat.data.model.Snipe
import com.example.daat.data.model.SnipeStatus
import com.example.daat.data.model.User
import com.example.daat.logic.ScoringManager
import com.example.daat.logic.VerificationUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID

class FirebaseGameRepository : GameRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    override fun getCurrentUser(): Flow<User?> = callbackFlow {
        var snapshotListener: ListenerRegistration? = null
        
        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            snapshotListener?.remove()
            val currentUserId = firebaseAuth.currentUser?.uid
            if (currentUserId == null) {
                trySend(null)
            } else {
                snapshotListener = db.collection("users").document(currentUserId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Log.e("FirebaseGameRepo", "Error fetching current user", e)
                            return@addSnapshotListener
                        }
                        if (snapshot != null && snapshot.exists()) {
                            val user = snapshot.toObject(User::class.java)
                            trySend(user)
                        } else {
                            createDefaultUser(currentUserId)
                        }
                    }
            }
        }
        
        auth.addAuthStateListener(authListener)
        
        awaitClose { 
            auth.removeAuthStateListener(authListener)
            snapshotListener?.remove()
        }
    }

    private fun createDefaultUser(userId: String) {
        val firebaseUser = auth.currentUser ?: return
        val displayName = firebaseUser.displayName ?: "Agent ${userId.take(4)}"
        Log.d("FirebaseGameRepo", "Creating default user for $userId")
        
        val newUser = User(
            id = userId,
            name = displayName,
            username = "@${displayName.replace(" ", "").lowercase()}",
            profileImageUrl = firebaseUser.photoUrl?.toString(),
            totalScore = 0,
            groupIds = listOf("global")
        )
        db.collection("users").document(userId).set(newUser)
            .addOnFailureListener { e -> Log.e("FirebaseGameRepo", "Failed to create user", e) }
    }

    override suspend fun signInAnonymously(): Result<Unit> {
        return try {
            auth.signInAnonymously().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> {
        auth.signOut()
        return Result.success(Unit)
    }

    override fun getCurrentTarget(userId: String): Flow<User?> = callbackFlow {
        val listener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirebaseGameRepo", "Error in getCurrentTarget", e)
                    return@addSnapshotListener
                }
                val user = snapshot?.toObject(User::class.java)
                val targetId = user?.currentTargetId
                
                if (targetId != null) {
                    db.collection("users").document(targetId)
                        .get()
                        .addOnSuccessListener { targetSnapshot ->
                            val target = targetSnapshot.toObject(User::class.java)
                            trySend(target)
                        }
                        .addOnFailureListener { err ->
                             Log.e("FirebaseGameRepo", "Error fetching target doc", err)
                             trySend(null)
                        }
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    override fun getLeaderboard(groupId: String): Flow<List<User>> = callbackFlow {
        Log.d("Leaderboard", "Fetching leaderboard for group: $groupId")
        val listener = db.collection("users")
            .whereArrayContains("groupIds", groupId)
            .orderBy("totalScore", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("Leaderboard", "Error fetching leaderboard. If it mentions a missing index, click the link in the message to create it.", e)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val users = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(User::class.java)?.toPublicProfile()
                } ?: emptyList()
                Log.d("Leaderboard", "Received ${users.size} users for group $groupId")
                trySend(users)
            }
        awaitClose { listener.remove() }
    }

    override fun getSnipeFeed(): Flow<List<Snipe>> = callbackFlow {
        val listener = db.collection("snipes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("SnipeFeed", "Error fetching snipe feed", e)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val snipes = snapshot?.documents?.mapNotNull { it.toObject(Snipe::class.java) } ?: emptyList()
                Log.d("SnipeFeed", "Received ${snipes.size} snipes from Firestore")
                trySend(snipes)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun updateLocation(userId: String, latitude: Double, longitude: Double): Result<Unit> {
        return try {
            db.collection("users").document(userId)
                .update(mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "lastLocationUpdate" to System.currentTimeMillis()
                )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun submitSnipe(
        hunterId: String,
        targetId: String,
        imageUrl: String,
        hunterLat: Double,
        hunterLon: Double,
        hunterHeading: Double,
        capturedAt: Long
    ): Result<Int> = try {
        withTimeout(30000) {
            Log.d("Snipe", "STEP 1: Verifying target $targetId")
            
            val targetDoc = db.collection("users").document(targetId).get().await()
            val target = targetDoc.toObject(User::class.java) ?: throw Exception("TARGET_NOT_FOUND")
            
            val targetLat = target.latitude ?: throw Exception("TARGET_LOCATION_UNKNOWN")
            val targetLon = target.longitude ?: throw Exception("TARGET_LOCATION_UNKNOWN")

            val distance = VerificationUtils.calculateDistance(hunterLat, hunterLon, targetLat, targetLon)
            Log.d("Snipe", "Distance: $distance m")
            if (distance > 100.0) throw Exception("TOO_FAR")

            val targetBearing = VerificationUtils.calculateBearing(hunterLat, hunterLon, targetLat, targetLon)
            if (!VerificationUtils.isPointingAtTarget(hunterHeading, targetBearing)) {
                throw Exception("WRONG_ORIENTATION")
            }

            Log.d("Snipe", "STEP 2: Uploading image: $imageUrl")
            val fileName = "snipes/${UUID.randomUUID()}.jpg"
            val storageRef = storage.reference.child(fileName)
            
            val cleanPath = imageUrl.removePrefix("file://")
            val file = File(cleanPath)
            
            if (!file.exists()) {
                Log.e("Snipe", "File does not exist at path: $cleanPath")
                throw Exception("FILE_NOT_FOUND")
            }
            
            val fileUri = Uri.fromFile(file)
            storageRef.putFile(fileUri).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.d("Snipe", "Upload success: $downloadUrl")

            val hunterDoc = db.collection("users").document(hunterId).get().await()
            val hunter = hunterDoc.toObject(User::class.java) ?: throw Exception("HUNTER_NOT_FOUND")
            
            val points = ScoringManager.calculatePoints(
                distanceMeters = distance,
                streak = hunter.currentStreak,
                targetAssignedAt = hunter.targetAssignedAt,
                capturedAt = capturedAt
            )

            val snipeId = UUID.randomUUID().toString()
            val newSnipe = Snipe(
                id = snipeId,
                hunterId = hunterId,
                targetId = targetId,
                groupId = hunter.currentTargetGroupId ?: "global",
                timestamp = System.currentTimeMillis(),
                imageUrl = downloadUrl,
                status = SnipeStatus.PENDING,
                pointsAwarded = points
            )
            
            Log.d("Snipe", "STEP 3: Saving to Firestore")
            val batch = db.batch()
            batch.set(db.collection("snipes").document(snipeId), newSnipe)
            batch.update(db.collection("users").document(hunterId), mapOf(
                "totalScore" to (hunter.totalScore + points),
                "currentStreak" to (hunter.currentStreak + 1),
                "currentTargetId" to null,
                "currentTargetGroupId" to null
            ))
            batch.commit().await()
            
            Log.d("Snipe", "SUCCESS: Snipe submitted for community verification.")
            Result.success(points)
        }
    } catch (e: Exception) {
        Log.e("Snipe", "FAILURE: ${e.message}", e)
        Result.failure(e)
    }

    override suspend fun voteOnSnipe(snipeId: String, userId: String, isVerify: Boolean): Result<Unit> {
        return try {
            val snipeRef = db.collection("snipes").document(snipeId)
            db.runTransaction { transaction ->
                val snipe = transaction.get(snipeRef).toObject(Snipe::class.java) ?: return@runTransaction
                
                if (isVerify) {
                    if (userId !in snipe.verifiedBy) {
                        transaction.update(snipeRef, "verifiedBy", FieldValue.arrayUnion(userId))
                        transaction.update(snipeRef, "rejectedBy", FieldValue.arrayRemove(userId))
                        
                        if (snipe.verifiedBy.size + 1 >= 3) {
                            transaction.update(snipeRef, "status", SnipeStatus.VERIFIED)
                        }
                    }
                } else {
                    if (userId !in snipe.rejectedBy) {
                        transaction.update(snipeRef, "rejectedBy", FieldValue.arrayUnion(userId))
                        transaction.update(snipeRef, "verifiedBy", FieldValue.arrayRemove(userId))
                        
                        if (snipe.rejectedBy.size + 1 >= 3) {
                            transaction.update(snipeRef, "status", SnipeStatus.REJECTED)
                        }
                    }
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun moderateSnipe(snipeId: String, adminId: String, isVerify: Boolean): Result<Unit> {
        return try {
            val snipeRef = db.collection("snipes").document(snipeId)
            db.runTransaction { transaction ->
                val snipe = transaction.get(snipeRef).toObject(Snipe::class.java) ?: return@runTransaction
                val groupDoc = transaction.get(db.collection("groups").document(snipe.groupId))
                val adminIdInGroup = groupDoc.getString("adminId")

                if (adminId == adminIdInGroup) {
                    val newStatus = if (isVerify) SnipeStatus.VERIFIED else SnipeStatus.REJECTED
                    transaction.update(snipeRef, "status", newStatus)
                } else {
                    throw Exception("NOT_AN_ADMIN")
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun assignDailyTargets(groupId: String): Result<Unit> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("NOT_LOGGED_IN")
            val groupDoc = db.collection("groups").document(groupId).get().await()
            val group = groupDoc.toObject(Group::class.java) ?: throw Exception("GROUP_NOT_FOUND")
            
            val members = group.members.shuffled()
            if (members.size < 2) {
                return spawnDummyTarget()
            }

            db.runTransaction { transaction ->
                for (i in members.indices) {
                    val hunterId = members[i]
                    val targetId = members[(i + 1) % members.size]
                    val userRef = db.collection("users").document(hunterId)
                    transaction.update(userRef, mapOf(
                        "currentTargetId" to targetId,
                        "currentTargetGroupId" to groupId,
                        "targetAssignedAt" to System.currentTimeMillis()
                    ))
                }
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun spawnDummyTarget(): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("NOT_LOGGED_IN")
            val userDoc = db.collection("users").document(userId).get().await()
            val user = userDoc.toObject(User::class.java) ?: throw Exception("USER_NOT_FOUND")

            val baseLat = user.latitude ?: 37.7749
            val baseLon = user.longitude ?: -122.4194

            Log.d("FirebaseGameRepo", "Spawning dummy target near Lat: $baseLat, Lon: $baseLon")

            val dummyLat = baseLat
            val dummyLon = baseLon + 0.00045
            val dummyId = "dummy_practice_target"

            val dummyUser = User(
                id = dummyId,
                name = "Practice Target",
                username = "@practice",
                latitude = dummyLat,
                longitude = dummyLon,
                groupIds = listOf("global")
            )

            db.collection("users").document(dummyId).set(dummyUser).await()

            db.collection("users").document(userId).update(mapOf(
                "currentTargetId" to dummyId,
                "currentTargetGroupId" to "global",
                "targetAssignedAt" to System.currentTimeMillis()
            )).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseGameRepo", "Failed to spawn dummy", e)
            Result.failure(e)
        }
    }

    override fun getUserById(userId: String): Flow<User?> = callbackFlow {
        val listener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirebaseGameRepo", "Error in getUserById", e)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(User::class.java)?.toPublicProfile())
            }
        awaitClose { listener.remove() }
    }

    override suspend fun toggleLike(snipeId: String): Result<Unit> {
        return try {
            db.collection("snipes").document(snipeId)
                .update("likes", FieldValue.increment(1))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getUserGroups(userId: String): Flow<List<Group>> = callbackFlow {
        val listener = db.collection("groups")
            .whereArrayContains("members", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirebaseGameRepo", "Error in getUserGroups", e)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val groups = snapshot?.documents?.mapNotNull { it.toObject(Group::class.java) } ?: emptyList()
                trySend(groups)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun createGroup(name: String, adminId: String): Result<String> {
        return try {
            val inviteCode = (1..6).map { ('A'..'Z').random() }.joinToString("")
            val groupId = UUID.randomUUID().toString()
            val newGroup = Group(
                id = groupId,
                name = name,
                inviteCode = inviteCode,
                adminId = adminId,
                members = listOf(adminId)
            )
            db.collection("groups").document(groupId).set(newGroup).await()
            
            db.collection("users").document(adminId)
                .update("groupIds", FieldValue.arrayUnion(groupId))
                .await()
                
            Result.success(inviteCode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun joinGroup(inviteCode: String, userId: String): Result<Unit> {
        return try {
            val groupQuery = db.collection("groups")
                .whereEqualTo("inviteCode", inviteCode)
                .get()
                .await()
            
            if (groupQuery.isEmpty) throw Exception("GROUP_NOT_FOUND")
            
            val groupDoc = groupQuery.documents.first()
            val groupId = groupDoc.id
            
            db.runTransaction { transaction ->
                transaction.update(groupDoc.reference, "members", FieldValue.arrayUnion(userId))
                transaction.update(db.collection("users").document(userId), "groupIds", FieldValue.arrayUnion(groupId))
            }.await()
                
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
