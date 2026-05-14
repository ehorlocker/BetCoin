package com.betcoin.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the onboarding (first-launch) screen.
 *
 * Checks whether the app is being launched for the first time and handles
 * admin PIN setup.
 */
@HiltViewModel
open class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    protected val _isFirstLaunch = MutableStateFlow(false)
    val isFirstLaunch: StateFlow<Boolean> = _isFirstLaunch

    protected val _onboardingComplete = MutableStateFlow(false)
    val onboardingComplete: StateFlow<Boolean> = _onboardingComplete

    protected val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        viewModelScope.launch {
            _isFirstLaunch.value = settingsRepository.isFirstLaunch()
        }
    }

    /**
     * Validates and stores the admin [pin].
     *
     * Emits an error via [errorMessage] if the PIN is invalid.
     * Emits [onboardingComplete] when the PIN is successfully stored.
     */
    open fun setAdminPin(pin: String) {
        when {
            pin.length < 4 -> {
                _errorMessage.value = "PIN must be at least 4 digits"
                return
            }
            !pin.all { it.isDigit() } -> {
                _errorMessage.value = "PIN must be numeric"
                return
            }
        }

        viewModelScope.launch {
            settingsRepository.setAdminPin(pin)
            _onboardingComplete.value = true
        }
    }

    /** Clears any active error message. */
    fun clearError() {
        _errorMessage.value = null
    }
}
