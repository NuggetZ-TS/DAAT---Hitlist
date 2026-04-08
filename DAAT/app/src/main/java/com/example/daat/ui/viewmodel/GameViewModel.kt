package com.example.daat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.daat.data.model.Snipe
import com.example.daat.data.model.User
import com.example.daat.data.repository.GameRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameUiState(
    val currentUser: User? = null,
    val currentTarget: User? = null,
    val leaderboard: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val verificationStatus: VerificationStatus = VerificationStatus.IDLE,
    val lastPointsAwarded: Int? = null
)

enum class VerificationStatus {
    IDLE, VERIFYING, SUCCESS, FAILED_LOCATION, FAILED_TIME
}

class GameViewModel(
    private val repository: GameRepository
) : ViewModel() {

    private val _internalState = MutableStateFlow(GameUiState(isLoading = true))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentTargetFlow: Flow<User?> = repository.getCurrentUser().flatMapLatest { user ->
        user?.id?.let { repository.getCurrentTarget(it) } ?: flowOf(null)
    }

    val uiState: StateFlow<GameUiState> = combine(
        repository.getCurrentUser(),
        currentTargetFlow,
        repository.getLeaderboard("global"),
        _internalState
    ) { user, target, leaderboard, internal ->
        internal.copy(
            currentUser = user,
            currentTarget = target,
            leaderboard = leaderboard,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GameUiState(isLoading = true)
    )

    val snipeFeed: Flow<List<Snipe>> = repository.getSnipeFeed()

    fun getUserById(userId: String): Flow<User?> = repository.getUserById(userId)

    fun onLikeClicked(snipeId: String) {
        viewModelScope.launch {
            repository.toggleLike(snipeId)
        }
    }

    fun onAssignNewTarget() {
        viewModelScope.launch {
            repository.assignDailyTargets("global")
        }
    }

    fun onCaptureButtonPressed(
        imageUrl: String,
        hunterLat: Double,
        hunterLon: Double,
        capturedAt: Long
    ) {
        val hunter = uiState.value.currentUser ?: return
        val targetId = hunter.currentTargetId ?: return

        _internalState.update { it.copy(verificationStatus = VerificationStatus.VERIFYING) }

        viewModelScope.launch {
            val result = repository.submitSnipe(
                hunter.id, targetId, imageUrl, hunterLat, hunterLon, capturedAt
            )

            result.onSuccess { points ->
                _internalState.update {
                    it.copy(
                        verificationStatus = VerificationStatus.SUCCESS,
                        lastPointsAwarded = points
                    )
                }
            }.onFailure { error ->
                val status = when (error.message) {
                    "TOO_FAR" -> VerificationStatus.FAILED_LOCATION
                    "TOO_OLD" -> VerificationStatus.FAILED_TIME
                    else -> VerificationStatus.IDLE
                }
                _internalState.update { it.copy(verificationStatus = status) }
            }
        }
    }
}
