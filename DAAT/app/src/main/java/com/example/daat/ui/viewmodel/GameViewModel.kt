package com.example.daat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.daat.data.model.Group
import com.example.daat.data.model.GroupAssignment
import com.example.daat.data.model.Snipe
import com.example.daat.data.model.SnipeChallenge
import com.example.daat.data.model.User
import com.example.daat.data.repository.AssignmentRepository
import com.example.daat.data.repository.GameRepository
import com.example.daat.data.repository.SignInResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
    val groupMembers: List<User> = emptyList(),
    val isAuthLoading: Boolean = false,
    val registrationData: RegistrationData? = null,

    // ── Group game ────────────────────────────────────────────────
    val selectedGroup: Group? = null,
    val activeAssignment: GroupAssignment? = null,
    /** Public profile of today's target — used for name/username display. */
    val activeTarget: User? = null,
    /**
     * Raw (full) user doc of today's target — includes lat/lng.
     * Used ONLY for distance/bearing calculation. Never shown directly in UI.
     */
    val activeTargetRaw: User? = null,

    // ── Challenges ────────────────────────────────────────────────
    val pendingChallenges: List<SnipeChallenge> = emptyList(),
    val selectedChallenge: SnipeChallenge? = null,
)

data class RegistrationData(
    val userId: String,
    val email: String?,
    val defaultName: String?
)

enum class VerificationStatus {
    IDLE, VERIFYING, SUCCESS,
    FAILED_LOCATION, FAILED_TIME, FAILED_ORIENTATION,
    FAILED_NOT_IN_GROUP, FAILED_ALREADY_ELIMINATED
}

