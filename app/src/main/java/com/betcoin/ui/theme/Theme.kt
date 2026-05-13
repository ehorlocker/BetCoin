package com.betcoin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * BetCoin theme composable that applies the dark color scheme,
 * custom typography, and shapes to all children.
 *
 * BetCoin is a dark-mode-only app per the design system.
 */
@Composable
fun BetCoinTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = BetCoinDarkColorScheme,
        typography = BetCoinTypography,
        shapes = BetCoinShapes,
        content = content,
    )
}
