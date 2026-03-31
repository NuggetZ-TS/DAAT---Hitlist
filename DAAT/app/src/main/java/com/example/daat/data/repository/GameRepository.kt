package com.example.daat.data.repository

import com.example.daat.data.model.User
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    fun getCurrentUser(): Flow<User?>
    fun getCurrentTarget(userId: String): Flow<User?>
    fun getLeaderboard(groupId: String): Flow<List<User>>

    suspend fun updateLocation(userId: String, latitude: Double, longitude: Double): Result<Unit>

    suspend fun submitSnipe(
        hunterId: String,
        targetId: String,
        imageUrl: String,
        hunterLat: Double,
        hunterLon: Double,
        capturedAt: Long
    ): Result<Unit>
}
