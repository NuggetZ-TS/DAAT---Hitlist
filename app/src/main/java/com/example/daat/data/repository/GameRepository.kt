package com.example.daat.data.repository

import com.example.daat.data.model.Group
import com.example.daat.data.model.Message
import com.example.daat.data.model.Snipe
import com.example.daat.data.model.User
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    fun getCurrentUser(): Flow<User?>
    fun getUserById(userId: String): Flow<User?>
    fun getLeaderboard(groupId: String): Flow<List<User>>
    fun getSnipeFeed(): Flow<List<Snipe>>
    fun getCurrentTarget(hunterId: String): Flow<User?>
    
    suspend fun submitSnipe(
        hunterId: String,
        targetId: String,
        imageUrl: String,
        lat: Double,
        lon: Double,
        timestamp: Long
    ): Result<Int>
    
    suspend fun toggleLike(snipeId: String)
    suspend fun assignDailyTargets(groupId: String)
    suspend fun claimSurvivalReward(userId: String): Result<Int>

    // Group features
    fun getGroups(): Flow<List<Group>>
    fun getGroupById(groupId: String): Flow<Group?>
    suspend fun joinGroup(groupId: String): Result<Unit>
    
    // Messaging features
    fun getMessages(groupId: String): Flow<List<Message>>
    suspend fun sendMessage(groupId: String, content: String): Result<Unit>
}
