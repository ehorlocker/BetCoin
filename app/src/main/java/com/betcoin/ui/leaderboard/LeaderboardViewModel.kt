package com.betcoin.ui.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.database.entity.User
import com.betcoin.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LeaderboardUiState {
    data object Loading : LeaderboardUiState()
    data class Success(val items: List<LeaderboardItem>) : LeaderboardUiState()
    data object Empty : LeaderboardUiState()
}

data class LeaderboardItem(
    val userId: Long,
    val rank: Int,
    val username: String,
    val balance: Long,
    val totalWins: Int,
    val totalLosses: Int,
    val totalEarnings: Long,
    val totalLost: Long,
    val bailoutCount: Int,
    val totalDebt: Long,
)

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LeaderboardUiState>(LeaderboardUiState.Loading)
    val uiState: StateFlow<LeaderboardUiState> = _uiState

    init {
        viewModelScope.launch {
            userRepository.getLeaderboard().collect { users ->
                if (users.isEmpty()) {
                    _uiState.value = LeaderboardUiState.Empty
                } else {
                    val items = users.mapIndexed { index, user ->
                        LeaderboardItem(
                            userId = user.id,
                            rank = index + 1,
                            username = user.username,
                            balance = user.balance,
                            totalWins = user.totalWins,
                            totalLosses = user.totalLosses,
                            totalEarnings = user.totalEarnings,
                            totalLost = user.totalLost,
                            bailoutCount = user.bailoutCount,
                            totalDebt = user.totalDebt,
                        )
                    }
                    _uiState.value = LeaderboardUiState.Success(items)
                }
            }
        }
    }
}
