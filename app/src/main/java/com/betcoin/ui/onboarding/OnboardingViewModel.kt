package com.betcoin.ui.onboarding

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * UI state for the onboarding (first-launch) flow (spec §12).
 */
sealed interface OnboardingUiState {

    /**
     * The admin PIN is being entered twice.
     *
     * @property pin digits typed in the first field (at most 4)
     * @property confirmPin digits typed in the confirmation field (at most 4)
     * @property error validation or save error, or `null`
     * @property isSaving `true` while the PIN is hashed and stored
     */
    data class EnterPin(
        val pin: String = "",
        val confirmPin: String = "",
        val error: String? = null,
        val isSaving: Boolean = false,
    ) : OnboardingUiState

    /**
     * The PIN is saved and the recovery code is shown once (spec §5.6).
     *
     * @property recoveryCode the plain code, formatted `XXXX-XXXX-XXXX`
     * @property acknowledged whether "I've written this down" is ticked
     */
    data class ShowRecoveryCode(
        val recoveryCode: String,
        val acknowledged: Boolean = false,
    ) : OnboardingUiState
}

/**
 * ViewModel for the onboarding (first-launch) screen.
 *
 * Takes the master admin PIN twice, stores it, then generates the recovery code and shows
 * it once. [onboardingComplete] turns `true` when the user continues after ticking
 * "I've written this down".
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.EnterPin())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _onboardingComplete = MutableStateFlow(false)
    val onboardingComplete: StateFlow<Boolean> = _onboardingComplete.asStateFlow()

    /** Updates the first PIN field, keeping only digits and at most [PIN_LENGTH] of them. */
    fun onPinChanged(value: String) = updateEnterPin { it.copy(pin = sanitize(value), error = null) }

    /** Updates the confirmation PIN field, keeping only digits and at most [PIN_LENGTH] of them. */
    fun onConfirmPinChanged(value: String) =
        updateEnterPin { it.copy(confirmPin = sanitize(value), error = null) }

    /**
     * Validates the two PIN entries and, if they're 4 digits and match, stores the PIN and
     * generates the recovery code.
     */
    fun onSavePin() {
        val state = _uiState.value as? OnboardingUiState.EnterPin ?: return
        if (state.isSaving) return

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
            // Retrying after a code failure sets the same PIN again, which is harmless.
            var failureMessage = ERROR_SAVE_FAILED
            _uiState.value = try {
                settingsRepository.setAdminPin(state.pin)
                failureMessage = ERROR_CODE_FAILED
                OnboardingUiState.ShowRecoveryCode(settingsRepository.regenerateRecoveryCode())
            } catch (e: IOException) {
                state.copy(error = failureMessage, isSaving = false)
            } catch (e: SQLiteException) {
                state.copy(error = failureMessage, isSaving = false)
            }
        }
    }

    /** Ticks or unticks "I've written this down". */
    fun onAcknowledgedChanged(acknowledged: Boolean) {
        _uiState.update { state ->
            if (state is OnboardingUiState.ShowRecoveryCode) state.copy(acknowledged = acknowledged) else state
        }
    }

    /** Finishes onboarding, but only once the recovery code has been acknowledged. */
    fun onContinue() {
        val state = _uiState.value
        if (state is OnboardingUiState.ShowRecoveryCode && state.acknowledged) {
            _onboardingComplete.value = true
        }
    }

    private fun updateEnterPin(transform: (OnboardingUiState.EnterPin) -> OnboardingUiState.EnterPin) {
        _uiState.update { state ->
            if (state is OnboardingUiState.EnterPin && !state.isSaving) transform(state) else state
        }
    }

    private fun sanitize(value: String): String = value.filter { it.isDigit() }.take(PIN_LENGTH)

    companion object {
        const val PIN_LENGTH = 4
        const val ERROR_PIN_LENGTH = "PIN must be exactly 4 digits"
        const val ERROR_PIN_MISMATCH = "PINs don't match"
        const val ERROR_SAVE_FAILED = "Couldn't save the PIN. Try again."
        const val ERROR_CODE_FAILED = "Couldn't create the recovery code. Try again."
    }
}
