package com.example.daat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.daat.data.model.Group
import com.example.daat.data.model.Snipe
import com.example.daat.data.model.User
import com.example.daat.data.repository.GameRepository
import com.example.daat.data.repository.SignInResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
    val registrationData: RegistrationData? = null
)

data class RegistrationData(
    val userId: String,
    val email: String?,
    val defaultName: String?
)

enum class VerificationStatus {
    IDLE, VERIFYING, SUCCESS, FAILED_LOCATION, FAILED_TIME, FAILED_ORIENTATION
}

class GameViewModel(
    private val repository: GameRepository
) : ViewModel() {

    private val _internalState = MutableStateFlow(GameUiState(isLoading = true))

    // ── Events ────────────────────────────────────────────────────
    val onSnipeSuccessEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onLoginSuccessEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentTargetFlow: Flow<User?> =
        repository.getCurrentUser().flatMapLatest { user ->
            user?.id?.let { repository.getCurrentTarget(it) } ?: flowOf(null)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val groupsFlow: Flow<List<Group>> =
        repository.getCurrentUser().flatMapLatest { user ->
            user?.id?.let { repository.getUserGroups(it) } ?: flowOf(emptyList())
        }

    val uiState: StateFlow<GameUiState> = combine(
        repository.getCurrentUser(),
        currentTargetFlow,
        repository.getLeaderboard("global"),
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

    // ── Auth ──────────────────────────────────────────────────────

    fun onSignInAnonymously() {
        viewModelScope.launch {
            _internalState.update { it.copy(isAuthLoading = true) }
            val result = repository.signInAnonymously()
            result.onSuccess {
                onLoginSuccessEvent.tryEmit(Unit)
            }.onFailure { error ->
                _internalState.update { it.copy(errorMessage = error.message) }
            }
            _internalState.update { it.copy(isAuthLoading = false) }
        }
    }

    fun onSignInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _internalState.update { it.copy(isAuthLoading = true, errorMessage = null) }
            val result = repository.signInWithGoogle(idToken)
            result.onSuccess { signInResult ->
                when (signInResult) {
                    is SignInResult.Success -> {
                        onLoginSuccessEvent.tryEmit(Unit)
                    }
                    is SignInResult.NeedsRegistration -> {
                        _internalState.update {
                            it.copy(
                                registrationData = RegistrationData(
                                    userId = signInResult.userId,
                                    email = signInResult.email,
                                    defaultName = signInResult.name
                                )
                            )
                        }
                    }
                }
            }.onFailure { error ->
                _internalState.update { it.copy(errorMessage = error.message) }
            }
            _internalState.update { it.copy(isAuthLoading = false) }
        }
    }

    fun onCompleteRegistration(username: String, name: String) {
        val data = _internalState.value.registrationData ?: return
        viewModelScope.launch {
            _internalState.update { it.copy(isAuthLoading = true) }
            val result = repository.completeRegistration(data.userId, username, name)
            result.onSuccess {
                _internalState.update { it.copy(registrationData = null) }
                onLoginSuccessEvent.tryEmit(Unit)
            }.onFailure { error ->
                _internalState.update { it.copy(errorMessage = error.message) }
            }
            _internalState.update { it.copy(isAuthLoading = false) }
        }
    }

    fun onCancelRegistration() {
        _internalState.update { it.copy(registrationData = null) }
    }

    fun onSignOut() {
        viewModelScope.launch { repository.signOut() }
    }

    // ── Game actions ──────────────────────────────────────────────

    fun onLikeClicked(snipeId: String) {
        viewModelScope.launch { repository.toggleLike(snipeId) }
    }

    fun onAssignNewTarget() {
        viewModelScope.launch { repository.assignDailyTargets("global") }
    }

    fun onJoinGroup(code: String) {
        val userId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch { repository.joinGroup(code, userId) }
    }

    fun onCreateGroup(name: String) {
        val userId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch { repository.createGroup(name, userId) }
    }

    // ── Verification & Moderation ─────────────────────────────────

    fun onConfirmSnipe(snipeId: String) {
        viewModelScope.launch { repository.confirmSnipeAsTarget(snipeId) }
    }

    fun onChallengeSnipe(snipeId: String) {
        viewModelScope.launch { repository.challengeSnipeAsTarget(snipeId) }
    }

    fun onModerateSnipe(snipeId: String, approved: Boolean) {
        viewModelScope.launch { repository.moderateSnipe(snipeId, approved) }
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
            val result = repository.submitSnipe(
                hunterId = hunter.id,
                targetId = targetId,
                imageUrl = imageUrl,
                hunterLat = hunterLat,
                hunterLon = hunterLon,
                hunterHeading = hunterHeading,
                capturedAt = capturedAt
            )

            result.onSuccess { points ->
                _internalState.update {
                    it.copy(
                        verificationStatus = VerificationStatus.SUCCESS,
                        lastPointsAwarded = points
                    )
                }
                onSnipeSuccessEvent.tryEmit(Unit)
            }.onFailure { error ->
                val status = when (error.message) {
                    "TOO_FAR"          -> VerificationStatus.FAILED_LOCATION
                    "TOO_OLD"          -> VerificationStatus.FAILED_TIME
                    "WRONG_ORIENTATION" -> VerificationStatus.FAILED_ORIENTATION
                    else               -> VerificationStatus.IDLE
                }
                _internalState.update { it.copy(verificationStatus = status) }
            }
        }
    }
}
