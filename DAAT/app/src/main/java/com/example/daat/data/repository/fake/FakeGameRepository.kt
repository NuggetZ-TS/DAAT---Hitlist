package com.example.daat.data.repository.fake

import com.example.daat.data.model.Group
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
import java.util.UUID

class FakeGameRepository : GameRepository {
    private val _internalUsers = MutableStateFlow(listOf(
        User(id = "user1", name = "Test User", username = "@tester", totalScore = 150, currentTargetId = "user2", targetAssignedAt = System.currentTimeMillis() - 3600000, latitude = 34.0522, longitude = -118.2430, groupIds = listOf("global")),
        User(id = "user2", name = "Alice Smith", username = "@alice", totalScore = 450, latitude = 34.0522, longitude = -118.2437, groupIds = listOf("global")),
        User(id = "user3", name = "Bob Jones", username = "@bob", totalScore = 50, latitude = 34.0522, longitude = -118.2438, groupIds = listOf("global"))
    ))

    private val _groups = MutableStateFlow(listOf(
        Group(id = "global", name = "Global League", inviteCode = "GLOBAL", adminId = "system", members = listOf("user1", "user2", "user3"))
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

    override fun getCurrentUser(): Flow<User?> = _internalUsers.map { it.find { u -> u.id == "user1" } }

    override suspend fun signInAnonymously(): Result<Unit> {
        delay(500)
        return Result.success(Unit)
    }

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> {
        delay(500)
        return Result.success(Unit)
    }

    override suspend fun signOut(): Result<Unit> {
        delay(500)
        return Result.success(Unit)
    }

    override fun getCurrentTarget(userId: String): Flow<User?> {
        return _internalUsers.map { users ->
            val user = users.find { it.id == userId }
            users.find { it.id == user?.currentTargetId }?.toPublicProfile()
        }
    }

    override fun getLeaderboard(groupId: String): Flow<List<User>> = _internalUsers.map { users ->
        users.filter { it.groupIds.contains(groupId) }
            .map { it.toPublicProfile() }
            .sortedByDescending { it.totalScore }
    }

    override fun getSnipeFeed(): Flow<List<Snipe>> = _snipes.asStateFlow()

    override suspend fun updateLocation(userId: String, latitude: Double, longitude: Double): Result<Unit> {
        _internalUsers.update { users ->
            users.map { 
                if (it.id == userId) it.copy(latitude = latitude, longitude = longitude, lastLocationUpdate = System.currentTimeMillis()) 
                else it 
            }
        }
        return Result.success(Unit)
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
        delay(1000)
        
        if (!VerificationUtils.isPhotoFresh(capturedAt)) {
            return Result.failure(Exception("TOO_OLD"))
        }

        val allUsers = _internalUsers.value
        val target = allUsers.find { it.id == targetId } ?: return Result.failure(Exception("TARGET_NOT_FOUND"))
        val targetLat = target.latitude ?: 0.0
        val targetLon = target.longitude ?: 0.0
        
        val distance = VerificationUtils.calculateDistance(hunterLat, hunterLon, targetLat, targetLon)
        if (distance > 50.0) {
            return Result.failure(Exception("TOO_FAR"))
        }

        val targetBearing = VerificationUtils.calculateBearing(hunterLat, hunterLon, targetLat, targetLon)
        if (!VerificationUtils.isPointingAtTarget(hunterHeading, targetBearing)) {
            return Result.failure(Exception("WRONG_ORIENTATION"))
        }

        val hunter = allUsers.find { it.id == hunterId } ?: return Result.failure(Exception("HUNTER_NOT_FOUND"))
        val points = ScoringManager.calculatePoints(
            distanceMeters = distance,
            streak = hunter.currentStreak,
            targetAssignedAt = hunter.targetAssignedAt,
            capturedAt = capturedAt
        )

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

    override suspend fun voteOnSnipe(snipeId: String, userId: String, isVerify: Boolean): Result<Unit> {
        delay(300)
        _snipes.update { snipes ->
            snipes.map { snipe ->
                if (snipe.id == snipeId) {
                    if (isVerify) {
                        snipe.copy(
                            verifiedBy = (snipe.verifiedBy + userId).distinct(),
                            rejectedBy = snipe.rejectedBy - userId
                        )
                    } else {
                        snipe.copy(
                            rejectedBy = (snipe.rejectedBy + userId).distinct(),
                            verifiedBy = snipe.verifiedBy - userId
                        )
                    }
                } else {
                    snipe
                }
            }
        }
        return Result.success(Unit)
    }

    override suspend fun moderateSnipe(snipeId: String, adminId: String, isVerify: Boolean): Result<Unit> {
        delay(300)
        _snipes.update { snipes ->
            snipes.map { snipe ->
                if (snipe.id == snipeId) {
                    snipe.copy(
                        status = if (isVerify) SnipeStatus.VERIFIED else SnipeStatus.REJECTED
                    )
                } else {
                    snipe
                }
            }
        }
        return Result.success(Unit)
    }

    override suspend fun assignDailyTargets(groupId: String): Result<Unit> {
        delay(500)
        _internalUsers.update { users ->
            users.map { user ->
                if (user.groupIds.contains(groupId)) {
                    val currentTarget = user.currentTargetId
                    val nextTarget = if (currentTarget == "user2") "user3" else "user2"
                    user.copy(
                        currentTargetId = nextTarget,
                        targetAssignedAt = System.currentTimeMillis()
                    )
                } else user
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

    override fun getUserGroups(userId: String): Flow<List<Group>> {
        return _groups.map { groups -> 
            groups.filter { it.members.contains(userId) } 
        }
    }

    override suspend fun createGroup(name: String, adminId: String): Result<String> {
        delay(500)
        val inviteCode = (1..6).map { ('A'..'Z').random() }.joinToString("")
        val groupId = UUID.randomUUID().toString()
        val mockMembers = listOf(adminId, "user2", "user3")
        val newGroup = Group(
            id = groupId,
            name = name,
            inviteCode = inviteCode,
            adminId = adminId,
            members = mockMembers
        )
        _groups.update { it + newGroup }
        _internalUsers.update { users ->
            users.map { user ->
                if (mockMembers.contains(user.id)) {
                    user.copy(groupIds = (user.groupIds + groupId).distinct())
                } else user
            }
        }
        return Result.success(inviteCode)
    }

    override suspend fun joinGroup(inviteCode: String, userId: String): Result<Unit> {
        delay(500)
        val normalizedCode = inviteCode.uppercase().trim()
        val group = _groups.value.find { it.inviteCode.uppercase() == normalizedCode }
            ?: return Result.failure(Exception("Invalid invite code"))
        
        if (group.members.contains(userId)) {
            return Result.failure(Exception("Already a member"))
        }

        val groupId = group.id
        _groups.update { groups ->
            groups.map { g ->
                if (g.id == groupId) {
                    g.copy(members = (g.members + userId).distinct())
                } else g
            }
        }
        _internalUsers.update { users ->
            users.map { user ->
                if (user.id == userId) {
                    user.copy(groupIds = (user.groupIds + groupId).distinct())
                } else user
            }
        }
        return Result.success(Unit)
    }

    override suspend fun spawnDummyTarget(): Result<Unit> {
        delay(500)
        _internalUsers.update { users ->
            users.map { user ->
                if (user.id == "user1") {
                    user.copy(
                        currentTargetId = "user2",
                        targetAssignedAt = System.currentTimeMillis()
                    )
                } else user
            }
        }
        return Result.success(Unit)
    }
}
