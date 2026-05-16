package com.betcoin.ui.bethistory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Placeholder screen for bet history.
 *
 * This is a temporary placeholder until the full Bet History feature is implemented.
 *
 * @param onNavigateBack callback invoked when the user wants to go back
 */
@Composable
fun BetHistoryScreen(
    onNavigateBack: () -> Unit = {},
) {
    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                TextButton(onClick = onNavigateBack) {
                    Text("Back")
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Bet History - Coming Soon",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}
