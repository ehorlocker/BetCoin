package com.betcoin.ui.players

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betcoin.data.database.entity.User

/**
 * Player detail screen that connects to the [PlayerDetailViewModel].
 *
 * @param userId The unique identifier of the player to display.
 * @param viewModel The view model providing UI state.
 * @param onNavigateBack Callback invoked when the user requests to navigate back.
 */
@Composable
fun PlayerDetailScreen(
    userId: Long,
    viewModel: PlayerDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlayerDetailScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
    )
}

/**
 * Stateless player detail screen for preview and testing.
 *
 * @param uiState Current UI state to display.
 * @param onNavigateBack Callback invoked when the user requests to navigate back.
 */
@Composable
internal fun PlayerDetailScreen(
    uiState: PlayerDetailUiState,
    onNavigateBack: () -> Unit = {},
) {
    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Player Details", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onNavigateBack) { Text("Back") }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            when (uiState) {
                is PlayerDetailUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Loading...")
                    }
                }
                is PlayerDetailUiState.NotFound -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Player not found",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                is PlayerDetailUiState.Success -> {
                    PlayerStats(user = uiState.user)
                }
            }
        }
    }
}

@Composable
private fun PlayerStats(user: User) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatRow(label = "Username", value = user.username)
        StatRow(label = "Balance", value = user.balance.toString())
        StatRow(label = "Record", value = "${user.totalWins}-${user.totalLosses}")
        StatRow(label = "Total Earnings", value = user.totalEarnings.toString())
        StatRow(label = "Total Losses", value = user.totalLost.toString())
        StatRow(label = "Bailouts", value = user.bailoutCount.toString())
        StatRow(label = "Total Debt", value = user.totalDebt.toString())
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
