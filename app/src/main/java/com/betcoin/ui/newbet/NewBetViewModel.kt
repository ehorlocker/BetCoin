package com.betcoin.ui.newbet

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.BetCoinException
import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.model.InsufficientBalanceException
import com.betcoin.data.model.InvalidBetStateException
import com.betcoin.data.model.ParticipantWithUser
import com.betcoin.data.model.UnanimousPickException
import com.betcoin.data.repository.BetRepository
import com.betcoin.data.repository.UserRepository
import com.betcoin.navigation.Routes
import com.betcoin.ui.common.GENERIC_ERROR_MESSAGE
import com.betcoin.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

/** Steps of the New Bet flow (§4.2). */
enum class NewBetStep { A, B, C }

/**
 * ViewModel for the New Bet flow, Steps A to C (§4.2, §8).
 *
 * Step A collects the prompt and outcomes and writes the pending bet with `createBet` on Next.
 * Step B adds participants one at a time: each is written with `addParticipant` as soon as their
 * PIN is accepted, so §11 Recovery can restore them. Leaving the flow after the bet exists (or
 * after text was entered in Step A) asks "Discard this bet?" and deletes it with
 * `discardPendingBet`.
 *
 * Step C reviews the bet and locks it: unless the picks are unanimous, every participant re-enters
 * their PIN in list order (the screen drives that sequence), then `lockBet` runs. Back returns to
 * Step B with the participants kept. A successful lock sets [UiState.lockedBetId].
 *
 * The pending bet's ID comes from the optional [Routes.NEW_BET_BET_ID_ARG] navigation argument
 * (Recovery and Repeat open Step B directly) or from `createBet`, and is kept in
 * [SavedStateHandle] so a restored process reopens Step B instead of starting over.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NewBetViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val betRepository: BetRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    /** What a participant asked to join with, held while their PIN dialog is open. */
    data class PendingJoin(
        val user: User,
        val outcomeId: Long,
        val wager: Long,
    )

    data class UiState(
        val betId: Long? = null,
        // Step A
        val prompt: String = "",
        val outcomes: List<String> = List(MIN_OUTCOMES) { "" },
        // Step B
        val betPrompt: String = "",
        val betOutcomes: List<BetOutcome> = emptyList(),
        val participants: List<ParticipantWithUser> = emptyList(),
        val availableUsers: List<User> = emptyList(),
        val selectedUserId: Long? = null,
        val selectedOutcomeId: Long? = null,
        val wager: String = "",
        /** Non-null while the PIN dialog is asking [PendingJoin.user] to confirm. */
        val pendingJoin: PendingJoin? = null,
        // Step C
        /** True while Step C (Review & Lock) is showing. Not persisted: a restored process reopens Step B. */
        val reviewing: Boolean = false,
        /** True while the participants' PIN sequence is open. */
        val confirmingLock: Boolean = false,
        /** Set once `lockBet` succeeded; the screen navigates to this bet's detail. */
        val lockedBetId: Long? = null,
        // All steps
        val isBusy: Boolean = false,
        val errorMessage: String? = null,
        val showDiscardDialog: Boolean = false,
        /** True once the flow is finished and the screen should navigate back. */
        val exited: Boolean = false,
    ) {
        val step: NewBetStep
            get() = when {
                betId == null -> NewBetStep.A
                reviewing -> NewBetStep.C
                else -> NewBetStep.B
            }

        val promptError: String?
            get() = if (prompt.trim().length > MAX_PROMPT_LENGTH) {
                "Prompt must be $MAX_PROMPT_LENGTH characters or fewer"
            } else {
                null
            }

        /** One entry per outcome: why it's invalid, or null. Blank outcomes only block Next. */
        val outcomeErrors: List<String?>
            get() {
                val seen = mutableSetOf<String>()
                return outcomes.map { raw ->
                    val text = raw.trim()
                    when {
                        text.length > MAX_OUTCOME_LENGTH ->
                            "Outcome must be $MAX_OUTCOME_LENGTH characters or fewer"
                        text.isNotEmpty() && !seen.add(text.lowercase(Locale.ROOT)) ->
                            "Outcomes must be different"
                        else -> null
                    }
                }
            }

        val canAddOutcome: Boolean get() = outcomes.size < MAX_OUTCOMES
        val canRemoveOutcome: Boolean get() = outcomes.size > MIN_OUTCOMES

        val canProceedToStepB: Boolean
            get() = !isBusy &&
                prompt.isNotBlank() && promptError == null &&
                outcomes.size in MIN_OUTCOMES..MAX_OUTCOMES &&
                outcomes.all { it.isNotBlank() } && outcomeErrors.all { it == null }

        /** Whether leaving Step A needs the "Discard this bet?" confirmation. */
        val hasEnteredText: Boolean
            get() = prompt.isNotBlank() || outcomes.any { it.isNotBlank() }

        val selectedUser: User? get() = availableUsers.find { it.id == selectedUserId }

        val wagerError: String?
            get() {
                val user = selectedUser ?: return null
                if (user.balance < 1) return "${user.username} has no BetCoin to wager"
                if (wager.isEmpty()) return null
                val amount = wager.toLongOrNull()
                return when {
                    amount == null || amount < 1 -> "Wager must be at least 1"
                    amount > user.balance -> "${user.username} only has ${user.balance} BetCoin"
                    else -> null
                }
            }

        val canAddParticipant: Boolean
            get() = !isBusy && selectedUser != null &&
                betOutcomes.any { it.id == selectedOutcomeId } &&
                wager.isNotEmpty() && wagerError == null

        val canContinue: Boolean get() = !isBusy && participants.size >= MIN_PARTICIPANTS

        /** All participants picked the same outcome (§5.2), so the bet can't be locked. */
        val isUnanimous: Boolean
            get() = participants.size >= MIN_PARTICIPANTS &&
                participants.map { it.participant.outcomeId }.distinct().size == 1

        /** Why Step C can't lock the bet right now, or null. Checked against current balances. */
        val lockBlocker: String?
            get() {
                if (participants.size < MIN_PARTICIPANTS) {
                    return "A bet needs at least $MIN_PARTICIPANTS participants"
                }
                if (isUnanimous) {
                    return "Everyone picked the same outcome. Go back and change at least one pick."
                }
                participants.forEach { entry ->
                    val user = entry.user
                    if (user == null) {
                        val name = entry.participant.deletedUsername ?: "A participant"
                        return "$name no longer exists. Go back and remove them."
                    }
                    if (entry.participant.wagerAmount > user.balance) {
                        return balanceChangedMessage(user.username, entry.participant.wagerAmount, user.balance)
                    }
                }
                return null
            }

        val canLock: Boolean get() = !isBusy && !confirmingLock && lockBlocker == null
    }

    private val form = MutableStateFlow(
        UiState(betId = savedStateHandle.get<Long>(Routes.NEW_BET_BET_ID_ARG)?.takeIf { it > 0 })
    )

    private val betDetails: Flow<BetWithDetails?> = form
        .map { it.betId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else betRepository.getBetWithDetails(id) }

    val uiState: StateFlow<UiState> =
        combine(form, betDetails, userRepository.getAllUsers()) { state, details, users ->
            if (details == null) {
                state
            } else {
                val joined = details.participants.mapNotNull { it.participant.userId }.toSet()
                state.copy(
                    betPrompt = details.bet.prompt,
                    betOutcomes = details.outcomes,
                    participants = details.participants,
                    availableUsers = users.filterNot { it.id in joined },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value)

    // --- Step A ---

    fun onPromptChanged(prompt: String) {
        form.update { it.copy(prompt = prompt, errorMessage = null) }
    }

    fun onOutcomeChanged(index: Int, text: String) {
        form.update { state ->
            if (index !in state.outcomes.indices) return@update state
            state.copy(
                outcomes = state.outcomes.toMutableList().also { it[index] = text },
                errorMessage = null,
            )
        }
    }

    fun onAddOutcome() {
        form.update { if (it.canAddOutcome) it.copy(outcomes = it.outcomes + "") else it }
    }

    fun onRemoveOutcome(index: Int) {
        form.update { state ->
            if (!state.canRemoveOutcome || index !in state.outcomes.indices) return@update state
            state.copy(outcomes = state.outcomes.toMutableList().also { it.removeAt(index) })
        }
    }

    /** Step A → B: writes the pending bet and its outcomes. */
    fun onNext() {
        val state = form.value
        if (state.step != NewBetStep.A || !state.canProceedToStepB) return
        form.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val betId = betRepository.createBet(state.prompt.trim(), state.outcomes.map { it.trim() })
                savedStateHandle[Routes.NEW_BET_BET_ID_ARG] = betId
                form.update { it.copy(betId = betId, isBusy = false) }
            } catch (e: BetCoinException) {
                form.update { it.copy(isBusy = false, errorMessage = e.toUserMessage()) }
            } catch (e: IOException) {
                form.update { it.copy(isBusy = false, errorMessage = GENERIC_ERROR_MESSAGE) }
            } catch (e: SQLiteException) {
                form.update { it.copy(isBusy = false, errorMessage = GENERIC_ERROR_MESSAGE) }
            }
        }
    }

    // --- Step B ---

    fun onUserSelected(userId: Long) {
        form.update { it.copy(selectedUserId = userId, errorMessage = null) }
    }

    fun onOutcomeSelected(outcomeId: Long) {
        form.update { it.copy(selectedOutcomeId = outcomeId, errorMessage = null) }
    }

    fun onWagerChanged(wager: String) {
        form.update { it.copy(wager = wager.filter(Char::isDigit), errorMessage = null) }
    }

    /** Opens the PIN dialog for the selected user. The join is written once the PIN is accepted. */
    fun onAddParticipant() {
        val state = uiState.value
        if (!state.canAddParticipant) return
        val user = state.selectedUser ?: return
        val outcomeId = state.selectedOutcomeId ?: return
        val wager = state.wager.toLongOrNull() ?: return
        form.update { it.copy(pendingJoin = PendingJoin(user, outcomeId, wager), errorMessage = null) }
    }

    /** PIN check for the open dialog. Never throws, as [com.betcoin.ui.components.PinEntryDialog] requires. */
    suspend fun verifyPin(pin: String): Boolean {
        val join = form.value.pendingJoin ?: return false
        return try {
            userRepository.verifyPin(join.user.id, pin)
        } catch (e: BetCoinException) {
            false
        } catch (e: IOException) {
            false
        } catch (e: SQLiteException) {
            false
        }
    }

    fun onPinVerified() {
        val betId = form.value.betId ?: return
        val join = form.value.pendingJoin ?: return
        form.update { it.copy(pendingJoin = null, isBusy = true) }
        viewModelScope.launch {
            try {
                betRepository.addParticipant(betId, join.user.id, join.outcomeId, join.wager)
                form.update {
                    it.copy(isBusy = false, selectedUserId = null, selectedOutcomeId = null, wager = "")
                }
            } catch (e: BetCoinException) {
                form.update { it.copy(isBusy = false, errorMessage = e.toUserMessage()) }
            } catch (e: IOException) {
                form.update { it.copy(isBusy = false, errorMessage = GENERIC_ERROR_MESSAGE) }
            } catch (e: SQLiteException) {
                form.update { it.copy(isBusy = false, errorMessage = GENERIC_ERROR_MESSAGE) }
            }
        }
    }

    fun onPinDismissed() {
        form.update { it.copy(pendingJoin = null) }
    }

    fun onRemoveParticipant(participantId: Long) {
        viewModelScope.launch {
            try {
                betRepository.removeParticipant(participantId)
            } catch (e: BetCoinException) {
                form.update { it.copy(errorMessage = e.toUserMessage()) }
            } catch (e: IOException) {
                form.update { it.copy(errorMessage = GENERIC_ERROR_MESSAGE) }
            } catch (e: SQLiteException) {
                form.update { it.copy(errorMessage = GENERIC_ERROR_MESSAGE) }
            }
        }
    }

    /** Step B → C. */
    fun onContinue() {
        val state = uiState.value
        if (state.step != NewBetStep.B || !state.canContinue) return
        form.update { it.copy(reviewing = true, errorMessage = null) }
    }

    // --- Step C ---

    /** Starts the PIN sequence, unless the bet can't be locked. */
    fun onLockRequested() {
        val state = uiState.value
        if (state.step != NewBetStep.C || !state.canLock) return
        form.update { it.copy(confirmingLock = true, errorMessage = null) }
    }

    /** PIN check for one participant of the lock sequence. Never throws, as [PinEntryDialog] requires. */
    suspend fun verifyParticipantPin(userId: Long, pin: String): Boolean =
        try {
            userRepository.verifyPin(userId, pin)
        } catch (e: BetCoinException) {
            false
        } catch (e: IOException) {
            false
        } catch (e: SQLiteException) {
            false
        }

    /** A participant cancelled the PIN sequence: nothing is locked. */
    fun onLockAborted() {
        form.update { it.copy(confirmingLock = false) }
    }

    /** Every participant's PIN was accepted: locks the bet. */
    fun onAllPinsConfirmed() {
        val state = form.value
        val betId = state.betId ?: return
        if (!state.confirmingLock || state.isBusy) return
        form.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                betRepository.lockBet(betId)
                form.update { it.copy(isBusy = false, confirmingLock = false, lockedBetId = betId) }
            } catch (e: BetCoinException) {
                val message = e.toLockMessage(uiState.value.participants)
                form.update { it.copy(isBusy = false, confirmingLock = false, errorMessage = message) }
            } catch (e: IOException) {
                form.update {
                    it.copy(isBusy = false, confirmingLock = false, errorMessage = GENERIC_ERROR_MESSAGE)
                }
            } catch (e: SQLiteException) {
                form.update {
                    it.copy(isBusy = false, confirmingLock = false, errorMessage = GENERIC_ERROR_MESSAGE)
                }
            }
        }
    }

    // --- Leaving the flow ---

    /** System Back. From Step C it returns to Step B; otherwise it works like [onCloseRequested]. */
    fun onBackRequested() {
        val state = form.value
        if (state.step == NewBetStep.C) {
            if (!state.isBusy && !state.confirmingLock && state.lockedBetId == null) {
                form.update { it.copy(reviewing = false, errorMessage = null) }
            }
            return
        }
        onCloseRequested()
    }

    /** The top-bar close button, on any step: asks "Discard this bet?" unless Step A is empty. */
    fun onCloseRequested() {
        form.update { state ->
            when {
                state.exited || state.isBusy || state.confirmingLock || state.lockedBetId != null -> state
                state.step == NewBetStep.A && !state.hasEnteredText -> state.copy(exited = true)
                else -> state.copy(showDiscardDialog = true)
            }
        }
    }

    fun onDiscardDismissed() {
        form.update { it.copy(showDiscardDialog = false) }
    }

    fun onDiscardConfirmed() {
        val state = form.value
        if (state.isBusy || state.exited) return
        val betId = state.betId
        if (betId == null) {
            form.update { it.copy(showDiscardDialog = false, exited = true) }
            return
        }
        form.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                betRepository.discardPendingBet(betId)
            } catch (e: BetCoinException) {
                // The bet is already gone or no longer pending, so there's nothing left to discard.
            }
            savedStateHandle.remove<Long>(Routes.NEW_BET_BET_ID_ARG)
            form.update { it.copy(isBusy = false, showDiscardDialog = false, exited = true) }
        }
    }

    private fun BetCoinException.toLockMessage(participants: List<ParticipantWithUser>): String = when (this) {
        is UnanimousPickException ->
            "Everyone picked the same outcome. Go back and change at least one pick."
        is InsufficientBalanceException -> {
            val name = participants.find { it.participant.userId == userId }?.user?.username ?: "A participant"
            balanceChangedMessage(name, required, available)
        }
        is InvalidBetStateException -> "This bet can't be locked any more"
        else -> toUserMessage()
    }

    companion object {
        private fun balanceChangedMessage(name: String, wager: Long, balance: Long): String =
            "$name's balance changed: wagered $wager but now has $balance. Go back and re-add them."

        const val MAX_PROMPT_LENGTH = 120
        const val MIN_OUTCOMES = 2
        const val MAX_OUTCOMES = 8
        const val MAX_OUTCOME_LENGTH = 40
        const val MIN_PARTICIPANTS = 2
    }
}
