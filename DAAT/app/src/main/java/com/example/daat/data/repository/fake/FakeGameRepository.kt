package com.example.daat.data.repository.fake

import com.example.daat.data.model.Group
import com.example.daat.data.model.Snipe
import com.example.daat.data.model.SnipeStatus
import com.example.daat.data.model.User
import com.example.daat.data.repository.GameRepository
import com.example.daat.data.repository.SignInResult
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
    private val _internalUsers = MutableStateFlow<List<User>>(emptyList())
    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    private val _snipes = MutableStateFlow<List<Snipe>>(emptyList())
    
    // In-memory "database" for registered users to simulate persistence across logins
    private val registeredUsers = mutableMapOf<String, User>()

    private fun setupMockEnvironment(currentUser: User) {
        val names = listOf("Bob", "Charlie", "Diana")
        val baseLat = 34.0522
        val baseLon = -118.2430
        
        val otherUsers = names.mapIndexed { i, name ->
            val latOffset = (Math.random() - 0.5) * 0.0004 
            val lonOffset = (Math.random() - 0.5) * 0.0004
            User(
                id = "user${i + 2}",
                name = name,
                username = "@${name.lowercase()}",
                totalScore = (0..500).random(),
                latitude = baseLat + latOffset,
                longitude = baseLon + lonOffset,
                groupIds = listOf("global"),
                currentStreak = (0..5).random(),
                targetAssignedAt = System.currentTimeMillis()
            )
        }
        
        val allUsers = listOf(currentUser) + otherUsers
        
        // Randomly assign targets to everyone
        val userIds = allUsers.map { it.id }
        val assignedUsers = allUsers.map { user ->
            val targetId = userIds.filter { it != user.id }.random()
            user.copy(currentTargetId = targetId)
        }
        
        _internalUsers.value = assignedUsers
        _groups.value = listOf(
            Group(
                id = "global",
                name = "Global League",
                inviteCode = "GLOBAL",
                adminId = "system",
                members = userIds
            )
        )
        _snipes.value = emptyList()
    }

    override fun getCurrentUser(): Flow<User?> = _internalUsers.map { it.find { u -> u.id.startsWith("google_") || u.id == "guest_user" } }

    override suspend fun signInAnonymously(): Result<Unit> {
        delay(500)
        val guestUser = User(
            id = "guest_user",
            name = "Guest User",
            username = "@guest",
            groupIds = listOf("global")
        )
        setupMockEnvironment(guestUser)
        return Result.success(Unit)
    }

    override suspend fun signInWithGoogle(idToken: String): Result<SignInResult> {
        delay(500)
        // In a real app, the idToken would be used to get the Google ID and name
        val googleId = "google_12345" 
        val email = "test@gmail.com"
        val name = "Google User"

        val existingUser = registeredUsers[googleId]
        return if (existingUser != null) {
            setupMockEnvironment(existingUser)
            Result.success(SignInResult.Success(existingUser))
        } else {
            Result.success(SignInResult.NeedsRegistration(googleId, email, name))
        }
    }

    override suspend fun completeRegistration(userId: String, username: String, name: String): Result<Unit> {
        delay(500)
        val newUser = User(
            id = userId,
            name = name,
            username = if (username.startsWith("@")) username else "@$username",
            groupIds = listOf("global")
        )
        registeredUsers[userId] = newUser
        setupMockEnvironment(newUser)
        return Result.success(Unit)
    }

    override suspend fun signOut(): Result<Unit> {
        delay(500)
        _internalUsers.value = emptyList()
        return Result.success(Unit)
    }

    override fun getCurrentTarget(userId: String): Flow<User?> {
        return _internalUsers.map { users ->
            val user = users.find { it.id == userId }
            users.find { it.id == user?.currentTargetId }
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
            val userIds = users.map { it.id }
            users.map { user ->
                if (user.id == hunterId) {
                    val nextTargetId = userIds.filter { it != hunterId && it != targetId }.random()
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
            val userIds = users.map { it.id }
            users.map { user ->
                if (user.groupIds.contains(groupId)) {
                    val nextTarget = userIds.filter { it != user.id }.random()
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
        val members = _internalUsers.value.map { it.id }
        val newGroup = Group(
            id = groupId,
            name = name,
            inviteCode = inviteCode,
            adminId = adminId,
            members = members
        )
        _groups.update { it + newGroup }
        _internalUsers.update { users ->
            users.map { user ->
                if (members.contains(user.id)) {
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
}
