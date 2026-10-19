package com.betcoin.ui.admin

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.ActiveBetConflictException
import com.betcoin.data.model.BetCoinException
import com.betcoin.data.model.InvalidInputException
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

/**
 * UI state of the Admin Panel.
 *
 * BET-30 (players) and BET-31 (bets) extend this with their lists and actions.
 *
 * @property changePin the Change Admin PIN dialog, or `null` when closed
 * @property recoveryCode the Regenerate Recovery Code flow, or `null` when closed
 * @property playerDialog the open Player Management dialog, or `null` when closed
 * @property message one-off snackbar message, or `null`
 */
data class AdminUiState(
    val changePin: ChangePinDialogState? = null,
    val recoveryCode: RecoveryCodeDialogState? = null,
    val playerDialog: PlayerDialogState? = null,
    val message: String? = null,
)

/**
 * ViewModel for the Admin Panel. For now it owns App Settings: changing the admin PIN and
 * regenerating the recovery code. BET-30 and BET-31 extend [AdminUiState] and this class with
 * player and bet management.
 */
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    /** All players, in repository order. */
    val players: StateFlow<List<User>> = userRepository.getAllUsers()
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

    private fun PlayerDialogState.isBusy(): Boolean = when (this) {
        is PlayerDialogState.ConfirmDelete -> isWorking
        is PlayerDialogState.ConfirmResetBalance -> isWorking
        is PlayerDialogState.SetBalance -> isSaving
        is PlayerDialogState.EditUsername -> isSaving
        is PlayerDialogState.ResetPin -> isSaving
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

    private fun sanitize(value: String): String = value.filter { it.isDigit() }.take(PIN_LENGTH)

    companion object {
        const val PIN_LENGTH = 4
        const val ERROR_PIN_LENGTH = "PIN must be exactly 4 digits"
        const val ERROR_PIN_MISMATCH = "PINs don't match"
        const val MAX_USERNAME_LENGTH = 20
        const val MAX_BALANCE_DIGITS = 15
        const val ERROR_BALANCE_INVALID = "Enter a whole number"
        const val ERROR_USERNAME_LENGTH = "Username must be 1–20 characters"
        const val ERROR_USERNAME_TAKEN = "Username already exists"
        const val ERROR_GENERIC = "Something went wrong. Try again."
        const val MESSAGE_PIN_CHANGED = "Admin PIN changed"
        const val MESSAGE_CODE_SAVED = "New recovery code saved"
        const val MESSAGE_REGENERATE_FAILED = "Couldn't create the recovery code. Try again."

        fun messageDeleted(name: String) = "Deleted $name"
        fun messageBailout(name: String) = "$name got a 1,000 BetCoin bailout"
        fun messageBalanceSet(name: String, balance: Long) = "$name's balance set to ${formatCoins(balance)}"
        fun messageRenamed(newName: String) = "Renamed to $newName"
        fun messagePinReset(name: String) = "$name's PIN reset"
        fun messageDeleteBlocked(name: String) = "$name is in an active bet. Resolve or cancel it first."
    }
}
