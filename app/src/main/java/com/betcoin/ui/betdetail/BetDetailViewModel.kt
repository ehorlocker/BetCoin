package com.betcoin.ui.betdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.model.BetCoinException
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.model.InsufficientBalanceException
import com.betcoin.data.model.InvalidBetStateException
import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.repository.BetRepository
import com.betcoin.data.repository.UserRepository
import com.betcoin.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Bet Detail screen.
 *
 * Observes [BetRepository.getBetWithDetails] for the `betId` navigation argument and maps it
 * to a [BetDetailUiState]. Emits [BetDetailUiState.NotFound] when the bet doesn't exist, for
 * example after it is deleted.
 *
 * It also drives the Resolve, Cancel and Reopen actions through [actionState]: the screen shows
 * the outcome picker or the PIN consent sequence from it, verifies each PIN with
 * [verifyParticipantPin], and calls [onAllPinsConfirmed] once everyone has agreed. Action state
 * is in-memory only and no PIN is ever kept.
 */
@HiltViewModel
class BetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val betRepository: BetRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val betId: Long = checkNotNull(savedStateHandle[Routes.BET_ID_ARG]) {
        "Bet Detail needs a ${Routes.BET_ID_ARG} argument"
    }

    /**
     * Shared eagerly (not `WhileSubscribed`) so the action requests always see the latest bet
     * even when the UI isn't collecting, and so a stale picker or consent can be closed.
     */
    val uiState: StateFlow<BetDetailUiState> = betRepository.getBetWithDetails(betId)
        .map { details -> details?.let { BetDetailUiState.Success(it.toBetDetail()) } ?: BetDetailUiState.NotFound }
        .catch { emit(BetDetailUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BetDetailUiState.Loading)

    private val _actionState = MutableStateFlow(BetActionState())

    /** The picker, consent and busy/error state of the Resolve, Cancel and Reopen actions. */
    val actionState: StateFlow<BetActionState> = _actionState.asStateFlow()

    init {
        // Close the picker or consent once the bet no longer allows the pending action, and drop
        // an error that was about the bet's previous status.
        viewModelScope.launch {
            var lastStatus: BetStatus? = null
            uiState.collect { state ->
                if (state is BetDetailUiState.Loading || state is BetDetailUiState.Error) return@collect
                val detail = (state as? BetDetailUiState.Success)?.detail
                val statusChanged = lastStatus != null && detail?.status != lastStatus
                lastStatus = detail?.status ?: lastStatus
                _actionState.update { current ->
                    if (current.isBusy) return@update current
                    val cleared = if (statusChanged) current.copy(errorMessage = null) else current
                    cleared.closeStale(detail)
                }
            }
        }
    }

    private fun BetActionState.closeStale(detail: BetDetail?): BetActionState {
        val pickerOk = pickingOutcome && detail?.status == BetStatus.ACTIVE
        val stillValid = consent?.takeIf { detail != null && detail.allows(it.action) }
        return copy(pickingOutcome = pickerOk, consent = stillValid)
    }

    private fun currentDetail(): BetDetail? = (uiState.value as? BetDetailUiState.Success)?.detail

    /** Starts Resolve: asks the screen to show the outcome picker. Ignored unless ACTIVE and idle. */
    fun onResolveRequested() {
        val detail = currentDetail() ?: return
        if (detail.status != BetStatus.ACTIVE || _actionState.value.isBusy) return
        _actionState.update { it.copy(pickingOutcome = true, errorMessage = null) }
    }

    /** The outcome picker was dismissed: nothing starts. */
    fun onOutcomePickerDismissed() {
        _actionState.update { it.copy(pickingOutcome = false) }
    }

    /** The user chose [outcomeId] as the winner: closes the picker and asks for consent. */
    fun onOutcomePicked(outcomeId: Long) {
        val detail = currentDetail() ?: return
        val outcome = detail.outcomes.firstOrNull { it.id == outcomeId } ?: return
        if (!_actionState.value.pickingOutcome) return
        _actionState.update {
            it.copy(
                pickingOutcome = false,
                consent = PendingConsent(BetAction.Resolve(outcome.id, outcome.text), "Resolve: ${outcome.text} wins"),
            )
        }
    }

    /** Starts Cancel: asks every participant to consent. Ignored unless ACTIVE and idle. */
    fun onCancelRequested() {
        val detail = currentDetail() ?: return
        if (detail.status != BetStatus.ACTIVE || _actionState.value.isBusy) return
        _actionState.update {
            it.copy(consent = PendingConsent(BetAction.Cancel, "Cancel bet: all wagers refunded"), errorMessage = null)
        }
    }

    /**
     * Starts Reopen: asks every participant to consent. Ignored unless RESOLVED or CANCELLED,
     * no participant was deleted ([BetDetail.reopenBlockedReason] is null), and idle.
     */
    fun onReopenRequested() {
        val detail = currentDetail() ?: return
        if (!detail.allows(BetAction.Reopen) || _actionState.value.isBusy) return
        _actionState.update { it.copy(consent = PendingConsent(BetAction.Reopen, "Reopen bet"), errorMessage = null) }
    }

    /** PIN check for one participant of the consent sequence. Never throws, as the PIN dialog requires. */
    suspend fun verifyParticipantPin(userId: Long, pin: String): Boolean =
        try {
            userRepository.verifyPin(userId, pin)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    /** A participant cancelled the PIN sequence: nothing changes. */
    fun onConsentAborted() {
        _actionState.update { it.copy(consent = null) }
    }

    /** Every participant's PIN was accepted: runs the pending action. */
    fun onAllPinsConfirmed() {
        val state = _actionState.value
        val action = state.consent?.action ?: return
        if (state.isBusy) return
        _actionState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                when (action) {
                    is BetAction.Resolve -> betRepository.resolveBet(betId, action.outcomeId)
                    BetAction.Cancel -> betRepository.cancelBet(betId)
                    BetAction.Reopen -> betRepository.reopenBet(betId)
                }
                _actionState.update { it.copy(consent = null, isBusy = false, errorMessage = null) }
            } catch (e: BetCoinException) {
                val message = e.toActionMessage(currentDetail()?.participants.orEmpty())
                _actionState.update { it.copy(consent = null, isBusy = false, errorMessage = message) }
            }
        }
    }
}

