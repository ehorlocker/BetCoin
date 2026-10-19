package com.betcoin.ui.admin

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.ActiveBetConflictException
import com.betcoin.data.model.BetCoinException
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.model.InsufficientBalanceException
import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.model.ParticipantWithUser
import com.betcoin.data.model.UnanimousPickException
import com.betcoin.data.repository.BetRepository
import com.betcoin.data.repository.SettingsRepository
import com.betcoin.data.repository.UserRepository
import com.betcoin.ui.common.GENERIC_ERROR_MESSAGE
import com.betcoin.ui.common.toUserMessage
import com.betcoin.ui.components.formatCoins
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * State of the Change Admin PIN dialog.
 *
 * @property pin the new PIN being typed
 * @property confirmPin the confirmation entry
 * @property error validation or failure message, or `null`
 * @property isSaving `true` while the change is in flight
 */
data class ChangePinDialogState(
    val pin: String = "",
    val confirmPin: String = "",
    val error: String? = null,
    val isSaving: Boolean = false,
)

/** State of the Regenerate Recovery Code flow. */
sealed interface RecoveryCodeDialogState {

    /** Asks the admin to confirm; the current code stops working once regenerated. */
    data object ConfirmRegenerate : RecoveryCodeDialogState

    /** The new code is being created. */
    data object Generating : RecoveryCodeDialogState

    /**
     * The new code is shown once.
     *
     * @property code the new code, formatted `XXXX-XXXX-XXXX`
     * @property acknowledged whether "I've written this down" is ticked
     */
    data class Show(val code: String, val acknowledged: Boolean = false) : RecoveryCodeDialogState
}

/** The Player Management dialog that is open; always about one [user]. */
sealed interface PlayerDialogState {

    /** The player the dialog acts on (may be stale if they changed meanwhile). */
    val user: User

    /**
     * Confirmation before deleting [user].
     *
     * @property isWorking `true` while the deletion is in flight
     */
    data class ConfirmDelete(override val user: User, val isWorking: Boolean = false) : PlayerDialogState

    /**
     * Confirmation before giving [user] a forced bailout.
     *
     * @property isWorking `true` while the bailout is in flight
     */
    data class ConfirmResetBalance(override val user: User, val isWorking: Boolean = false) : PlayerDialogState

    /**
     * Form to set [user]'s balance.
     *
     * @property input the balance being typed (digits with an optional leading `-`)
     * @property error validation or failure message, or `null`
     * @property isSaving `true` while the save is in flight
     */
    data class SetBalance(
        override val user: User,
        val input: String,
        val error: String? = null,
        val isSaving: Boolean = false,
    ) : PlayerDialogState

    /**
     * Form to rename [user].
     *
     * @property input the new username being typed
     * @property error validation or failure message, or `null`
     * @property isSaving `true` while the save is in flight
     */
    data class EditUsername(
        override val user: User,
        val input: String,
        val error: String? = null,
        val isSaving: Boolean = false,
    ) : PlayerDialogState

    /**
     * Form to reset [user]'s PIN.
     *
     * @property pin the new PIN being typed
     * @property confirmPin the confirmation entry
     * @property error validation or failure message, or `null`
     * @property isSaving `true` while the save is in flight
     */
    data class ResetPin(
        override val user: User,
        val pin: String = "",
        val confirmPin: String = "",
        val error: String? = null,
        val isSaving: Boolean = false,
    ) : PlayerDialogState
}

/** `true` while this dialog's action is in flight, so it can't be dismissed or changed. */
internal fun PlayerDialogState.isBusy(): Boolean = when (this) {
    is PlayerDialogState.ConfirmDelete -> isWorking
    is PlayerDialogState.ConfirmResetBalance -> isWorking
    is PlayerDialogState.SetBalance -> isSaving
    is PlayerDialogState.EditUsername -> isSaving
    is PlayerDialogState.ResetPin -> isSaving
}

/** The Bet Management dialog that is open; always about one [bet]. */
sealed interface BetDialogState {

    /** The bet the dialog acts on (may be stale if it changed meanwhile). */
    val bet: BetWithDetails

    /**
     * Confirmation before force-cancelling [bet].
     *
     * @property isWorking `true` while the cancellation is in flight
     */
    data class ConfirmForceCancel(override val bet: BetWithDetails, val isWorking: Boolean = false) : BetDialogState

