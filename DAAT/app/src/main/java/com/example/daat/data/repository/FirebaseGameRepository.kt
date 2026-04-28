package com.example.daat.data.repository

import android.net.Uri
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
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
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

    override fun getCurrentUser(): Flow<User?> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    trySend(user)
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun signInAnonymously(): Result<Unit> {
        return try {
            val result = auth.signInAnonymously().await()
            val userId = result.user?.uid ?: throw Exception("Auth failed")
            
            val newUser = User(
                id = userId,
                name = "Guest",
                username = "@guest_${userId.takeLast(4)}",
                totalScore = 0,
                groupIds = listOf("global")
            )
            db.collection("users").document(userId).set(newUser).await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<SignInResult> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: throw Exception("Google Auth failed")
            
            val userDoc = db.collection("users").document(firebaseUser.uid).get().await()
            
            if (userDoc.exists()) {
                val user = userDoc.toObject(User::class.java)!!
                Result.success(SignInResult.Success(user))
            } else {
                Result.success(SignInResult.NeedsRegistration(
                    userId = firebaseUser.uid,
                    email = firebaseUser.email,
                    name = firebaseUser.displayName
                ))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun completeRegistration(userId: String, username: String, name: String): Result<Unit> {
        return try {
            val newUser = User(
                id = userId,
                name = name,
                username = if (username.startsWith("@")) username else "@$username",
                totalScore = 0,
                groupIds = listOf("global")
            )
            db.collection("users").document(userId).set(newUser).await()
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
            .addSnapshotListener { snapshot, _ ->
                val user = snapshot?.toObject(User::class.java)
                val targetId = user?.currentTargetId
                
                if (targetId != null) {
                    db.collection("users").document(targetId)
                        .get()
                        .addOnSuccessListener { targetSnapshot ->
                            val target = targetSnapshot.toObject(User::class.java)
                            trySend(target?.toPublicProfile())
                        }
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    override fun getLeaderboard(groupId: String): Flow<List<User>> = callbackFlow {
        val listener = db.collection("users")
            .whereArrayContains("groupIds", groupId)
            .orderBy("totalScore", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, _ ->
                val users = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(User::class.java)?.toPublicProfile()
                } ?: emptyList()
                trySend(users)
            }
        awaitClose { listener.remove() }
    }

    override fun getSnipeFeed(): Flow<List<Snipe>> = callbackFlow {
        val listener = db.collection("snipes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, _ ->
                val currentUserId = auth.currentUser?.uid
                val snipes = snapshot?.documents?.mapNotNull { doc ->
                    val snipe = doc.toObject(Snipe::class.java)
                    snipe?.copy(isLikedByMe = currentUserId != null && snipe.likedBy.contains(currentUserId))
                } ?: emptyList()
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
    ): Result<Int> {
        return try {
            val targetDoc = db.collection("users").document(targetId).get().await()
            val target = targetDoc.toObject(User::class.java) ?: throw Exception("TARGET_NOT_FOUND")
            
            val targetLat = target.latitude ?: 0.0
            val targetLon = target.longitude ?: 0.0

            val distance = VerificationUtils.calculateDistance(hunterLat, hunterLon, targetLat, targetLon)
            if (distance > 50.0) throw Exception("TOO_FAR")

            val targetBearing = VerificationUtils.calculateBearing(hunterLat, hunterLon, targetLat, targetLon)
            if (!VerificationUtils.isPointingAtTarget(hunterHeading, targetBearing)) {
                throw Exception("WRONG_ORIENTATION")
            }

            val fileName = "snipes/${UUID.randomUUID()}.jpg"
            val storageRef = storage.reference.child(fileName)
            storageRef.putFile(Uri.parse(imageUrl)).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()

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
                groupId = "global",
                timestamp = capturedAt,
                imageUrl = downloadUrl,
                status = SnipeStatus.PENDING,
                pointsAwarded = points
            )
            db.collection("snipes").document(snipeId).set(newSnipe).await()

            db.collection("users").document(hunterId).update(mapOf(
                "totalScore" to (hunter.totalScore + points),
                "currentStreak" to (hunter.currentStreak + 1),
                "currentTargetId" to null,
                "latitude" to hunterLat,
                "longitude" to hunterLon,
                "lastLocationUpdate" to System.currentTimeMillis()
            )).await()

            Result.success(points)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun assignDailyTargets(groupId: String): Result<Unit> {
        return Result.failure(Exception("Not implemented locally for Firebase. Use Cloud Functions."))
    }

    override fun getUserById(userId: String): Flow<User?> = callbackFlow {
        val listener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.toObject(User::class.java)?.toPublicProfile())
            }
        awaitClose { listener.remove() }
    }

    override suspend fun toggleLike(snipeId: String): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Not logged in")
            val snipeRef = db.collection("snipes").document(snipeId)
            
            db.runTransaction { transaction ->
                val snipe = transaction.get(snipeRef).toObject(Snipe::class.java) ?: return@runTransaction
                val likedBy = snipe.likedBy.toMutableList()
                
                if (likedBy.contains(userId)) {
                    likedBy.remove(userId)
                    transaction.update(snipeRef, "likedBy", likedBy)
                    transaction.update(snipeRef, "likes", FieldValue.increment(-1))
                } else {
                    likedBy.add(userId)
                    transaction.update(snipeRef, "likedBy", likedBy)
                    transaction.update(snipeRef, "likes", FieldValue.increment(1))
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun confirmSnipeAsTarget(snipeId: String): Result<Unit> {
        return try {
            db.collection("snipes").document(snipeId)
                .update(mapOf(
                    "targetConfirmed" to true,
                    "status" to SnipeStatus.VERIFIED.name
                )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun challengeSnipeAsTarget(snipeId: String): Result<Unit> {
        return try {
            db.collection("snipes").document(snipeId)
                .update("status", SnipeStatus.CHALLENGED.name).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun moderateSnipe(snipeId: String, approved: Boolean): Result<Unit> {
        return try {
            val status = if (approved) SnipeStatus.VERIFIED else SnipeStatus.REJECTED
            db.collection("snipes").document(snipeId)
                .update("status", status.name).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getUserGroups(userId: String): Flow<List<Group>> = callbackFlow {
        val listener = db.collection("groups")
            .whereArrayContains("members", userId)
            .addSnapshotListener { snapshot, _ ->
                val groups = snapshot?.documents?.mapNotNull { it.toObject(Group::class.java) } ?: emptyList()
                trySend(groups)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun createGroup(name: String, adminId: String): Result<String> {
        return try {
            val inviteCode = (1..6).map { ('A'..'Z').random() }.joinToString("")
            val docRef = db.collection("groups").document()
            val group = Group(
                id = docRef.id,
                name = name,
                inviteCode = inviteCode,
                adminId = adminId,
                members = listOf(adminId)
            )
            docRef.set(group).await()
            
            val userRef = db.collection("users").document(adminId)
            db.runTransaction { transaction ->
                val user = transaction.get(userRef).toObject(User::class.java)
                val newGroupIds = (user?.groupIds ?: emptyList()) + docRef.id
                transaction.update(userRef, "groupIds", newGroupIds)
            }.await()

            Result.success(inviteCode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun joinGroup(inviteCode: String, userId: String): Result<Unit> {
        return try {
            val groupSnapshot = db.collection("groups")
                .whereEqualTo("inviteCode", inviteCode)
                .limit(1)
                .get()
                .await()
            
            val groupDoc = groupSnapshot.documents.firstOrNull() 
                ?: return Result.failure(Exception("Invalid invite code"))
            
            val groupId = groupDoc.id
            @Suppress("UNCHECKED_CAST")
            val members = groupDoc.get("members") as? List<String> ?: emptyList()
            
            if (members.contains(userId)) {
                return Result.failure(Exception("Already a member"))
            }

            db.runTransaction { transaction ->
                transaction.update(groupDoc.reference, "members", members + userId)
                
                val userRef = db.collection("users").document(userId)
                val user = transaction.get(userRef).toObject(User::class.java)
                val newGroupIds = (user?.groupIds ?: emptyList()) + groupId
                transaction.update(userRef, "groupIds", newGroupIds)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
