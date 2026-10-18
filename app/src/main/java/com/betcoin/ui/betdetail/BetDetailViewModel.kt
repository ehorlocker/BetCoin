package com.betcoin.ui.betdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.repository.BetRepository
import com.betcoin.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the read-only Bet Detail screen.
 *
 * Observes [BetRepository.getBetWithDetails] for the `betId` navigation argument and maps it
 * to a [BetDetailUiState]. Emits [BetDetailUiState.NotFound] when the bet doesn't exist, for
 * example after it is deleted.
 */
@HiltViewModel
class BetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    betRepository: BetRepository,
) : ViewModel() {

    private val betId: Long = checkNotNull(savedStateHandle[Routes.BET_ID_ARG]) {
        "Bet Detail needs a ${Routes.BET_ID_ARG} argument"
    }

    val uiState: StateFlow<BetDetailUiState> = betRepository.getBetWithDetails(betId)
        .map { details -> details?.let { BetDetailUiState.Success(it.toBetDetail()) } ?: BetDetailUiState.NotFound }
        .catch { emit(BetDetailUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BetDetailUiState.Loading)
}

/** UI state for the Bet Detail screen. */
sealed interface BetDetailUiState {
    data object Loading : BetDetailUiState
    data object NotFound : BetDetailUiState
    data object Error : BetDetailUiState
    data class Success(val detail: BetDetail) : BetDetailUiState
}

/** Everything the Bet Detail screen displays for one bet. */
data class BetDetail(
    val prompt: String,
    val status: BetStatus,
    val createdAt: Long,
    val resolvedAt: Long?,
    val outcomes: List<OutcomeRow>,
    val participants: List<ParticipantRow>,
    val potSize: Long,
    /** The winning outcome's text, or null unless the bet is RESOLVED. */
    val winningOutcome: String?,
    /** True for a RESOLVED bet where no one picked the winning outcome (§5.3). */
    val isPush: Boolean,
)

data class OutcomeRow(
    val id: Long,
    val text: String,
    val isWinner: Boolean,
)

data class ParticipantRow(
    val id: Long,
    /** The user's name, or the snapshot kept when the user was deleted (§5.7). */
    val name: String,
    val isDeletedUser: Boolean,
    val pick: String,
    val wager: Long,
    val result: ParticipantResult,
    /** `payout − wager` once the bet is resolved, otherwise null. */
    val net: Long?,
)

/** A participant's result indicator, derived from the bet's status. */
enum class ParticipantResult {
    /** The bet hasn't been resolved yet. */
    PENDING,
    WIN,
    LOSS,

    /** Resolved with no one on the winning outcome, so the wager was refunded. */
    PUSH,

    /** The bet was cancelled, so the wager was refunded. */
    REFUNDED,
}

internal fun BetWithDetails.toBetDetail(): BetDetail {
    val resolved = bet.status == BetStatus.RESOLVED
    val winningId = bet.winningOutcomeId.takeIf { resolved }
    val isPush = resolved && participants.none { it.participant.outcomeId == winningId }

    return BetDetail(
        prompt = bet.prompt,
        status = bet.status,
        createdAt = bet.createdAt,
        resolvedAt = bet.resolvedAt,
        outcomes = outcomes.sortedBy { it.id }.map { outcome ->
            OutcomeRow(id = outcome.id, text = outcome.outcomeText, isWinner = outcome.id == winningId)
        },
        participants = participants.sortedBy { it.participant.id }.map { row ->
            val participant = row.participant
            val result = when (bet.status) {
                BetStatus.RESOLVED -> when {
                    isPush -> ParticipantResult.PUSH
                    participant.outcomeId == winningId -> ParticipantResult.WIN
                    else -> ParticipantResult.LOSS
                }
                BetStatus.CANCELLED -> ParticipantResult.REFUNDED
                BetStatus.ACTIVE, BetStatus.PENDING_CONFIRMATION -> ParticipantResult.PENDING
            }
            ParticipantRow(
                id = participant.id,
                name = row.user?.username ?: participant.deletedUsername ?: DELETED_PLAYER,
                isDeletedUser = row.user == null,
                pick = row.outcome.outcomeText,
                wager = participant.wagerAmount,
                result = result,
                net = participant.payout?.takeIf { resolved }?.let { it - participant.wagerAmount },
            )
        },
        potSize = participants.sumOf { it.participant.wagerAmount },
        winningOutcome = outcomes.firstOrNull { it.id == winningId }?.outcomeText,
        isPush = isPush,
    )
}

/** Shown when a deleted participant has no username snapshot. */
internal const val DELETED_PLAYER = "Deleted player"

/** Formats a net payout as `+150`, `−200` (with a minus sign) or `±0`. */
internal fun formatNet(net: Long): String = when {
    net > 0 -> "+$net"
    net < 0 -> "\u2212${-net}"
    else -> "\u00B10"
}
