package com.example.daat.data.repository

import com.example.daat.data.model.Group
import com.example.daat.data.model.Snipe
import com.example.daat.data.model.SnipeChallenge
import com.example.daat.data.model.User
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    // Auth
    fun getCurrentUser(): Flow<User?>
    suspend fun signInAnonymously(): Result<Unit>
    suspend fun signInWithGoogle(idToken: String): Result<SignInResult>
    suspend fun completeRegistration(userId: String, username: String, name: String): Result<Unit>
    suspend fun signOut(): Result<Unit>

    // Game
    fun getCurrentTarget(userId: String): Flow<User?>
    fun getLeaderboard(groupId: String): Flow<List<User>>
    fun getSnipeFeed(): Flow<List<Snipe>>

    suspend fun updateLocation(userId: String, latitude: Double, longitude: Double): Result<Unit>

    suspend fun submitSnipe(
        hunterId: String,
        targetId: String,
        imageUrl: String,
        hunterLat: Double,
        hunterLon: Double,
        hunterHeading: Double,
        capturedAt: Long,
        groupId: String = ""
    ): Result<Int>

    suspend fun assignDailyTargets(groupId: String): Result<Unit>

    /** Returns public profile (no location). Used for display in feed/leaderboard. */
    fun getUserById(userId: String): Flow<User?>

    /**
     * Returns the full user document including lat/lng.
     * Used internally for distance/bearing calculation toward the active target.
     * Never expose raw coordinates in the UI.
     */
    fun getUserByIdRaw(userId: String): Flow<User?>

    suspend fun toggleLike(snipeId: String, userId: String): Result<Unit>

    // Groups
    fun getUserGroups(userId: String): Flow<List<Group>>
    suspend fun createGroup(name: String, adminId: String): Result<String>
    suspend fun joinGroup(inviteCode: String, userId: String): Result<Unit>
    suspend fun leaveGroup(groupId: String, userId: String): Result<Unit>

    // Admin group management
    fun getGroupMembers(groupId: String): Flow<List<User>>
    suspend fun kickMember(groupId: String, targetUserId: String, adminId: String): Result<Unit>
    suspend fun deleteGroup(groupId: String, adminId: String): Result<Unit>
    suspend fun renameGroup(groupId: String, newName: String, adminId: String): Result<Unit>
    suspend fun transferAdmin(groupId: String, newAdminId: String, currentAdminId: String): Result<Unit>

    // Challenges
    /** Submit a challenge on a snipe. Only the snipe's targetId may call this. */
    suspend fun submitChallenge(snipe: com.example.daat.data.model.Snipe, challengerId: String, challengerName: String): Result<Unit>

    /** Live stream of PENDING challenges for groups where [adminId] is admin. */
    fun getPendingChallenges(adminId: String): Flow<List<SnipeChallenge>>

    /** Admin upholds the snipe — challenge closed, points stay. */
    suspend fun upholdChallenge(challenge: SnipeChallenge, adminId: String): Result<Unit>

    /** Admin overturns the snipe — points reversed, snipe marked OVERTURNED. */
    suspend fun overturnChallenge(challenge: SnipeChallenge, adminId: String): Result<Unit>
}

sealed class SignInResult {
    data class Success(val user: User) : SignInResult()
    data class NeedsRegistration(val userId: String, val email: String?, val name: String?) : SignInResult()
}