    /**
     * Confirmation before deleting [bet].
     *
     * @property isWorking `true` while the deletion is in flight
     */
    data class ConfirmDelete(override val bet: BetWithDetails, val isWorking: Boolean = false) : BetDialogState

    /**
     * Form to resolve [bet] by picking the winning outcome.
     *
     * @property selectedOutcomeId the chosen winning outcome, or `null`
     * @property error validation or failure message, or `null`
     * @property isSaving `true` while the resolution is in flight
     */
    data class Resolve(
        override val bet: BetWithDetails,
        val selectedOutcomeId: Long? = null,
        val error: String? = null,
        val isSaving: Boolean = false,
    ) : BetDialogState

    /**
     * Form to edit the prompt of [bet].
     *
     * @property input the prompt being typed
     * @property error validation or failure message, or `null`
     * @property isSaving `true` while the save is in flight
     */
    data class EditPrompt(
        override val bet: BetWithDetails,
        val input: String,
        val error: String? = null,
        val isSaving: Boolean = false,
    ) : BetDialogState

    /**
     * Form to remove a participant from [bet].
     *
     * @property selectedParticipantId id of the chosen [com.betcoin.data.database.entity.BetParticipant], or `null`
     * @property error validation or failure message, or `null`
     * @property isSaving `true` while the removal is in flight
     */
    data class RemoveParticipant(
        override val bet: BetWithDetails,
        val selectedParticipantId: Long? = null,
        val error: String? = null,
        val isSaving: Boolean = false,
    ) : BetDialogState

    /**
     * Form to change a participant's wager in [bet].
     *
     * @property selectedParticipantId id of the chosen [com.betcoin.data.database.entity.BetParticipant], or `null`
     * @property input the new wager being typed (digits only)
     * @property error validation or failure message, or `null`
     * @property isSaving `true` while the save is in flight
     */
    data class EditWager(
        override val bet: BetWithDetails,
        val selectedParticipantId: Long? = null,
        val input: String = "",
        val error: String? = null,
        val isSaving: Boolean = false,
    ) : BetDialogState
}

/** `true` while this dialog's action is in flight, so it can't be dismissed or changed. */
internal fun BetDialogState.isBusy(): Boolean = when (this) {
    is BetDialogState.ConfirmForceCancel -> isWorking
    is BetDialogState.ConfirmDelete -> isWorking
    is BetDialogState.Resolve -> isSaving
    is BetDialogState.EditPrompt -> isSaving
    is BetDialogState.RemoveParticipant -> isSaving
    is BetDialogState.EditWager -> isSaving
}

/**
 * UI state of the Admin Panel.
 *
 * @property changePin the Change Admin PIN dialog, or `null` when closed
 * @property recoveryCode the Regenerate Recovery Code flow, or `null` when closed
 * @property playerDialog the open Player Management dialog, or `null` when closed
 * @property betDialog the open Bet Management dialog, or `null` when closed
 * @property message one-off snackbar message, or `null`
 */
data class AdminUiState(
    val changePin: ChangePinDialogState? = null,
    val recoveryCode: RecoveryCodeDialogState? = null,
    val playerDialog: PlayerDialogState? = null,
    val betDialog: BetDialogState? = null,
    val message: String? = null,
)

