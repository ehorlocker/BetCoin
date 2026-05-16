package com.betcoin.ui.leaderboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Placeholder screen for the leaderboard.
 *
 * This is a temporary placeholder until the full Leaderboard feature is implemented.
 */
@Composable
fun LeaderboardScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Leaderboard - Coming Soon",
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}
