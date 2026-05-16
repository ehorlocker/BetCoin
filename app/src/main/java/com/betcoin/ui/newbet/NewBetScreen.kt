package com.betcoin.ui.newbet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Placeholder screen for creating a new bet.
 *
 * This is a temporary placeholder until the full New Bet feature is implemented.
 */
@Composable
fun NewBetScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "New Bet - Coming Soon",
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}
