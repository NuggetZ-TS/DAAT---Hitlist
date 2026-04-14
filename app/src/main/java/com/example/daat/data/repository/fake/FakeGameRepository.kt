package com.example.daat.data.repository.fake

import com.example.daat.data.model.*
import com.example.daat.data.repository.GameRepository
import com.example.daat.logic.ScoringManager
import com.example.daat.logic.VerificationUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.update
import java.util.UUID

class FakeGameRepository : GameRepository {
    private val _internalUsers = MutableStateFlow(listOf(
        User(id = "user1", name = "Test User", username = "@tester", totalScore = 150, currentTargetId = "user2", targetAssignedAt = System.currentTimeMillis() - 3600000, latitude = 34.0522, longitude = -118.2430, lastSnipedAt = System.currentTimeMillis() - (86400000 * 3)),
        User(id = "user2", name = "Alice Smith", username = "@alice", totalScore = 450, latitude = 34.0522, longitude = -118.2437),
        User(id = "user3", name = "Bob Jones", username = "@bob", totalScore = 50, latitude = 34.0522, longitude = -118.2438)
    ))

    private val _snipes = MutableStateFlow(listOf(
        Snipe("s1", "user2", "user1", System.currentTimeMillis() - 7200000, "https://picsum.photos/seed/1/800/600", SnipeStatus.VERIFIED, 100, 5, false, 4.5),
        Snipe("s2", "user3", "user2", System.currentTimeMillis() - 14400000, "https://picsum.photos/seed/2/800/600", SnipeStatus.VERIFIED, 100, 2, true, 12.0)
    ))

    private val _groups = MutableStateFlow(listOf(
        Group("g1", "Campus Hunters", "Daily hunt around the university.", 12, true, "user2"),
        Group("g2", "Night Owls", "Only for those who hunt after dark.", 5, false, "user3")
    ))

    private val _messages = MutableStateFlow<Map<String, List<Message>>>(mapOf(
        "g1" to listOf(
            Message("m1", "user2", "Alice Smith", "Anyone seen Test User?", System.currentTimeMillis() - 3600000, "g1"),
            Message("m2", "user3", "Bob Jones", "He's hiding near the library!", System.currentTimeMillis() - 1800000, "g1")
        )
    ))

    override fun getCurrentUser(): Flow<User?> = _internalUsers.map { it.find { u -> u.id == "user1" } }

    override fun getCurrentTarget(hunterId: String): Flow<User?> {
        return _internalUsers.map { users ->
            val user = users.find { it.id == hunterId }
            users.find { it.id == user?.currentTargetId }
        }
    }

    override fun getLeaderboard(groupId: String): Flow<List<User>> = _internalUsers.map { it.sortedByDescending { it.totalScore } }
    override fun getSnipeFeed(): Flow<List<Snipe>> = _snipes.asStateFlow()
    override fun getUserById(userId: String): Flow<User?> = _internalUsers.map { it.find { u -> u.id == userId } }

    override suspend fun submitSnipe(
        hunterId: String, targetId: String, imageUrl: String, lat: Double, lon: Double, timestamp: Long
    ): Result<Int> {
        delay(1000)
        if (!VerificationUtils.isPhotoFresh(timestamp)) return Result.failure(Exception("TOO_OLD"))
        
        val target = _internalUsers.value.find { it.id == targetId } ?: return Result.failure(Exception("TARGET_NOT_FOUND"))
        val distance = VerificationUtils.calculateDistance(lat, lon, target.latitude ?: 0.0, target.longitude ?: 0.0)
        
        if (distance > 35.0) return Result.failure(Exception("TOO_FAR"))

        val hunter = _internalUsers.value.find { it.id == hunterId } ?: return Result.failure(Exception("HUNTER_NOT_FOUND"))
        val points = ScoringManager.calculateSnipePoints(hunter.lastSnipedAt, timestamp)

        val newSnipe = Snipe("s${System.currentTimeMillis()}", hunterId, targetId, timestamp, imageUrl, SnipeStatus.VERIFIED, points, 0, false, distance)
        _snipes.update { listOf(newSnipe) + it }
        
        _internalUsers.update { users ->
            users.map { user ->
                when (user.id) {
                    hunterId -> user.copy(
                        totalScore = user.totalScore + points,
                        currentStreak = user.currentStreak + 1,
                        currentTargetId = if (targetId == "user2") "user3" else "user2",
                        targetAssignedAt = System.currentTimeMillis()
                    )
                    targetId -> user.copy(
                        currentStreak = 0, 
                        lastSnipedAt = System.currentTimeMillis()
                    )
                    else -> user
                }
            }
        }
        return Result.success(points)
    }

    override suspend fun toggleLike(snipeId: String) {
        _snipes.update { list ->
            list.map { if (it.id == snipeId) it.copy(isLikedByMe = !it.isLikedByMe, likes = if (!it.isLikedByMe) it.likes + 1 else it.likes - 1) else it }
        }
    }

    override suspend fun assignDailyTargets(groupId: String) {
        _internalUsers.update { users ->
            users.map { it.copy(currentTargetId = if (it.currentTargetId == "user2") "user3" else "user2", targetAssignedAt = System.currentTimeMillis()) }
        }
    }

    override suspend fun claimSurvivalReward(userId: String): Result<Int> {
        delay(500)
        val user = _internalUsers.value.find { it.id == userId } ?: return Result.failure(Exception("USER_NOT_FOUND"))
        
        if (ScoringManager.shouldReceiveSurvivalReward(user.lastSurvivalRewardAt)) {
            val daysSurvived = ScoringManager.getDaysSurvived(user.lastSnipedAt)
            val reward = ScoringManager.calculateSurvivalBonus(daysSurvived)
            _internalUsers.update { users ->
                users.map { 
                    if (it.id == userId) it.copy(
                        totalScore = it.totalScore + reward,
                        lastSurvivalRewardAt = System.currentTimeMillis()
                    ) else it
                }
            }
            return Result.success(reward)
        }
        return Result.failure(Exception("NOT_ELIGIBLE"))
    }

    override fun getGroups(): Flow<List<Group>> = _groups.asStateFlow()

    override fun getGroupById(groupId: String): Flow<Group?> = _groups.map { it.find { g -> g.id == groupId } }

    override suspend fun joinGroup(groupId: String): Result<Unit> {
        delay(500)
        _groups.update { list ->
            list.map { if (it.id == groupId) it.copy(isJoined = true, membersCount = it.membersCount + 1) else it }
        }
        return Result.success(Unit)
    }

    override fun getMessages(groupId: String): Flow<List<Message>> = _messages.map { it[groupId] ?: emptyList() }

    override suspend fun sendMessage(groupId: String, content: String): Result<Unit> {
        val currentUser = _internalUsers.value.find { it.id == "user1" } ?: return Result.failure(Exception("NOT_LOGGED_IN"))
        val newMessage = Message(
            id = UUID.randomUUID().toString(),
            senderId = currentUser.id,
            senderName = currentUser.name,
            content = content,
            timestamp = System.currentTimeMillis(),
            groupId = groupId
        )
        _messages.update { currentMap ->
            val list = currentMap[groupId] ?: emptyList()
            currentMap + (groupId to (list + newMessage))
        }
        return Result.success(Unit)
    }
}
