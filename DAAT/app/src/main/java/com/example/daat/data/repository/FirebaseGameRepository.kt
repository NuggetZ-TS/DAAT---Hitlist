package com.example.daat.data.repository

import com.example.daat.data.model.Group
import com.example.daat.data.model.Snipe
import com.example.daat.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseGameRepository : GameRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun getCurrentUser(): Flow<User?> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, _ ->
                val user = snapshot?.toObject(User::class.java)
                trySend(user)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun signInAnonymously(): Result<Unit> {
        return try {
            auth.signInAnonymously().await()
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
                val snipes = snapshot?.documents?.mapNotNull { it.toObject(Snipe::class.java) } ?: emptyList()
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
        return Result.failure(Exception("Cloud Functions not yet implemented. Please use FakeGameRepository for local testing."))
    }

    override suspend fun assignDailyTargets(groupId: String): Result<Unit> {
        return Result.failure(Exception("Cloud Functions not yet implemented."))
    }

    override fun getUserById(userId: String): Flow<User?> = callbackFlow {
        val listener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.toObject(User::class.java)?.toPublicProfile())
            }
        awaitClose { listener.remove() }
    }

    override suspend fun toggleLike(snipeId: String): Result<Unit> {
        return Result.success(Unit)
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
            
            // Update user document
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
                // Update group members
                transaction.update(groupDoc.reference, "members", members + userId)
                
                // Update user groupIds
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