/** An action on a bet that needs every participant's PIN. */
sealed interface BetAction {
    data class Resolve(val outcomeId: Long, val outcomeText: String) : BetAction
    data object Cancel : BetAction
    data object Reopen : BetAction
}

/** [action] is waiting for every participant's PIN; [title] summarises it in each PIN dialog. */
data class PendingConsent(val action: BetAction, val title: String)

/** State of the Resolve, Cancel and Reopen actions on the Bet Detail screen. */
data class BetActionState(
    val pickingOutcome: Boolean = false,
    val consent: PendingConsent? = null,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
)

private fun BetDetail.allows(action: BetAction): Boolean = when (action) {
    is BetAction.Resolve, BetAction.Cancel -> status == BetStatus.ACTIVE
    BetAction.Reopen -> reopenBlockedReason == null &&
        (status == BetStatus.RESOLVED || status == BetStatus.CANCELLED)
}

private fun BetCoinException.toActionMessage(participants: List<ParticipantRow>): String = when (this) {
    is InsufficientBalanceException -> {
        val name = participants.firstOrNull { it.userId == userId }?.name ?: "A participant"
        "$name needs $required BetCoin but has $available"
    }
    is InvalidInputException -> "A participant was deleted, so this can't be done here. Use the Admin Panel."
    is InvalidBetStateException -> "This bet has changed. Nothing was updated."
    else -> message ?: "Something went wrong"
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
    /** Why Reopen is unavailable (a participant was deleted), or null when it isn't blocked. */
    val reopenBlockedReason: String?,
)

data class OutcomeRow(
    val id: Long,
    val text: String,
    val isWinner: Boolean,
)

data class ParticipantRow(
    val id: Long,
    /** The participant's user ID, or null when the user was deleted. */
    val userId: Long?,
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

    val deletedNames = participants.filter { it.participant.userId == null }.map { row ->
        row.participant.deletedUsername ?: DELETED_PLAYER
    }
    val reopenable = bet.status == BetStatus.RESOLVED || bet.status == BetStatus.CANCELLED
    val reopenBlockedReason = if (reopenable && deletedNames.isNotEmpty()) {
        val verb = if (deletedNames.size == 1) "was" else "were"
        "Can't reopen: ${deletedNames.joinToString(", ")} $verb deleted"
    } else {
        null
    }

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
                userId = participant.userId,
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
        reopenBlockedReason = reopenBlockedReason,
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
