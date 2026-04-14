package com.example.daat.data.repository.fake

import com.example.daat.data.model.User
import com.example.daat.data.repository.GameRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

class FakeGameRepository : GameRepository {
    private val _currentUser = MutableStateFlow<User?>(
        User(id = "user1", name = "Test User", username = "@tester", totalScore = 150)
    )
    
    private val _leaderboard = MutableStateFlow(listOf(
        User(id = "user2", name = "Alice Smith", username = "@alice", totalScore = 450),
        User(id = "user1", name = "Test User", username = "@tester", totalScore = 150),
        User(id = "user3", name = "Bob Jones", username = "@bob", totalScore = 50)
    ))

    override fun getCurrentUser(): Flow<User?> = _currentUser.asStateFlow()

    override fun getCurrentTarget(userId: String): Flow<User?> {
        return flowOf(User(id = "user2", name = "Alice Smith", username = "@alice"))
    }

    override fun getLeaderboard(groupId: String): Flow<List<User>> = _leaderboard.asStateFlow()

    override suspend fun updateLocation(userId: String, latitude: Double, longitude: Double): Result<Unit> {
        delay(500)
        _currentUser.update { 
            if (it?.id == userId) {
                it.copy(
                    latitude = latitude,
                    longitude = longitude,
                    lastLocationUpdate = System.currentTimeMillis()
                )
            } else {
                it
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
        capturedAt: Long
    ): Result<Unit> {
        delay(1000) // Simulate network
        _currentUser.update { it?.copy(totalScore = it?.totalScore?.plus(100) ?: 100) }
        return Result.success(Unit)
    }
}
