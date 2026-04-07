package com.example.daat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.daat.data.model.User
import com.example.daat.data.repository.GameRepository
import com.example.daat.logic.ScoringManager
import com.example.daat.logic.VerificationUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
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

    val uiState: StateFlow<GameUiState> = combine(
        repository.getCurrentUser(),
        repository.getLeaderboard("global"),
        _internalState
    ) { user, leaderboard, internal ->
        internal.copy(
            currentUser = user,
            leaderboard = leaderboard
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GameUiState(isLoading = true)
    )

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
            // 1. Photo Freshness Check
            if (!VerificationUtils.isPhotoFresh(capturedAt)) {
                _internalState.update { it.copy(verificationStatus = VerificationStatus.FAILED_TIME) }
                return@launch
            }

            // 2. Proximity Check
            val target = repository.getCurrentTarget(targetId).firstOrNull() ?: return@launch
            val targetLat = target.latitude
            val targetLon = target.longitude

            if (targetLat != null && targetLon != null) {
                val distance = VerificationUtils.calculateDistance(hunterLat, hunterLon, targetLat, targetLon)
                if (distance > 30.0) { // 30m limit
                    _internalState.update { it.copy(verificationStatus = VerificationStatus.FAILED_LOCATION) }
                    return@launch
                }

                // 3. Calculate Points
                val points = ScoringManager.calculatePoints(
                    distanceMeters = distance,
                    streak = hunter.currentStreak,
                    targetAssignedAt = hunter.targetAssignedAt,
                    capturedAt = capturedAt
                )

                // 4. Final Submission
                val result = repository.submitSnipe(
                    hunter.id, targetId, imageUrl, hunterLat, hunterLon, capturedAt
                )

                if (result.isSuccess) {
                    _internalState.update {
                        it.copy(
                            verificationStatus = VerificationStatus.SUCCESS,
                            lastPointsAwarded = points
                        )
                    }
                }
            }
        }
    }
}
