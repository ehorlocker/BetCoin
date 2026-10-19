package com.betcoin.ui.settings

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.model.BetCoinException
import com.betcoin.data.model.ThemePreference
import com.betcoin.data.repository.SettingsRepository
import com.betcoin.ui.common.GENERIC_ERROR_MESSAGE
import com.betcoin.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * UI state of the Settings screen.
 *
 * @property themePreference the stored theme choice
 * @property errorMessage one-off error to show in a snackbar, or `null`
 */
data class SettingsUiState(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val errorMessage: String? = null,
)

/** ViewModel for the Settings screen: the theme picker. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.getThemePreference(),
        errorMessage,
    ) { theme, error -> SettingsUiState(themePreference = theme, errorMessage = error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Persists [pref]; on failure shows an error and the stored selection is unchanged. */
    fun onThemeSelected(pref: ThemePreference) {
        viewModelScope.launch {
            try {
                settingsRepository.setThemePreference(pref)
            } catch (e: BetCoinException) {
                errorMessage.update { e.toUserMessage() }
            } catch (e: IOException) {
                errorMessage.update { GENERIC_ERROR_MESSAGE }
            } catch (e: SQLiteException) {
                errorMessage.update { GENERIC_ERROR_MESSAGE }
            }
        }
    }

    /** Clears the error once it has been shown. */
    fun onErrorShown() {
        errorMessage.update { null }
    }
}
