package com.betcoin.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.model.ThemePreference
import com.betcoin.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Activity-scoped holder of the stored theme preference for the app root.
 *
 * [themePreference] is `null` until the first value is read from storage, so the root can
 * render nothing instead of flashing the wrong theme.
 *
 * Uses [SharingStarted.Eagerly] instead of the usual `WhileSubscribed(5000)` on purpose: the
 * activity root must already hold the value on the first frame after a recreation (rotation),
 * and the ViewModel outlives the activity, so the upstream is started once and kept.
 */
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val themePreference: StateFlow<ThemePreference?> = settingsRepository.getThemePreference()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
