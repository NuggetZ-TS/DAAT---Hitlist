package com.example.daat.data.repository

import com.example.daat.data.model.Snipe
import com.example.daat.data.model.User
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    fun getCurrentUser(): Flow<User?>
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
        capturedAt: Long
    ): Result<Int>

    suspend fun assignDailyTargets(groupId: String): Result<Unit>
    
    fun getUserById(userId: String): Flow<User?>

    suspend fun toggleLike(snipeId: String): Result<Unit>
}