class GameViewModel(
    private val repository: GameRepository,
    private val assignmentRepository: AssignmentRepository
) : ViewModel() {

    private val _internalState = MutableStateFlow(GameUiState(isLoading = true))
    private var groupMembersJob: Job? = null
    private var assignmentJob: Job? = null
    private var activeTargetJob: Job? = null
    private var activeTargetRawJob: Job? = null
    private var challengesJob: Job? = null

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
            isLoading = false,
            selectedGroup = internal.selectedGroup?.let { sel ->
                groups.find { it.id == sel.id } ?: sel
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GameUiState(isLoading = true)
    )

    val snipeFeed: Flow<List<Snipe>> = repository.getSnipeFeed()

    fun getUserById(userId: String): Flow<User?> = repository.getUserById(userId)

    fun clearError() { _internalState.update { it.copy(errorMessage = null) } }

    // ── Auth ──────────────────────────────────────────────────────

    fun onSignInAnonymously() {
        viewModelScope.launch {
            _internalState.update { it.copy(isAuthLoading = true, errorMessage = null) }
            repository.signInAnonymously()
                .onSuccess { onLoginSuccessEvent.tryEmit(Unit) }
                .onFailure { e -> _internalState.update { it.copy(errorMessage = e.message) } }
            _internalState.update { it.copy(isAuthLoading = false) }
        }
    }

    fun onSignInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _internalState.update { it.copy(isAuthLoading = true, errorMessage = null) }
            repository.signInWithGoogle(idToken)
                .onSuccess { result ->
                    when (result) {
                        is SignInResult.Success -> {
                            onLoginSuccessEvent.tryEmit(Unit)
                            result.user.activeGroupId?.let { restoreActiveGroup(it) }
                            startChallengeListener(result.user.id)
                        }
                        is SignInResult.NeedsRegistration ->
                            _internalState.update {
                                it.copy(registrationData = RegistrationData(result.userId, result.email, result.name))
                            }
                    }
                }
                .onFailure { e -> _internalState.update { it.copy(errorMessage = e.message) } }
            _internalState.update { it.copy(isAuthLoading = false) }
        }
    }

    fun onCompleteRegistration(username: String, name: String) {
        val data = _internalState.value.registrationData ?: return
        viewModelScope.launch {
            _internalState.update { it.copy(isAuthLoading = true, errorMessage = null) }
            repository.completeRegistration(data.userId, username, name)
                .onSuccess {
                    _internalState.update { it.copy(registrationData = null) }
                    onLoginSuccessEvent.tryEmit(Unit)
                    startChallengeListener(data.userId)
                }
                .onFailure { e -> _internalState.update { it.copy(errorMessage = e.message) } }
            _internalState.update { it.copy(isAuthLoading = false) }
        }
    }

    fun onCancelRegistration() { _internalState.update { it.copy(registrationData = null) } }

    fun onSignOut() {
        viewModelScope.launch {
            clearActiveGroupInternal()
            challengesJob?.cancel()
            repository.signOut()
        }
    }

    // ── Game ──────────────────────────────────────────────────────

    fun onLikeClicked(snipeId: String) {
        val userId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch { repository.toggleLike(snipeId, userId) }
    }

    fun onAssignNewTarget() {
        viewModelScope.launch { repository.assignDailyTargets("global") }
    }

    fun onJoinGroup(code: String) {
        val userId = uiState.value.currentUser?.id ?: return
        _internalState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.joinGroup(code, userId)
                .onFailure { e -> _internalState.update { it.copy(errorMessage = "Join failed: ${e.message}") } }
            _internalState.update { it.copy(isLoading = false) }
        }
    }

    fun onCreateGroup(name: String) {
        val userId = uiState.value.currentUser?.id ?: return
        _internalState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.createGroup(name, userId)
                .onFailure { e -> _internalState.update { it.copy(errorMessage = "Create failed: ${e.message}") } }
            _internalState.update { it.copy(isLoading = false) }
        }
    }

    fun onLeaveGroup(groupId: String) {
        val userId = uiState.value.currentUser?.id ?: return
        _internalState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.leaveGroup(groupId, userId)
                .onFailure { e -> _internalState.update { it.copy(errorMessage = "Leave failed: ${e.message}") } }
            if (_internalState.value.selectedGroup?.id == groupId) clearActiveGroupInternal()
            _internalState.update { it.copy(isLoading = false) }
        }
    }

    fun onCaptureButtonPressed(
        imageUrl: String, hunterLat: Double, hunterLon: Double,
        hunterHeading: Double, capturedAt: Long
    ) {
        val hunter = uiState.value.currentUser ?: return
        val state = uiState.value
        val assignment = state.activeAssignment
        val selectedGroup = state.selectedGroup

        // ── Group snipe ───────────────────────────────────────────
        if (assignment != null && selectedGroup != null) {
            if (!selectedGroup.gameActive) { _internalState.update { it.copy(verificationStatus = VerificationStatus.IDLE) }; return }
            if (assignment.isHunterEliminated || assignment.isTargetEliminated) {
                _internalState.update { it.copy(verificationStatus = VerificationStatus.FAILED_ALREADY_ELIMINATED) }; return
            }
            _internalState.update { it.copy(verificationStatus = VerificationStatus.VERIFYING, errorMessage = null) }
            viewModelScope.launch {
                repository.submitSnipe(
                    hunterId = hunter.id, targetId = assignment.targetId, imageUrl = imageUrl,
                    hunterLat = hunterLat, hunterLon = hunterLon, hunterHeading = hunterHeading,
                    capturedAt = capturedAt, groupId = selectedGroup.id
                ).onSuccess { points ->
                    assignmentRepository.markEliminated(selectedGroup.id, hunter.id, assignment.targetId)
                    _internalState.update { it.copy(verificationStatus = VerificationStatus.SUCCESS, lastPointsAwarded = points) }
                    onSnipeSuccessEvent.tryEmit(Unit)
                }.onFailure { e ->
                    _internalState.update {
                        it.copy(
                            verificationStatus = when (e.message) {
                                "TOO_FAR" -> VerificationStatus.FAILED_LOCATION
                                "TOO_OLD" -> VerificationStatus.FAILED_TIME
                                "WRONG_ORIENTATION" -> VerificationStatus.FAILED_ORIENTATION
                                "NOT_IN_GROUP" -> VerificationStatus.FAILED_NOT_IN_GROUP
                                else -> VerificationStatus.IDLE
                            },
                            errorMessage = e.message
                        )
                    }
                }
            }
            return
        }

        // ── Legacy global snipe ───────────────────────────────────
        val targetId = hunter.currentTargetId ?: return
        _internalState.update { it.copy(verificationStatus = VerificationStatus.VERIFYING, errorMessage = null) }
        viewModelScope.launch {
            repository.submitSnipe(
                hunterId = hunter.id, targetId = targetId, imageUrl = imageUrl,
                hunterLat = hunterLat, hunterLon = hunterLon, hunterHeading = hunterHeading,
                capturedAt = capturedAt, groupId = ""
            ).onSuccess { points ->
                _internalState.update { it.copy(verificationStatus = VerificationStatus.SUCCESS, lastPointsAwarded = points) }
                onSnipeSuccessEvent.tryEmit(Unit)
            }.onFailure { e ->
                _internalState.update {
                    it.copy(
                        verificationStatus = when (e.message) {
                            "TOO_FAR" -> VerificationStatus.FAILED_LOCATION
                            "TOO_OLD" -> VerificationStatus.FAILED_TIME
                            "WRONG_ORIENTATION" -> VerificationStatus.FAILED_ORIENTATION
                            else -> VerificationStatus.IDLE
                        },
                        errorMessage = e.message
                    )
                }
            }
        }
    }

    // ── Group selection ───────────────────────────────────────────

    fun onSelectGroup(group: Group) {
        val userId = uiState.value.currentUser?.id ?: return
        if (_internalState.value.selectedGroup?.id == group.id) { clearActiveGroupInternal(); return }

        _internalState.update { it.copy(selectedGroup = group, activeAssignment = null, activeTarget = null, activeTargetRaw = null) }
        viewModelScope.launch {
            assignmentRepository.setActiveGroup(userId, group.id)
            if (group.gameActive) assignmentRepository.triggerDailyResetIfNeeded(group.id)
            startListeningToAssignment(group.id, userId)
        }
    }

    private fun startListeningToAssignment(groupId: String, hunterId: String) {
        assignmentJob?.cancel(); activeTargetJob?.cancel(); activeTargetRawJob?.cancel()

        assignmentJob = viewModelScope.launch {
            assignmentRepository.getAssignment(groupId, hunterId).collect { assignment ->
                _internalState.update { it.copy(activeAssignment = assignment) }
                activeTargetJob?.cancel(); activeTargetRawJob?.cancel()
                val targetId = assignment?.targetId ?: run {
                    _internalState.update { it.copy(activeTarget = null, activeTargetRaw = null) }
                    return@collect
                }
                // Public profile for display
                activeTargetJob = launch {
                    repository.getUserById(targetId).collect { user ->
                        _internalState.update { it.copy(activeTarget = user) }
                    }
                }
                // Raw doc with coordinates for distance/bearing
                activeTargetRawJob = launch {
                    repository.getUserByIdRaw(targetId).collect { user ->
                        _internalState.update { it.copy(activeTargetRaw = user) }
                    }
                }
            }
        }
    }

    private fun clearActiveGroupInternal() {
        assignmentJob?.cancel(); activeTargetJob?.cancel(); activeTargetRawJob?.cancel()
        val userId = uiState.value.currentUser?.id
        if (userId != null) viewModelScope.launch { assignmentRepository.setActiveGroup(userId, null) }
        _internalState.update { it.copy(selectedGroup = null, activeAssignment = null, activeTarget = null, activeTargetRaw = null) }
    }

    private fun restoreActiveGroup(groupId: String) {
        viewModelScope.launch {
            uiState.collect { state ->
                if (state.userGroups.isNotEmpty()) {
                    state.userGroups.find { it.id == groupId }?.let { onSelectGroup(it) }
                    return@collect
                }
            }
        }
    }

    // ── Admin: members ────────────────────────────────────────────

    fun loadGroupMembers(groupId: String) {
        groupMembersJob?.cancel()
        _internalState.update { it.copy(groupMembers = emptyList()) }
        groupMembersJob = viewModelScope.launch {
            repository.getGroupMembers(groupId).collect { members ->
                _internalState.update { it.copy(groupMembers = members) }
            }
        }
    }

    fun clearGroupMembers() {
        groupMembersJob?.cancel(); groupMembersJob = null
        _internalState.update { it.copy(groupMembers = emptyList()) }
    }

    fun onKickMember(groupId: String, targetUserId: String) {
        val adminId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            repository.kickMember(groupId, targetUserId, adminId)
                .onFailure { e -> _internalState.update { it.copy(errorMessage = e.message) } }
        }
    }

    fun onDeleteGroup(groupId: String) {
        val adminId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            repository.deleteGroup(groupId, adminId)
                .onFailure { e -> _internalState.update { it.copy(errorMessage = e.message) } }
            if (_internalState.value.selectedGroup?.id == groupId) clearActiveGroupInternal()
        }
    }

    fun onRenameGroup(groupId: String, newName: String) {
        val adminId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            repository.renameGroup(groupId, newName, adminId)
                .onFailure { e -> _internalState.update { it.copy(errorMessage = e.message) } }
        }
    }

    fun onTransferAdmin(groupId: String, newAdminId: String) {
        val adminId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            repository.transferAdmin(groupId, newAdminId, adminId)
                .onFailure { e -> _internalState.update { it.copy(errorMessage = e.message) } }
        }
    }

    // ── Admin: session ────────────────────────────────────────────

    fun onActivateGroup(groupId: String, durationDays: Int) {
        val adminId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            assignmentRepository.activateGroup(groupId, adminId, durationDays)
                .onFailure { e -> _internalState.update { it.copy(errorMessage = e.message) } }
        }
    }

    fun onDeactivateGroup(groupId: String) {
        val adminId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            assignmentRepository.deactivateGroup(groupId, adminId)
                .onFailure { e -> _internalState.update { it.copy(errorMessage = e.message) } }
        }
    }

    fun onForceReassign(groupId: String) {
        val adminId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            assignmentRepository.forceReassign(groupId, adminId)
                .onFailure { e -> _internalState.update { it.copy(errorMessage = e.message) } }
        }
    }

    // ── Challenges ────────────────────────────────────────────────

    private fun startChallengeListener(userId: String) {
        challengesJob?.cancel()
        challengesJob = viewModelScope.launch {
            repository.getPendingChallenges(userId).collect { challenges ->
                _internalState.update { it.copy(pendingChallenges = challenges) }
            }
        }
    }

    fun onSubmitChallenge(snipe: Snipe) {
        val user = uiState.value.currentUser ?: return
        viewModelScope.launch {
            repository.submitChallenge(snipe, user.id, user.name)
                .onFailure { e -> _internalState.update { it.copy(errorMessage = e.message) } }
        }
    }

    fun onSelectChallenge(challenge: SnipeChallenge?) {
        _internalState.update { it.copy(selectedChallenge = challenge) }
    }

    fun onUpholdChallenge(challenge: SnipeChallenge) {
        val adminId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            repository.upholdChallenge(challenge, adminId)
                .onSuccess { _internalState.update { it.copy(selectedChallenge = null) } }
                .onFailure { e -> _internalState.update { it.copy(errorMessage = e.message) } }
        }
    }

    fun onOverturnChallenge(challenge: SnipeChallenge) {
        val adminId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            repository.overturnChallenge(challenge, adminId)
                .onSuccess { _internalState.update { it.copy(selectedChallenge = null) } }
                .onFailure { e -> _internalState.update { it.copy(errorMessage = e.message) } }
        }
    }
}