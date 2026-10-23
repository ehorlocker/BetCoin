package com.betcoin.ui.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.avatar.AvatarStore
import com.betcoin.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the Leaderboard screen (§4.2 Leaderboard).
 *
 * Ranks come from the order of [UserRepository.getLeaderboard], which already sorts by net worth,
 * then balance, then username; this class only numbers the rows and derives net worth
 * (balance minus total debt, which can be negative).
 */
@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    userRepository: UserRepository,
    private val avatarStore: AvatarStore,
) : ViewModel() {

    data class Row(
        val userId: Long,
        val rank: Int,
        val username: String,
        val netWorth: Long,
        val wins: Int,
        val losses: Int,
        val avatarModel: Any? = null,
    )

    data class UiState(
        val isLoading: Boolean = true,
        val rows: List<Row> = emptyList(),
    )

    val uiState: StateFlow<UiState> =
        userRepository.getLeaderboard()
            .map { users ->
                UiState(
                    isLoading = false,
                    rows = users.mapIndexed { index, user ->
                        Row(
                            userId = user.id,
                            rank = index + 1,
                            username = user.username,
                            netWorth = user.balance - user.totalDebt,
                            wins = user.totalWins,
                            losses = user.totalLosses,
                            avatarModel = user.avatarRef?.let { avatarStore.load(it) },
                        )
                    },
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())
}
