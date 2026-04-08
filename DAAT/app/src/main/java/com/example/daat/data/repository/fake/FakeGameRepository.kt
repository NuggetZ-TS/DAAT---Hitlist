package com.example.daat.data.repository.fake

import com.example.daat.data.model.Snipe
import com.example.daat.data.model.SnipeStatus
import com.example.daat.data.model.User
import com.example.daat.data.repository.GameRepository
import com.example.daat.logic.ScoringManager
import com.example.daat.logic.VerificationUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeGameRepository : GameRepository {
    // Internal "Source of Truth" with full data (Server-side)
    private val _internalUsers = MutableStateFlow(listOf(
        User(id = "user1", name = "Test User", username = "@tester", totalScore = 150, currentTargetId = "user2", targetAssignedAt = System.currentTimeMillis() - 3600000, latitude = 34.0522, longitude = -118.2430),
        User(id = "user2", name = "Alice Smith", username = "@alice", totalScore = 450, latitude = 34.0522, longitude = -118.2437),
        User(id = "user3", name = "Bob Jones", username = "@bob", totalScore = 50, latitude = 34.0522, longitude = -118.2438)
    ))

    private val _snipes = MutableStateFlow(listOf(
        Snipe(
            id = "s1",
            hunterId = "user2",
            targetId = "user1",
            timestamp = System.currentTimeMillis() - 7200000,
            imageUrl = "https://example.com/snipe1.jpg",
            status = SnipeStatus.VERIFIED,
            pointsAwarded = 120,
            likes = 5,
            isLikedByMe = false
        ),
        Snipe(
            id = "s2",
            hunterId = "user3",
            targetId = "user2",
            timestamp = System.currentTimeMillis() - 14400000,
            imageUrl = "https://example.com/snipe2.jpg",
            status = SnipeStatus.VERIFIED,
            pointsAwarded = 95,
            likes = 2,
            isLikedByMe = true
        )
    ))

    // Public Flows: These simulate the API by stripping private data
    override fun getCurrentUser(): Flow<User?> = _internalUsers.map { it.find { u -> u.id == "user1" } }

    override fun getCurrentTarget(userId: String): Flow<User?> {
        return _internalUsers.map { users ->
            val user = users.find { it.id == userId }
            users.find { it.id == user?.currentTargetId }?.toPublicProfile()
        }
    }

    override fun getLeaderboard(groupId: String): Flow<List<User>> = _internalUsers.map { users ->
        users.map { it.toPublicProfile() }.sortedByDescending { it.totalScore }
    }

    override fun getSnipeFeed(): Flow<List<Snipe>> = _snipes.asStateFlow()

    override suspend fun updateLocation(userId: String, latitude: Double, longitude: Double): Result<Unit> {
        _internalUsers.update { users ->
            users.map { if (it.id == userId) it.copy(latitude = latitude, longitude = longitude, lastLocationUpdate = System.currentTimeMillis()) else it }
        }
        return Result.success(Unit)
    }

    override suspend fun submitSnipe(
        hunterId: String,
        targetId: String,
        imageUrl: String,
        hunterLat: Double,
        hunterLon: Double,
        capturedAt: Long
    ): Result<Int> {
        delay(1000) // Simulate network

        // SERVER-SIDE LOGIC
        
        // 1. Photo Freshness Check
        if (!VerificationUtils.isPhotoFresh(capturedAt)) {
            return Result.failure(Exception("TOO_OLD"))
        }

        // 2. Proximity Check
        val allUsers = _internalUsers.value
        val target = allUsers.find { it.id == targetId } 
            ?: return Result.failure(Exception("TARGET_NOT_FOUND"))
            
        val targetLat = target.latitude ?: 0.0
        val targetLon = target.longitude ?: 0.0
        val distance = VerificationUtils.calculateDistance(hunterLat, hunterLon, targetLat, targetLon)
        
        if (distance > 30.0) {
            return Result.failure(Exception("TOO_FAR"))
        }

        // 3. Calculate Points
        val hunter = allUsers.find { it.id == hunterId } ?: return Result.failure(Exception("HUNTER_NOT_FOUND"))
        val points = ScoringManager.calculatePoints(
            distanceMeters = distance,
            streak = hunter.currentStreak,
            targetAssignedAt = hunter.targetAssignedAt,
            capturedAt = capturedAt
        )

        // 4. Update Database
        val newSnipe = Snipe(
            id = "s${System.currentTimeMillis()}",
            hunterId = hunterId,
            targetId = targetId,
            timestamp = capturedAt,
            imageUrl = imageUrl,
            status = SnipeStatus.VERIFIED,
            pointsAwarded = points
        )
        
        _snipes.update { listOf(newSnipe) + it }
        
        _internalUsers.update { users ->
            users.map { user ->
                if (user.id == hunterId) {
                    val nextTargetId = if (targetId == "user2") "user3" else "user2"
                    user.copy(
                        totalScore = user.totalScore + points,
                        currentStreak = user.currentStreak + 1,
                        currentTargetId = nextTargetId,
                        targetAssignedAt = System.currentTimeMillis()
                    )
                } else {
                    user
                }
            }
        }

        return Result.success(points)
    }

    override suspend fun assignDailyTargets(groupId: String): Result<Unit> {
        delay(500)
        _internalUsers.update { users ->
            users.map { user ->
                val currentTarget = user.currentTargetId
                val nextTarget = if (currentTarget == "user2") "user3" else "user2"
                user.copy(
                    currentTargetId = nextTarget,
                    targetAssignedAt = System.currentTimeMillis()
                )
            }
        }
        return Result.success(Unit)
    }

    override fun getUserById(userId: String): Flow<User?> {
        return _internalUsers.map { users -> users.find { it.id == userId }?.toPublicProfile() }
    }

    override suspend fun toggleLike(snipeId: String): Result<Unit> {
        _snipes.update { snipes ->
            snipes.map { snipe ->
                if (snipe.id == snipeId) {
                    val newIsLiked = !snipe.isLikedByMe
                    snipe.copy(
                        isLikedByMe = newIsLiked,
                        likes = if (newIsLiked) snipe.likes + 1 else snipe.likes - 1
                    )
                } else {
                    snipe
                }
            }
        }
        return Result.success(Unit)
    }
}
