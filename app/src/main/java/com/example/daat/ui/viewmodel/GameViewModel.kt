package com.example.daat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.daat.data.model.Group
import com.example.daat.data.model.Message
import com.example.daat.data.model.Snipe
import com.example.daat.data.model.User
import com.example.daat.data.repository.GameRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GameUiState(
    val currentUser: User? = null,
    val currentTarget: User? = null,
    val leaderboard: List<User> = emptyList(),
    val groups: List<Group> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val verificationStatus: VerificationStatus = VerificationStatus.IDLE,
    val lastPointsAwarded: Int? = null,
    val survivalRewardAwarded: Int? = null
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
        repository.getGroups(),
        _internalState
    ) { user, target, leaderboard, groups, internal ->
        internal.copy(
            currentUser = user,
            currentTarget = target,
            leaderboard = leaderboard,
            groups = groups,
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

    fun checkSurvivalReward() {
        val user = uiState.value.currentUser ?: return
        viewModelScope.launch {
            repository.claimSurvivalReward(user.id).onSuccess { points ->
                _internalState.update { it.copy(survivalRewardAwarded = points) }
            }
        }
    }

    fun clearSurvivalNotification() {
        _internalState.update { it.copy(survivalRewardAwarded = null) }
    }

    fun onJoinGroup(groupId: String) {
        viewModelScope.launch {
            repository.joinGroup(groupId)
        }
    }

    // Messaging methods
    fun getMessages(groupId: String): Flow<List<Message>> = repository.getMessages(groupId)

    fun sendMessage(groupId: String, content: String) {
        viewModelScope.launch {
            repository.sendMessage(groupId, content)
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
