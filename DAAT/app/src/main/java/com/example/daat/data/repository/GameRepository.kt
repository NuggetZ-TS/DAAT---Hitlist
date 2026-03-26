package com.example.daat.data.repository

import com.example.daat.data.model.Snipe
import com.example.daat.data.model.User
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    fun getCurrentUser(): Flow<User?>
    fun getCurrentTarget(userId: String): Flow<User?>
    fun getLeaderboard(groupId: String): Flow<List<User>>
    suspend fun submitSnipe(hunterId: String, targetId: String, imageUrl: String): Result<Unit>
}
