package com.betcoin.ui.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.database.entity.User
import com.betcoin.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LeaderboardUiState {
    data object Loading : LeaderboardUiState()
    data class Success(val items: List<LeaderboardItem>) : LeaderboardUiState()
    data object Empty : LeaderboardUiState()
}

data class LeaderboardItem(
    val rank: Int,
    val user: User,
)

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LeaderboardUiState>(LeaderboardUiState.Loading)
    val uiState: StateFlow<LeaderboardUiState> = _uiState

    init {
        viewModelScope.launch {
            userRepository.getLeaderboard().collectLatest { users ->
                if (users.isEmpty()) {
                    _uiState.value = LeaderboardUiState.Empty
                } else {
                    val items = users.mapIndexed { index, user ->
                        LeaderboardItem(rank = index + 1, user = user)
                    }
                    _uiState.value = LeaderboardUiState.Success(items)
                }
            }
        }
    }
}
