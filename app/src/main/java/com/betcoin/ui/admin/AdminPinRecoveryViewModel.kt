package com.betcoin.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.model.WrongRecoveryCodeException
import com.betcoin.data.repository.RecoveryCode
import com.betcoin.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state of the Reset Admin PIN flow (spec §5.6). */
sealed interface AdminPinRecoveryUiState {

    /**
     * The recovery code is being typed.
     *
     * @property code the text in the field (uppercase, letters, digits and dashes)
     * @property error validation or failure message, or `null`
     */
    data class EnterCode(val code: String = "", val error: String? = null) : AdminPinRecoveryUiState

    /**
     * The new admin PIN is being entered twice.
     *
     * @property isSaving `true` while the reset is in flight
     */
    data class EnterNewPin(
        val pin: String = "",
        val confirmPin: String = "",
        val error: String? = null,
        val isSaving: Boolean = false,
    ) : AdminPinRecoveryUiState

    /**
     * The PIN was reset and the replacement recovery code is shown once.
     *
     * @property recoveryCode the new code, formatted `XXXX-XXXX-XXXX`
     * @property acknowledged whether "I've written this down" is ticked
     */
    data class ShowNewCode(
        val recoveryCode: String,
        val acknowledged: Boolean = false,
    ) : AdminPinRecoveryUiState
}

/**
 * ViewModel for the Reset Admin PIN screen.
 *
 * Three steps: enter the recovery code, choose a new PIN, then see the new recovery code.
 * The repository checks the code when the new PIN is submitted; the code is kept in a private
 * field (not in UI state) between those steps. [recovered] turns `true` once the new code is
 * acknowledged and the user continues.
 */
@HiltViewModel
class AdminPinRecoveryViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AdminPinRecoveryUiState>(AdminPinRecoveryUiState.EnterCode())
    val uiState: StateFlow<AdminPinRecoveryUiState> = _uiState.asStateFlow()

    private val _recovered = MutableStateFlow(false)
    val recovered: StateFlow<Boolean> = _recovered.asStateFlow()

    /** The code accepted in step 1, held only until the reset succeeds or fails. */
    private var heldCode: String? = null

    /** Updates the code field, keeping letters, digits and dashes in uppercase, up to [CODE_MAX_LENGTH]. */
    fun onCodeChanged(value: String) {
        _uiState.update { state ->
            if (state is AdminPinRecoveryUiState.EnterCode) {
                state.copy(
                    code = value.filter { it.isLetterOrDigit() || it == '-' }.uppercase().take(CODE_MAX_LENGTH),
                    error = null,
                )
            } else {
                state
            }
        }
    }

    /** Moves to the PIN step if the typed code has a valid format. */
    fun onSubmitCode() {
        val state = _uiState.value as? AdminPinRecoveryUiState.EnterCode ?: return
        if (RecoveryCode.normalize(state.code) == null) {
            _uiState.value = state.copy(error = ERROR_CODE_FORMAT)
            return
        }
        heldCode = state.code
        _uiState.value = AdminPinRecoveryUiState.EnterNewPin()
    }

    /** Updates the new PIN field, keeping only digits and at most [PIN_LENGTH] of them. */
    fun onPinChanged(value: String) = updateEnterNewPin { it.copy(pin = sanitize(value), error = null) }

    /** Updates the confirmation field, keeping only digits and at most [PIN_LENGTH] of them. */
    fun onConfirmPinChanged(value: String) =
        updateEnterNewPin { it.copy(confirmPin = sanitize(value), error = null) }

    /** Validates the PIN entries, then resets the PIN with the held recovery code. */
    fun onSavePin() {
        val state = _uiState.value as? AdminPinRecoveryUiState.EnterNewPin ?: return
        if (state.isSaving) return
        val code = heldCode ?: return

        val error = when {
            state.pin.length != PIN_LENGTH -> ERROR_PIN_LENGTH
            state.confirmPin != state.pin -> ERROR_PIN_MISMATCH
            else -> null
        }
        if (error != null) {
            _uiState.value = state.copy(error = error)
            return
        }

        _uiState.value = state.copy(error = null, isSaving = true)
        viewModelScope.launch {
            _uiState.value = try {
                val newCode = settingsRepository.resetAdminPinWithRecoveryCode(code, state.pin)
                heldCode = null
                AdminPinRecoveryUiState.ShowNewCode(newCode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: WrongRecoveryCodeException) {
                heldCode = null
                AdminPinRecoveryUiState.EnterCode(code = code, error = ERROR_WRONG_CODE)
            } catch (e: InvalidInputException) {
                AdminPinRecoveryUiState.EnterNewPin(error = e.message ?: ERROR_GENERIC)
            } catch (e: Exception) {
                AdminPinRecoveryUiState.EnterNewPin(error = ERROR_GENERIC)
            }
        }
    }

    /** Ticks or unticks "I've written this down". */
    fun onAcknowledgedChanged(acknowledged: Boolean) {
        _uiState.update { state ->
            if (state is AdminPinRecoveryUiState.ShowNewCode) state.copy(acknowledged = acknowledged) else state
        }
    }

    /** Finishes recovery, but only once the new recovery code has been acknowledged. */
    fun onContinue() {
        val state = _uiState.value
        if (state is AdminPinRecoveryUiState.ShowNewCode && state.acknowledged) {
            _recovered.value = true
        }
    }

    /**
     * Steps back from the PIN step to the code step, keeping the typed code.
     *
     * @return `true` if the step changed; `false` if the screen itself should close
     */
    fun onBack(): Boolean {
        val state = _uiState.value
        if (state is AdminPinRecoveryUiState.EnterNewPin && !state.isSaving) {
            _uiState.value = AdminPinRecoveryUiState.EnterCode(code = heldCode.orEmpty())
            heldCode = null
            return true
        }
        return false
    }

    private fun updateEnterNewPin(
        transform: (AdminPinRecoveryUiState.EnterNewPin) -> AdminPinRecoveryUiState.EnterNewPin,
    ) {
        _uiState.update { state ->
            if (state is AdminPinRecoveryUiState.EnterNewPin && !state.isSaving) transform(state) else state
        }
    }

    private fun sanitize(value: String): String = value.filter { it.isDigit() }.take(PIN_LENGTH)

    companion object {
        const val PIN_LENGTH = 4

        /** 12 characters plus two dashes. */
        const val CODE_MAX_LENGTH = 14
        const val ERROR_CODE_FORMAT = "Enter the 12-character code"
        const val ERROR_WRONG_CODE = "Wrong recovery code"
        const val ERROR_PIN_LENGTH = "PIN must be exactly 4 digits"
        const val ERROR_PIN_MISMATCH = "PINs don't match"
        const val ERROR_GENERIC = "Something went wrong. Try again."
    }
}
