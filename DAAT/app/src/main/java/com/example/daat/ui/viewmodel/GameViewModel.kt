package com.example.daat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.daat.data.model.User
import com.example.daat.data.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GameUiState(
    val currentUser: User? = null,
    val currentTarget: User? = null,
    val leaderboard: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class GameViewModel(
    private val repository: GameRepository
) : ViewModel() {

    val uiState: StateFlow<GameUiState> = combine(
        repository.getCurrentUser(),
        repository.getLeaderboard("global") // Placeholder group
    ) { user, leaderboard ->
        GameUiState(
            currentUser = user,
            leaderboard = leaderboard
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GameUiState(isLoading = true)
    )

    fun captureSnipe(imageUrl: String) {
        val hunterId = uiState.value.currentUser?.id ?: return
        val targetId = uiState.value.currentTarget?.id ?: "user2" // Demo target

        viewModelScope.launch {
            repository.submitSnipe(hunterId, targetId, imageUrl)
        }
    }
}
