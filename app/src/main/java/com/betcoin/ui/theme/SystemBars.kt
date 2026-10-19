package com.betcoin.ui.theme

import android.graphics.Color
import androidx.activity.SystemBarStyle

/**
 * Scrim used where the system can't draw dark navigation bar icons (API 24-25). There
 * `SystemBarStyle.light` and `SystemBarStyle.auto` fall back to their dark scrim, and a transparent
 * one would leave white icons on a near-white background in light theme. This is androidx
 * activity's own default dark scrim. On API 26-28, `auto()` also uses the scrim for both bars in
 * night mode until the resolved-theme style replaces it (as in androidx's own default).
 */
internal val LegacyDarkScrim: Int = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/**
 * Style for the status and navigation bars: transparent, with icon color following the
 * resolved theme ([darkTheme]) rather than the device mode.
 *
 * On the dev flavor the status bar sits over the banner's errorContainer, which is dark in the
 * dark scheme and light in the light scheme, so the same rule keeps the icons readable
 * (pinned by ColorTest).
 */
fun systemBarStyle(darkTheme: Boolean): SystemBarStyle =
    if (darkTheme) {
        SystemBarStyle.dark(Color.TRANSPARENT)
    } else {
        SystemBarStyle.light(Color.TRANSPARENT, LegacyDarkScrim)
    }
