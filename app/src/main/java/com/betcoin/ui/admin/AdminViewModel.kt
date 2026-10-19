package com.betcoin.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

/**
 * UI state of the Admin Panel.
 *
 * BET-30 (players) and BET-31 (bets) extend this with their lists and actions.
 *
 * @property changePin the Change Admin PIN dialog, or `null` when closed
 * @property recoveryCode the Regenerate Recovery Code flow, or `null` when closed
 * @property message one-off snackbar message, or `null`
 */
data class AdminUiState(
    val changePin: ChangePinDialogState? = null,
    val recoveryCode: RecoveryCodeDialogState? = null,
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

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

    private fun sanitize(value: String): String = value.filter { it.isDigit() }.take(PIN_LENGTH)

    companion object {
        const val PIN_LENGTH = 4
        const val ERROR_PIN_LENGTH = "PIN must be exactly 4 digits"
        const val ERROR_PIN_MISMATCH = "PINs don't match"
        const val ERROR_GENERIC = "Something went wrong. Try again."
        const val MESSAGE_PIN_CHANGED = "Admin PIN changed"
        const val MESSAGE_CODE_SAVED = "New recovery code saved"
        const val MESSAGE_REGENERATE_FAILED = "Couldn't create the recovery code. Try again."
    }
}
