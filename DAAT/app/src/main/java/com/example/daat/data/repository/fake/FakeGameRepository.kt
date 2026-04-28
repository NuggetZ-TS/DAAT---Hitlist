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
    private val _groups = MutableStateFlow<List<Group>>(listOf(
        Group(id = "global", name = "Global League", inviteCode = "GLOBAL", adminId = "system", members = emptyList())
    ))
    private val _snipes = MutableStateFlow<List<Snipe>>(emptyList())

    private val registeredUsers = mutableMapOf<String, User>()

    private fun setupMockEnvironment(currentUser: User) {
        // Only setup if not already setup to preserve cross-account data in mock
        if (_internalUsers.value.any { it.id == currentUser.id }) return

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

        val allUsers = (_internalUsers.value + currentUser + otherUsers).distinctBy { it.id }
        val userIds = allUsers.map { it.id }
        
        _internalUsers.value = allUsers.map { user ->
            if (user.currentTargetId == null) {
                val targetId = userIds.filter { it != user.id }.randomOrNull()
                user.copy(currentTargetId = targetId, groupIds = (user.groupIds + "global").distinct())
            } else user
        }

        _groups.update { groups ->
            groups.map { g ->
                if (g.id == "global") g.copy(members = (g.members + userIds).distinct()) else g
            }
        }
    }

    override fun getCurrentUser(): Flow<User?> =
        _internalUsers.map { users ->
            // Try to find the "active" user. In a real app this is handled by Auth.
            // For mock, we'll just pick the first one that looks like a Google or Guest user
            users.find { u -> u.id.startsWith("google_") || u.id == "guest_user" }
        }

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
        // Simulate different IDs for different "accounts" based on the token
        val googleId = if (idToken.contains("acc2")) "google_account_2" else "google_account_1"
        val email = "$googleId@gmail.com"
        val name = if (googleId.endsWith("2")) "Agent Two" else "Agent One"

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
        delay(300)
        // We don't remove users from _internalUsers to simulate persistent DB
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
            users.map {
                if (it.id == userId) it.copy(
                    latitude = latitude,
                    longitude = longitude,
                    lastLocationUpdate = System.currentTimeMillis()
                ) else it
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
        val target = allUsers.find { it.id == targetId }
            ?: return Result.failure(Exception("TARGET_NOT_FOUND"))
        val targetLat = target.latitude ?: 0.0
        val targetLon = target.longitude ?: 0.0

        val distance = VerificationUtils.calculateDistance(hunterLat, hunterLon, targetLat, targetLon)
        if (distance > 50.0) return Result.failure(Exception("TOO_FAR"))

        val targetBearing = VerificationUtils.calculateBearing(hunterLat, hunterLon, targetLat, targetLon)
        if (!VerificationUtils.isPointingAtTarget(hunterHeading, targetBearing)) {
            return Result.failure(Exception("WRONG_ORIENTATION"))
        }

        val hunter = allUsers.find { it.id == hunterId }
            ?: return Result.failure(Exception("HUNTER_NOT_FOUND"))
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
                } else user
            }
        }
        return Result.success(points)
    }

    override suspend fun assignDailyTargets(groupId: String): Result<Unit> {
        delay(500)
        _internalUsers.update { users ->
            val userIds = users.filter { it.groupIds.contains(groupId) }.map { it.id }
            users.map { user ->
                if (user.groupIds.contains(groupId)) {
                    val nextTarget = userIds.filter { it != user.id }.randomOrNull()
                    user.copy(currentTargetId = nextTarget, targetAssignedAt = System.currentTimeMillis())
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
                } else snipe
            }
        }
        return Result.success(Unit)
    }

    override fun getUserGroups(userId: String): Flow<List<Group>> {
        return _groups.map { groups -> groups.filter { it.members.contains(userId) } }
    }

    override suspend fun createGroup(name: String, adminId: String): Result<String> {
        delay(500)
        val inviteCode = (1..6).map { ('A'..'Z').random() }.joinToString("")
        val groupId = UUID.randomUUID().toString()
        val newGroup = Group(
            id = groupId,
            name = name,
            inviteCode = inviteCode,
            adminId = adminId,
            members = listOf(adminId)
        )
        _groups.update { it + newGroup }
        _internalUsers.update { users ->
            users.map { user ->
                if (user.id == adminId) user.copy(groupIds = (user.groupIds + groupId).distinct())
                else user
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
            return Result.success(Unit) // Already in
        }

        val groupId = group.id
        _groups.update { groups ->
            groups.map { g ->
                if (g.id == groupId) g.copy(members = (g.members + userId).distinct()) else g
            }
        }
        _internalUsers.update { users ->
            users.map { user ->
                if (user.id == userId) user.copy(groupIds = (user.groupIds + groupId).distinct())
                else user
            }
        }
        return Result.success(Unit)
    }

    override suspend fun leaveGroup(groupId: String, userId: String): Result<Unit> {
        if (groupId == "global") return Result.failure(Exception("Cannot leave global group"))

        _groups.update { groups ->
            groups.map { g ->
                if (g.id == groupId) g.copy(members = g.members - userId) else g
            }
        }
        _internalUsers.update { users ->
            users.map { user ->
                if (user.id == userId) user.copy(groupIds = user.groupIds - groupId) else user
            }
        }
        return Result.success(Unit)
    }

    // ── Admin-only operations ─────────────────────────────────────

    override fun getGroupMembers(groupId: String): Flow<List<User>> {
        return _groups.map { groups ->
            val memberIds = groups.find { it.id == groupId }?.members ?: emptyList()
            _internalUsers.value
                .filter { it.id in memberIds }
                .map { it.toPublicProfile() }
        }
    }

    override suspend fun kickMember(groupId: String, targetUserId: String, adminId: String): Result<Unit> {
        if (groupId == "global") return Result.failure(Exception("Cannot kick from global group"))
        _groups.update { groups ->
            groups.map { g ->
                if (g.id == groupId) g.copy(members = g.members - targetUserId) else g
            }
        }
        _internalUsers.update { users ->
            users.map { user ->
                if (user.id == targetUserId) user.copy(groupIds = user.groupIds - groupId) else user
            }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteGroup(groupId: String, adminId: String): Result<Unit> {
        if (groupId == "global") return Result.failure(Exception("Cannot delete global group"))
        _groups.update { it.filter { g -> g.id != groupId } }
        _internalUsers.update { users ->
            users.map { user ->
                if (user.groupIds.contains(groupId)) user.copy(groupIds = user.groupIds - groupId)
                else user
            }
        }
        return Result.success(Unit)
    }

    override suspend fun renameGroup(groupId: String, newName: String, adminId: String): Result<Unit> {
        _groups.update { groups ->
            groups.map { g ->
                if (g.id == groupId) g.copy(name = newName) else g
            }
        }
        return Result.success(Unit)
    }

    override suspend fun transferAdmin(groupId: String, newAdminId: String, currentAdminId: String): Result<Unit> {
        _groups.update { groups ->
            groups.map { g ->
                if (g.id == groupId) g.copy(adminId = newAdminId) else g
            }
        }
        return Result.success(Unit)
    }
}
