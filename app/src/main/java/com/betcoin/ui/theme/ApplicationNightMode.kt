package com.betcoin.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import com.betcoin.data.model.ThemePreference

/**
 * Maps the stored [ThemePreference] to a [UiModeManager] night mode. SYSTEM maps to AUTO so the
 * device setting still applies.
 */
fun ThemePreference.toApplicationNightMode(): Int = when (this) {
    ThemePreference.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
    ThemePreference.LIGHT -> UiModeManager.MODE_NIGHT_NO
    ThemePreference.DARK -> UiModeManager.MODE_NIGHT_YES
}

/**
 * Sets the app-level night mode to the stored [preference] on API 31+.
 *
 * Android persists this per app and applies it before the first frame of the next launch, so the
 * window background (`values-night`) and the initial `SystemBarStyle.auto` bars match the stored
 * theme instead of flashing the device mode. On API < 31 the flash remains. With it set,
 * `isSystemInDarkTheme()` reports the app mode, which matches the resolved theme.
 */
fun applyApplicationNightMode(context: Context, preference: ThemePreference) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    context.getSystemService(UiModeManager::class.java)
        .setApplicationNightMode(preference.toApplicationNightMode())
}
