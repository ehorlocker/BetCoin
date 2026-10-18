package com.betcoin.ui.newbet

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
import com.betcoin.data.repository.BetRepository
import com.betcoin.data.repository.UserRepository
import com.betcoin.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import java.util.Locale
import javax.inject.Inject

/** Steps of the New Bet flow (§4.2). Step C (Review & Lock) arrives with BET-23. */
enum class NewBetStep { A, B }

/**
 * ViewModel for the New Bet flow, Steps A and B (§4.2, §8).
 *
 * Step A collects the prompt and outcomes and writes the pending bet with `createBet` on Next.
 * Step B adds participants one at a time: each is written with `addParticipant` as soon as their
 * PIN is accepted, so §11 Recovery can restore them. Leaving the flow after the bet exists (or
 * after text was entered in Step A) asks "Discard this bet?" and deletes it with
 * `discardPendingBet`.
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
        // Both steps
        val isBusy: Boolean = false,
        val errorMessage: String? = null,
        val showDiscardDialog: Boolean = false,
        /** True once the flow is finished and the screen should navigate back. */
        val exited: Boolean = false,
    ) {
        val step: NewBetStep get() = if (betId == null) NewBetStep.A else NewBetStep.B

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
                form.update { it.copy(isBusy = false, errorMessage = e.toMessage()) }
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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
                form.update { it.copy(isBusy = false, errorMessage = e.toMessage()) }
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
                form.update { it.copy(errorMessage = e.toMessage()) }
            }
        }
    }

    // --- Leaving the flow ---

    /** System Back or the top-bar close button, on either step. */
    fun onBackRequested() {
        form.update { state ->
            when {
                state.exited || state.isBusy -> state
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

    private fun BetCoinException.toMessage(): String = when (this) {
        is InsufficientBalanceException -> "Not enough BetCoin: needs $required, has $available"
        is InvalidBetStateException -> "This bet can't be changed any more"
        else -> message ?: "Something went wrong"
    }

    companion object {
        const val MAX_PROMPT_LENGTH = 120
        const val MIN_OUTCOMES = 2
        const val MAX_OUTCOMES = 8
        const val MAX_OUTCOME_LENGTH = 40
        const val MIN_PARTICIPANTS = 2
    }
}
