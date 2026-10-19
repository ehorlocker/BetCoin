package com.betcoin.ui.theme

import android.graphics.Color
import androidx.activity.SystemBarStyle

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
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    }
