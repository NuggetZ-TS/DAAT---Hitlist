package com.example.daat.data.repository

import com.example.daat.data.model.Group
import com.example.daat.data.model.Snipe
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

    /**
     * Submits a snipe attempt. 
     * Returns Result with the points awarded if successful.
     */
    suspend fun submitSnipe(
        hunterId: String,
        targetId: String,
        imageUrl: String,
        hunterLat: Double,
        hunterLon: Double,
        hunterHeading: Double, // New parameter for orientation verification
        capturedAt: Long
    ): Result<Int>

    suspend fun assignDailyTargets(groupId: String): Result<Unit>
    
    fun getUserById(userId: String): Flow<User?>

    suspend fun toggleLike(snipeId: String): Result<Unit>

    // Groups
    fun getUserGroups(userId: String): Flow<List<Group>>
    suspend fun createGroup(name: String, adminId: String): Result<String>
    suspend fun joinGroup(inviteCode: String, userId: String): Result<Unit>
}

sealed class SignInResult {
    data class Success(val user: User) : SignInResult()
    data class NeedsRegistration(val userId: String, val email: String?, val name: String?) : SignInResult()
}