/**
 * ViewModel for the Admin Panel. It owns App Settings (changing the admin PIN and regenerating
 * the recovery code), Player Management and Bet Management.
 */
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val userRepository: UserRepository,
    private val betRepository: BetRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    /** All players, in repository order. */
    val players: StateFlow<List<User>> = userRepository.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All non-pending bets, newest first (repository order). */
    val bets: StateFlow<List<BetWithDetails>> = betRepository.getAllBets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Opens the Change Admin PIN dialog with empty fields. */
    fun onChangePinClicked() {
        _uiState.update { it.copy(changePin = ChangePinDialogState()) }
    }

    /** Updates the new PIN field, keeping only digits and at most [PIN_LENGTH] of them. */
    fun onChangePinPinChanged(value: String) =
        updateChangePin { it.copy(pin = sanitize(value), error = null) }

    /** Updates the confirmation field, keeping only digits and at most [PIN_LENGTH] of them. */
    fun onChangePinConfirmChanged(value: String) =
        updateChangePin { it.copy(confirmPin = sanitize(value), error = null) }

    /** Validates the entries, then saves the new PIN. Ignored while a save is in flight. */
    fun onChangePinSave() {
        val dialog = _uiState.value.changePin ?: return
        if (dialog.isSaving) return

        val error = when {
            dialog.pin.length != PIN_LENGTH -> ERROR_PIN_LENGTH
            dialog.confirmPin != dialog.pin -> ERROR_PIN_MISMATCH
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(changePin = dialog.copy(error = error)) }
            return
        }

        _uiState.update { it.copy(changePin = dialog.copy(error = null, isSaving = true)) }
        viewModelScope.launch {
            try {
                settingsRepository.setAdminPin(dialog.pin)
                _uiState.update { it.copy(changePin = null, message = MESSAGE_PIN_CHANGED) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidInputException) {
                failChangePin(e.message ?: ERROR_GENERIC)
            } catch (e: Exception) {
                failChangePin(ERROR_GENERIC)
            }
        }
    }

    /** Closes the Change Admin PIN dialog. Ignored while saving. */
    fun onChangePinDismiss() {
        _uiState.update { state ->
            if (state.changePin?.isSaving == true) state else state.copy(changePin = null)
        }
    }

    /** Opens the confirmation before regenerating the recovery code. */
    fun onRegenerateClicked() {
        _uiState.update { it.copy(recoveryCode = RecoveryCodeDialogState.ConfirmRegenerate) }
    }

    /** Regenerates the recovery code and shows it once. Only valid from the confirmation. */
    fun onRegenerateConfirm() {
        if (_uiState.value.recoveryCode != RecoveryCodeDialogState.ConfirmRegenerate) return
        _uiState.update { it.copy(recoveryCode = RecoveryCodeDialogState.Generating) }
        viewModelScope.launch {
            try {
                val code = settingsRepository.regenerateRecoveryCode()
                _uiState.update { it.copy(recoveryCode = RecoveryCodeDialogState.Show(code)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(recoveryCode = null, message = MESSAGE_REGENERATE_FAILED) }
            }
        }
    }

    /** Dismisses the confirmation. Does nothing in any other state (e.g. while generating). */
    fun onRegenerateCancel() {
        _uiState.update { state ->
            if (state.recoveryCode == RecoveryCodeDialogState.ConfirmRegenerate) {
                state.copy(recoveryCode = null)
            } else {
                state
            }
        }
    }

    /** Ticks or unticks "I've written this down". */
    fun onRecoveryCodeAcknowledged(acknowledged: Boolean) {
        _uiState.update { state ->
            val shown = state.recoveryCode as? RecoveryCodeDialogState.Show ?: return@update state
            state.copy(recoveryCode = shown.copy(acknowledged = acknowledged))
        }
    }

    /** Closes the reveal, but only once the new code has been acknowledged. */
    fun onRecoveryCodeDone() {
        _uiState.update { state ->
            val shown = state.recoveryCode as? RecoveryCodeDialogState.Show ?: return@update state
            if (shown.acknowledged) state.copy(recoveryCode = null, message = MESSAGE_CODE_SAVED) else state
        }
    }

    /** Clears the snackbar message after it was shown. */
    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    /** Shows [error] in the dialog and clears the PIN fields. */
    private fun failChangePin(error: String) {
        _uiState.update { state ->
            if (state.changePin == null) state else state.copy(changePin = ChangePinDialogState(error = error))
        }
    }

    private fun updateChangePin(transform: (ChangePinDialogState) -> ChangePinDialogState) {
        _uiState.update { state ->
            val dialog = state.changePin
            if (dialog != null && !dialog.isSaving) state.copy(changePin = transform(dialog)) else state
        }
    }

    // region Player Management

    /** Opens the confirmation before deleting [user]. */
    fun onDeletePlayerClicked(user: User) = openPlayerDialog(PlayerDialogState.ConfirmDelete(user))

    /** Opens the confirmation before giving [user] a forced bailout. */
    fun onResetBalanceClicked(user: User) = openPlayerDialog(PlayerDialogState.ConfirmResetBalance(user))

    /** Opens the Set Balance form, prefilled with [user]'s current balance. */
    fun onSetBalanceClicked(user: User) =
        openPlayerDialog(PlayerDialogState.SetBalance(user, input = user.balance.toString()))

    /** Opens the Edit Username form, prefilled with [user]'s current name. */
    fun onEditUsernameClicked(user: User) =
        openPlayerDialog(PlayerDialogState.EditUsername(user, input = user.username))

    /** Opens the Reset PIN form with empty fields. */
    fun onResetPinClicked(user: User) = openPlayerDialog(PlayerDialogState.ResetPin(user))

    /**
     * Updates the main field of the open form: the balance (digits, optional leading `-`), the
     * username, or the new PIN (digits only). Ignored for confirmations and while saving.
     */
    fun onPlayerDialogInputChanged(value: String) = updatePlayerDialog { dialog ->
        when (dialog) {
            is PlayerDialogState.SetBalance -> dialog.copy(input = sanitizeBalance(value), error = null)
            is PlayerDialogState.EditUsername -> dialog.copy(input = value, error = null)
            is PlayerDialogState.ResetPin -> dialog.copy(pin = sanitize(value), error = null)
            else -> dialog
        }
    }

    /** Flips the leading `-` of the Set Balance input. Ignored for other dialogs and while saving. */
    fun onSetBalanceSignToggled() = updatePlayerDialog { dialog ->
        if (dialog is PlayerDialogState.SetBalance) {
            val input = if (dialog.input.startsWith("-")) dialog.input.removePrefix("-") else "-${dialog.input}"
            dialog.copy(input = sanitizeBalance(input), error = null)
        } else {
            dialog
        }
    }

    /** Updates the PIN confirmation of the Reset PIN form. Ignored while saving. */
    fun onPlayerDialogConfirmPinChanged(value: String) = updatePlayerDialog { dialog ->
        if (dialog is PlayerDialogState.ResetPin) dialog.copy(confirmPin = sanitize(value), error = null) else dialog
    }

    /** Validates the open dialog and runs its action. Ignored while one is already in flight. */
    fun onPlayerDialogConfirm() {
        val dialog = _uiState.value.playerDialog ?: return
        if (dialog.isBusy()) return

        val action: suspend () -> String = when (dialog) {
            is PlayerDialogState.ConfirmDelete -> {
                { userRepository.deleteUser(dialog.user.id); messageDeleted(dialog.user.username) }
            }
            is PlayerDialogState.ConfirmResetBalance -> {
                { userRepository.forceBailout(dialog.user.id); messageBailout(dialog.user.username) }
            }
            is PlayerDialogState.SetBalance -> {
                val balance = dialog.input.toLongOrNull()
                if (balance == null) {
                    setPlayerDialogError(ERROR_BALANCE_INVALID)
                    return
                }
                { userRepository.setBalance(dialog.user.id, balance); messageBalanceSet(dialog.user.username, balance) }
            }
            is PlayerDialogState.EditUsername -> {
                val name = dialog.input.trim()
                val error = usernameError(dialog.user, name)
                if (error != null) {
                    setPlayerDialogError(error)
                    return
                }
                if (name == dialog.user.username) {
                    _uiState.update { it.copy(playerDialog = null) }
                    return
                }
                { userRepository.updateUsername(dialog.user.id, name); messageRenamed(name) }
            }
            is PlayerDialogState.ResetPin -> {
                val error = when {
                    dialog.pin.length != PIN_LENGTH -> ERROR_PIN_LENGTH
                    dialog.confirmPin != dialog.pin -> ERROR_PIN_MISMATCH
                    else -> null
                }
                if (error != null) {
                    setPlayerDialogError(error)
                    return
                }
                { userRepository.resetPin(dialog.user.id, dialog.pin); messagePinReset(dialog.user.username) }
            }
        }

        _uiState.update { it.copy(playerDialog = dialog.asBusy()) }
        viewModelScope.launch {
            try {
                val message = action()
                _uiState.update { it.copy(playerDialog = null, message = message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BetCoinException) {
                failPlayerDialog(dialog, playerErrorMessage(dialog, e))
            } catch (e: IOException) {
                failPlayerDialog(dialog, GENERIC_ERROR_MESSAGE)
            } catch (e: SQLiteException) {
                failPlayerDialog(dialog, GENERIC_ERROR_MESSAGE)
            }
        }
    }

    /** Closes the open player dialog. Ignored while its action is in flight. */
    fun onPlayerDialogDismiss() {
        _uiState.update { state ->
            if (state.playerDialog?.isBusy() == true) state else state.copy(playerDialog = null)
        }
    }

    private fun openPlayerDialog(dialog: PlayerDialogState) {
        _uiState.update { state ->
            if (state.playerDialog?.isBusy() == true) state else state.copy(playerDialog = dialog)
        }
    }

    private fun updatePlayerDialog(transform: (PlayerDialogState) -> PlayerDialogState) {
        _uiState.update { state ->
            val dialog = state.playerDialog
            if (dialog != null && !dialog.isBusy()) state.copy(playerDialog = transform(dialog)) else state
        }
    }

    private fun setPlayerDialogError(error: String) = updatePlayerDialog { dialog ->
        when (dialog) {
            is PlayerDialogState.SetBalance -> dialog.copy(error = error)
            is PlayerDialogState.EditUsername -> dialog.copy(error = error)
            is PlayerDialogState.ResetPin -> dialog.copy(error = error)
            else -> dialog
        }
    }

    /** Confirmations close and report in the snackbar; forms stay open and show [error] inline. */
    private fun failPlayerDialog(dialog: PlayerDialogState, error: String) {
        _uiState.update { state ->
            when (dialog) {
                is PlayerDialogState.ConfirmDelete,
                is PlayerDialogState.ConfirmResetBalance -> state.copy(playerDialog = null, message = error)
                is PlayerDialogState.SetBalance ->
                    state.copy(playerDialog = dialog.copy(error = error, isSaving = false))
                is PlayerDialogState.EditUsername ->
                    state.copy(playerDialog = dialog.copy(error = error, isSaving = false))
                is PlayerDialogState.ResetPin ->
                    state.copy(playerDialog = dialog.copy(error = error, isSaving = false))
            }
        }
    }

    private fun playerErrorMessage(dialog: PlayerDialogState, e: BetCoinException): String =
        if (dialog is PlayerDialogState.ConfirmDelete && e is ActiveBetConflictException) {
            messageDeleteBlocked(dialog.user.username)
        } else {
            e.toUserMessage()
        }

    /** Length and case-insensitive uniqueness check of [name] (already trimmed) for [user]. */
    private fun usernameError(user: User, name: String): String? = when {
        name.length !in 1..MAX_USERNAME_LENGTH -> ERROR_USERNAME_LENGTH
        players.value.any { it.id != user.id && it.username.equals(name, ignoreCase = true) } ->
            ERROR_USERNAME_TAKEN
        else -> null
    }

    private fun PlayerDialogState.asBusy(): PlayerDialogState = when (this) {
        is PlayerDialogState.ConfirmDelete -> copy(isWorking = true)
        is PlayerDialogState.ConfirmResetBalance -> copy(isWorking = true)
        is PlayerDialogState.SetBalance -> copy(error = null, isSaving = true)
        is PlayerDialogState.EditUsername -> copy(error = null, isSaving = true)
        is PlayerDialogState.ResetPin -> copy(error = null, isSaving = true)
    }

    /** Keeps digits and a single leading `-`, at most [MAX_BALANCE_DIGITS] digits. */
    private fun sanitizeBalance(value: String): String {
        val digits = value.filter { it.isDigit() }.take(MAX_BALANCE_DIGITS)
        return if (value.startsWith("-")) "-$digits" else digits
    }

    // endregion

    // region Bet Management

    /** Opens the confirmation before force-cancelling [bet]. Only for ACTIVE bets. */
    fun onForceCancelClicked(bet: BetWithDetails) {
        if (bet.bet.status == BetStatus.ACTIVE) openBetDialog(BetDialogState.ConfirmForceCancel(bet))
    }

    /** Opens the Admin Resolve form for [bet]. Only for ACTIVE bets. */
    fun onAdminResolveClicked(bet: BetWithDetails) {
        if (bet.bet.status == BetStatus.ACTIVE) openBetDialog(BetDialogState.Resolve(bet))
    }

    /** Opens the Edit Prompt form, prefilled with the current prompt. Allowed for any status. */
    fun onEditPromptClicked(bet: BetWithDetails) =
        openBetDialog(BetDialogState.EditPrompt(bet, input = bet.bet.prompt))

    /** Opens the Remove Participant form for [bet]. Only for ACTIVE bets. */
    fun onRemoveParticipantClicked(bet: BetWithDetails) {
        if (bet.bet.status == BetStatus.ACTIVE) openBetDialog(BetDialogState.RemoveParticipant(bet))
    }

    /** Opens the Edit Wager form for [bet]. Only for ACTIVE bets. */
    fun onEditWagerClicked(bet: BetWithDetails) {
        if (bet.bet.status == BetStatus.ACTIVE) openBetDialog(BetDialogState.EditWager(bet))
    }

    /** Opens the confirmation before deleting [bet]. Only for RESOLVED or CANCELLED bets. */
    fun onDeleteBetClicked(bet: BetWithDetails) {
        if (bet.bet.status == BetStatus.RESOLVED || bet.bet.status == BetStatus.CANCELLED) {
            openBetDialog(BetDialogState.ConfirmDelete(bet))
        }
    }

    /** Picks the winning outcome in the Resolve form. Ignored for other dialogs and while saving. */
    fun onBetDialogOutcomeSelected(outcomeId: Long) = updateBetDialog { dialog ->
        if (dialog is BetDialogState.Resolve) dialog.copy(selectedOutcomeId = outcomeId, error = null) else dialog
    }

    /**
     * Picks the participant (by [com.betcoin.data.database.entity.BetParticipant] id) in the Remove
     * Participant or Edit Wager form; Edit Wager prefills the input with their current wager.
     */
    fun onBetDialogParticipantSelected(participantId: Long) = updateBetDialog { dialog ->
        when (dialog) {
            is BetDialogState.RemoveParticipant ->
                dialog.copy(selectedParticipantId = participantId, error = null)
            is BetDialogState.EditWager -> {
                val wager = dialog.bet.participants.firstOrNull { it.participant.id == participantId }
                    ?.participant?.wagerAmount
                dialog.copy(
                    selectedParticipantId = participantId,
                    input = wager?.toString() ?: dialog.input,
                    error = null,
                )
            }
            else -> dialog
        }
    }

    /**
     * Updates the text field of the open form: the prompt as typed, or the wager (digits only, at
     * most [MAX_BALANCE_DIGITS]). Ignored for other dialogs and while saving.
     */
    fun onBetDialogInputChanged(value: String) = updateBetDialog { dialog ->
        when (dialog) {
            is BetDialogState.EditPrompt -> dialog.copy(input = value, error = null)
            is BetDialogState.EditWager ->
                dialog.copy(input = value.filter { it.isDigit() }.take(MAX_BALANCE_DIGITS), error = null)
            else -> dialog
        }
    }

    /** Validates the open bet dialog and runs its action. Ignored while one is already in flight. */
    fun onBetDialogConfirm() {
        val dialog = _uiState.value.betDialog ?: return
        if (dialog.isBusy()) return

        val action: suspend () -> String = when (dialog) {
            is BetDialogState.ConfirmForceCancel -> {
                { betRepository.forceCancelBet(dialog.bet.bet.id); MESSAGE_BET_CANCELLED }
            }
            is BetDialogState.ConfirmDelete -> {
                { betRepository.deleteBet(dialog.bet.bet.id); MESSAGE_BET_DELETED }
            }
            is BetDialogState.Resolve -> {
                val outcome = dialog.bet.outcomes.firstOrNull { it.id == dialog.selectedOutcomeId }
                if (outcome == null) {
                    setBetDialogError(ERROR_PICK_OUTCOME)
                    return
                }
                { betRepository.adminResolveBet(dialog.bet.bet.id, outcome.id); messageBetResolved(outcome.outcomeText) }
            }
            is BetDialogState.EditPrompt -> {
                val prompt = dialog.input.trim()
                if (prompt.length !in 1..MAX_PROMPT_LENGTH) {
                    setBetDialogError(ERROR_PROMPT_LENGTH)
                    return
                }
                if (prompt == dialog.bet.bet.prompt) {
                    _uiState.update { it.copy(betDialog = null) }
                    return
                }
                { betRepository.updateBetPrompt(dialog.bet.bet.id, prompt); MESSAGE_PROMPT_UPDATED }
            }
            is BetDialogState.RemoveParticipant -> {
                val target = dialog.bet.participants.firstOrNull { it.participant.id == dialog.selectedParticipantId }
                if (target == null) {
                    setBetDialogError(ERROR_PICK_PARTICIPANT)
                    return
                }
                val blocked = removeBlockReason(dialog.bet, target)
                if (blocked != null) {
                    setBetDialogError(blocked)
                    return
                }
                {
                    betRepository.adminRemoveParticipant(dialog.bet.bet.id, target.participant.id)
                    messageParticipantRemoved(participantName(target))
                }
            }
            is BetDialogState.EditWager -> {
                val target = dialog.bet.participants.firstOrNull { it.participant.id == dialog.selectedParticipantId }
                if (target == null) {
                    setBetDialogError(ERROR_PICK_PARTICIPANT)
                    return
                }
                val wager = dialog.input.toLongOrNull()?.takeIf { it >= 1 }
                if (wager == null) {
                    setBetDialogError(ERROR_WAGER_INVALID)
                    return
                }
                val current = target.participant.wagerAmount
                if (wager == current) {
                    _uiState.update { it.copy(betDialog = null) }
                    return
                }
                val extra = wager - current
                if (extra > 0) {
                    val userId = target.participant.userId
                    val balance = players.value.firstOrNull { it.id == userId }?.balance
                        ?: target.user?.balance
                        ?: 0L
                    if (extra > balance) {
                        setBetDialogError(messageWagerTooHigh(participantName(target), extra, balance))
                        return
                    }
                }
                { betRepository.adminEditWager(target.participant.id, wager); messageWagerUpdated(participantName(target), wager) }
            }
        }

        _uiState.update { it.copy(betDialog = dialog.asBusy()) }
        viewModelScope.launch {
            try {
                val message = action()
                _uiState.update { it.copy(betDialog = null, message = message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BetCoinException) {
                failBetDialog(dialog, betErrorMessage(dialog, e))
            } catch (e: IOException) {
                failBetDialog(dialog, GENERIC_ERROR_MESSAGE)
            } catch (e: SQLiteException) {
                failBetDialog(dialog, GENERIC_ERROR_MESSAGE)
            }
        }
    }

    /** Closes the open bet dialog. Ignored while its action is in flight. */
    fun onBetDialogDismiss() {
        _uiState.update { state ->
            if (state.betDialog?.isBusy() == true) state else state.copy(betDialog = null)
        }
    }

    private fun openBetDialog(dialog: BetDialogState) {
        _uiState.update { state ->
            if (state.betDialog?.isBusy() == true || state.playerDialog?.isBusy() == true) {
                state
            } else {
                state.copy(betDialog = dialog)
            }
        }
    }

    private fun updateBetDialog(transform: (BetDialogState) -> BetDialogState) {
        _uiState.update { state ->
            val dialog = state.betDialog
            if (dialog != null && !dialog.isBusy()) state.copy(betDialog = transform(dialog)) else state
        }
    }

    private fun setBetDialogError(error: String) = updateBetDialog { dialog ->
        when (dialog) {
            is BetDialogState.Resolve -> dialog.copy(error = error)
            is BetDialogState.EditPrompt -> dialog.copy(error = error)
            is BetDialogState.RemoveParticipant -> dialog.copy(error = error)
            is BetDialogState.EditWager -> dialog.copy(error = error)
            else -> dialog
        }
    }

    /** Confirmations close and report in the snackbar; forms stay open and show [error] inline. */
    private fun failBetDialog(dialog: BetDialogState, error: String) {
        _uiState.update { state ->
            when (dialog) {
                is BetDialogState.ConfirmForceCancel,
                is BetDialogState.ConfirmDelete -> state.copy(betDialog = null, message = error)
                is BetDialogState.Resolve -> state.copy(betDialog = dialog.copy(error = error, isSaving = false))
                is BetDialogState.EditPrompt -> state.copy(betDialog = dialog.copy(error = error, isSaving = false))
                is BetDialogState.RemoveParticipant ->
                    state.copy(betDialog = dialog.copy(error = error, isSaving = false))
                is BetDialogState.EditWager -> state.copy(betDialog = dialog.copy(error = error, isSaving = false))
            }
        }
    }

    private fun betErrorMessage(dialog: BetDialogState, e: BetCoinException): String = when {
        dialog is BetDialogState.RemoveParticipant && e is UnanimousPickException -> ERROR_REMOVE_UNANIMOUS
        dialog is BetDialogState.RemoveParticipant && e is InvalidInputException -> {
            val target = dialog.bet.participants.firstOrNull { it.participant.id == dialog.selectedParticipantId }
            if (target != null && remainingAfterRemoving(dialog.bet, target).size < MIN_PARTICIPANTS) {
                ERROR_REMOVE_TOO_FEW
            } else {
                e.toUserMessage()
            }
        }
        dialog is BetDialogState.EditWager && e is InsufficientBalanceException -> {
            val target = dialog.bet.participants.firstOrNull { it.participant.id == dialog.selectedParticipantId }
            messageWagerTooHigh(target?.let(::participantName) ?: DELETED_PLAYER_NAME, e.required, e.available)
        }
        else -> e.toUserMessage()
    }

    private fun remainingAfterRemoving(bet: BetWithDetails, target: ParticipantWithUser) =
        bet.participants.filter { it.participant.id != target.participant.id }

    /** Why removing [target] from [bet] is not allowed, or `null` when it is. */
    private fun removeBlockReason(bet: BetWithDetails, target: ParticipantWithUser): String? {
        val remaining = remainingAfterRemoving(bet, target)
        return when {
            remaining.size < MIN_PARTICIPANTS -> ERROR_REMOVE_TOO_FEW
            remaining.map { it.participant.outcomeId }.distinct().size < 2 -> ERROR_REMOVE_UNANIMOUS
            else -> null
        }
    }

    private fun participantName(p: ParticipantWithUser): String =
        p.user?.username ?: p.participant.deletedUsername ?: DELETED_PLAYER_NAME

    private fun BetDialogState.asBusy(): BetDialogState = when (this) {
        is BetDialogState.ConfirmForceCancel -> copy(isWorking = true)
        is BetDialogState.ConfirmDelete -> copy(isWorking = true)
        is BetDialogState.Resolve -> copy(error = null, isSaving = true)
        is BetDialogState.EditPrompt -> copy(error = null, isSaving = true)
        is BetDialogState.RemoveParticipant -> copy(error = null, isSaving = true)
        is BetDialogState.EditWager -> copy(error = null, isSaving = true)
    }

    // endregion

    private fun sanitize(value: String): String = value.filter { it.isDigit() }.take(PIN_LENGTH)

    companion object {
        const val PIN_LENGTH = 4
        const val ERROR_PIN_LENGTH = "PIN must be exactly 4 digits"
        const val ERROR_PIN_MISMATCH = "PINs don't match"
        const val MAX_USERNAME_LENGTH = 20
        const val MAX_BALANCE_DIGITS = 15
        const val MAX_PROMPT_LENGTH = 120
        private const val MIN_PARTICIPANTS = 2
        private const val DELETED_PLAYER_NAME = "Deleted player"
        private const val BAILOUT_AMOUNT = 1_000L
        const val ERROR_BALANCE_INVALID = "Enter a whole number"
        const val ERROR_USERNAME_LENGTH = "Username must be 1–20 characters"
        const val ERROR_USERNAME_TAKEN = "Username already exists"
        const val ERROR_GENERIC = "Something went wrong. Try again."
        const val ERROR_PROMPT_LENGTH = "Prompt must be 1–120 characters"
        const val ERROR_WAGER_INVALID = "Wager must be a whole number of at least 1"
        const val ERROR_PICK_OUTCOME = "Pick the winning outcome"
        const val ERROR_PICK_PARTICIPANT = "Pick a participant"
        const val ERROR_REMOVE_TOO_FEW = "A bet needs at least 2 players. Force cancel it instead."
        const val ERROR_REMOVE_UNANIMOUS = "Everyone left would be on the same outcome. Force cancel it instead."
        const val MESSAGE_BET_CANCELLED = "Bet cancelled. All wagers refunded."
        const val MESSAGE_PROMPT_UPDATED = "Prompt updated"
        const val MESSAGE_BET_DELETED = "Bet deleted"
        const val MESSAGE_PIN_CHANGED = "Admin PIN changed"
        const val MESSAGE_CODE_SAVED = "New recovery code saved"
        const val MESSAGE_REGENERATE_FAILED = "Couldn't create the recovery code. Try again."

        fun messageWagerTooHigh(name: String, extra: Long, balance: Long) =
            "$name can't cover ${formatCoins(extra)} more BetCoin (has ${formatCoins(balance)})"
        fun messageBetResolved(outcome: String) = "Resolved: $outcome wins"
        fun messageParticipantRemoved(name: String) = "Removed $name and refunded their wager"
        fun messageWagerUpdated(name: String, wager: Long) = "$name's wager set to ${formatCoins(wager)}"
        fun messageDeleted(name: String) = "Deleted $name"
        fun messageBailout(name: String) = "$name got a ${formatCoins(BAILOUT_AMOUNT)} BetCoin bailout"
        fun messageBalanceSet(name: String, balance: Long) = "$name's balance set to ${formatCoins(balance)}"
        fun messageRenamed(newName: String) = "Renamed to $newName"
        fun messagePinReset(name: String) = "$name's PIN reset"
        fun messageDeleteBlocked(name: String) = "$name is in an active bet. Resolve or cancel it first."
    }
}
