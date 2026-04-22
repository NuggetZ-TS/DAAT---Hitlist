package com.example.daat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.daat.data.model.Group
import com.example.daat.data.model.Snipe
import com.example.daat.data.model.User
import com.example.daat.data.repository.GameRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
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
    val lastPointsAwarded: Int? = null,
    val userGroups: List<Group> = emptyList(),
    val isAuthLoading: Boolean = false,
    val selectedGroupId: String = "global"
)

enum class VerificationStatus {
    IDLE, VERIFYING, SUCCESS, FAILED_LOCATION, FAILED_TIME, FAILED_ORIENTATION, FAILED_NETWORK
}

class GameViewModel(
    private val repository: GameRepository
) : ViewModel() {

    private val _internalState = MutableStateFlow(GameUiState(isLoading = true))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentTargetFlow: Flow<User?> = repository.getCurrentUser().flatMapLatest { user ->
        user?.id?.let { repository.getCurrentTarget(it) } ?: flowOf(null)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val groupsFlow: Flow<List<Group>> = repository.getCurrentUser().flatMapLatest { user ->
        user?.id?.let { repository.getUserGroups(it) } ?: flowOf(emptyList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val leaderboardFlow: Flow<List<User>> = _internalState.flatMapLatest { state ->
        repository.getLeaderboard(state.selectedGroupId)
    }

    val uiState: StateFlow<GameUiState> = combine(
        repository.getCurrentUser(),
        currentTargetFlow,
        leaderboardFlow,
        groupsFlow,
        _internalState
    ) { user, target, leaderboard, groups, internal ->
        internal.copy(
            currentUser = user,
            currentTarget = target,
            leaderboard = leaderboard,
            userGroups = groups,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GameUiState(isLoading = true)
    )

    val snipeFeed: Flow<List<Snipe>> = repository.getSnipeFeed()

    fun getUserById(userId: String): Flow<User?> = repository.getUserById(userId)

    fun onSelectGroup(groupId: String) {
        _internalState.update { it.copy(selectedGroupId = groupId) }
    }

    fun onSignInAnonymously() {
        viewModelScope.launch {
            _internalState.update { it.copy(isAuthLoading = true, errorMessage = null) }
            val result = repository.signInAnonymously()
            result.onFailure { error ->
                _internalState.update { it.copy(errorMessage = error.message) }
            }
            _internalState.update { it.copy(isAuthLoading = false) }
        }
    }

    fun onSignInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _internalState.update { it.copy(isAuthLoading = true, errorMessage = null) }
            val result = repository.signInWithGoogle(idToken)
            result.onFailure { error ->
                _internalState.update { it.copy(errorMessage = error.message) }
            }
            _internalState.update { it.copy(isAuthLoading = false) }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            repository.signOut()
        }
    }

    fun onLikeClicked(snipeId: String) {
        viewModelScope.launch {
            repository.toggleLike(snipeId)
        }
    }

    fun onUpdateLocation(lat: Double, lon: Double) {
        val userId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            repository.updateLocation(userId, lat, lon)
        }
    }

    fun onAssignNewTarget(groupId: String) {
        viewModelScope.launch {
            repository.assignDailyTargets(groupId)
        }
    }

    fun onJoinGroup(code: String) {
        val userId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            repository.joinGroup(code, userId)
        }
    }

    fun onCreateGroup(name: String) {
        val userId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            repository.createGroup(name, userId)
        }
    }

    fun onSpawnDummyTarget() {
        viewModelScope.launch {
            repository.spawnDummyTarget()
        }
    }

    fun onVoteOnSnipe(snipeId: String, isVerify: Boolean) {
        val userId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            repository.voteOnSnipe(snipeId, userId, isVerify)
        }
    }

    fun onCaptureButtonPressed(
        imageUrl: String,
        hunterLat: Double,
        hunterLon: Double,
        hunterHeading: Double,
        capturedAt: Long
    ) {
        val hunter = uiState.value.currentUser ?: return
        val targetId = hunter.currentTargetId ?: return

        _internalState.update { it.copy(verificationStatus = VerificationStatus.VERIFYING) }

        viewModelScope.launch {
            try {
                val result = repository.submitSnipe(
                    hunter.id, targetId, imageUrl, hunterLat, hunterLon, hunterHeading, capturedAt
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
                        "WRONG_ORIENTATION" -> VerificationStatus.FAILED_ORIENTATION
                        "FILE_NOT_FOUND" -> VerificationStatus.IDLE
                        else -> VerificationStatus.FAILED_NETWORK
                    }
                    _internalState.update { it.copy(verificationStatus = status) }
                }
            } catch (e: TimeoutCancellationException) {
                _internalState.update { it.copy(verificationStatus = VerificationStatus.FAILED_NETWORK) }
            } catch (e: Exception) {
                _internalState.update { it.copy(verificationStatus = VerificationStatus.IDLE) }
            }
        }
    }
}
