package com.betcoin.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.model.ThemePreference
import com.betcoin.data.repository.SettingsRepository
import com.betcoin.navigation.LAUNCH_LOG_TAG
import com.betcoin.util.ErrorLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/**
 * Activity-scoped holder of the stored theme preference for the app root.
 *
 * [themePreference] is `null` until the first value is read from storage, so the root can
 * render nothing instead of flashing the wrong theme.
 *
 * If reading the stored preference fails, the error is logged and the preference falls back to
 * [ThemePreference.SYSTEM] so the app doesn't crash before the launch error screen can show.
 *
 * Uses [SharingStarted.Eagerly] instead of the usual `WhileSubscribed(5000)` on purpose: the
 * activity root must already hold the value on the first frame after a recreation (rotation),
 * and the ViewModel outlives the activity, so the upstream is started once and kept.
 */
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    errorLogger: ErrorLogger,
) : ViewModel() {

    val themePreference: StateFlow<ThemePreference?> = settingsRepository.getThemePreference()
        .catch { e ->
            errorLogger.logError(LAUNCH_LOG_TAG, "Couldn't read the theme preference", e)
            emit(ThemePreference.SYSTEM)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
