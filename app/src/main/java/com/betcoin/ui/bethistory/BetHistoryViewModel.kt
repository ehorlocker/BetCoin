package com.betcoin.ui.bethistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.repository.BetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** The tabs of the Bet History screen, with their labels and per-tab empty-state copy. */
enum class BetHistoryFilter(val label: String, val emptyMessage: String) {
    ALL("All", "No bets yet"),
    ACTIVE("Active", "No active bets"),
    RESOLVED("Resolved", "No resolved bets"),
    CANCELLED("Cancelled", "No cancelled bets"),
    ;

    /** Whether a bet with [status] belongs under this tab. Pending bets belong to no tab. */
    internal fun matches(status: BetStatus): Boolean = when (this) {
        ALL -> status != BetStatus.PENDING_CONFIRMATION
        ACTIVE -> status == BetStatus.ACTIVE
        RESOLVED -> status == BetStatus.RESOLVED
        CANCELLED -> status == BetStatus.CANCELLED
    }
}

/** One entry of the history list; [pot] is the sum of the participants' wagers. */
data class BetHistoryRow(
    val betId: Long,
    val prompt: String,
    val status: BetStatus,
    val createdAt: Long,
    val participantCount: Int,
    val pot: Long,
)

/** UI state for the Bet History screen; [rows] is already filtered for [filter]. */
data class BetHistoryUiState(
    val isLoading: Boolean = true,
    val isError: Boolean = false,
    val filter: BetHistoryFilter = BetHistoryFilter.ALL,
    val rows: List<BetHistoryRow> = emptyList(),
)

/**
 * ViewModel for the Bet History screen (§4.2 Bet History).
 *
 * Combines [BetRepository.getAllBets] with the selected [BetHistoryFilter] so the selected tab
 * survives repository re-emissions. Rows are sorted newest first (then highest id), and pending
 * bets are excluded even though the repository already omits them.
 */
@HiltViewModel
class BetHistoryViewModel @Inject constructor(
    betRepository: BetRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(BetHistoryFilter.ALL)

    val uiState: StateFlow<BetHistoryUiState> =
        combine(betRepository.getAllBets(), filter) { bets, selected ->
            BetHistoryUiState(
                isLoading = false,
                filter = selected,
                rows = bets
                    .filter { selected.matches(it.bet.status) }
                    .sortedWith(
                        compareByDescending<BetWithDetails> { it.bet.createdAt }
                            .thenByDescending { it.bet.id },
                    )
                    .map { it.toRow() },
            )
        }
            .catch { emit(BetHistoryUiState(isLoading = false, isError = true, filter = filter.value)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BetHistoryUiState())

    fun selectFilter(filter: BetHistoryFilter) {
        this.filter.update { filter }
    }

    private fun BetWithDetails.toRow() = BetHistoryRow(
        betId = bet.id,
        prompt = bet.prompt,
        status = bet.status,
        createdAt = bet.createdAt,
        participantCount = participants.size,
        pot = participants.sumOf { it.participant.wagerAmount },
    )
}
