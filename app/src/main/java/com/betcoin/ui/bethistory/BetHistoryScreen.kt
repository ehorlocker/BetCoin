package com.betcoin.ui.bethistory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Placeholder screen for bet history.
 *
 * This is a temporary placeholder until the full Bet History feature is implemented.
 */
@Composable
fun BetHistoryScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Bet History - Coming Soon",
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}
