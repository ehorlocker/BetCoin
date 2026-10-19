package com.betcoin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.betcoin.data.model.ThemePreference

/**
 * Resolves this preference to a concrete dark/light decision.
 * SYSTEM follows the device setting; LIGHT and DARK are explicit.
 */
@Composable
fun ThemePreference.isDarkTheme(): Boolean = when (this) {
    ThemePreference.SYSTEM -> isSystemInDarkTheme()
    ThemePreference.LIGHT -> false
    ThemePreference.DARK -> true
}

/**
 * BetCoin theme composable that applies the dark or light color scheme
 * (chosen by [themePreference]), custom typography, and shapes to all children.
 */
@Composable
fun BetCoinTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (themePreference.isDarkTheme()) BetCoinDarkColorScheme else BetCoinLightColorScheme,
        typography = BetCoinTypography,
        shapes = BetCoinShapes,
        content = content,
    )
}
