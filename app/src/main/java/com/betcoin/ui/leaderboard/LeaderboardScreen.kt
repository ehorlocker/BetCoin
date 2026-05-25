package com.betcoin.ui.leaderboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Leaderboard screen that connects to the [LeaderboardViewModel].
 *
 * @param viewModel The view model providing UI state.
 * @param onNavigateBack Callback invoked when the user navigates back.
 * @param onPlayerClick Callback invoked when a player row is tapped.
 */
@Composable
fun LeaderboardScreen(
    viewModel: LeaderboardViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onPlayerClick: (Long) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LeaderboardScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onPlayerClick = onPlayerClick,
    )
}

/**
 * Stateless Leaderboard screen for preview and testing.
 *
 * @param uiState Current UI state to display.
 * @param onNavigateBack Callback invoked when the user navigates back.
 * @param onPlayerClick Callback invoked when a player row is tapped.
 */
@Composable
internal fun LeaderboardScreen(
    uiState: LeaderboardUiState,
    onNavigateBack: () -> Unit = {},
    onPlayerClick: (Long) -> Unit = {},
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
                Text("Leaderboard", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onNavigateBack) { Text("Back") }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (uiState) {
                is LeaderboardUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Loading...")
                    }
                }
                is LeaderboardUiState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No players yet",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                is LeaderboardUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        stickyHeader {
                            LeaderboardHeaderRow()
                        }
                        items(uiState.items, key = { it.userId }) { item ->
                            LeaderboardDataRow(
                                item = item,
                                onClick = { onPlayerClick(item.userId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HeaderCell("Rank", weight = 0.8f)
        HeaderCell("Username", weight = 1.8f)
        HeaderCell("Balance", weight = 1.2f)
        HeaderCell("W-L", weight = 0.8f)
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun LeaderboardDataRow(
    item: LeaderboardItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DataCell(text = item.rank.toString(), weight = 0.8f)
        DataCell(text = item.username, weight = 1.8f)
        DataCell(text = item.balance.toString(), weight = 1.2f)
        DataCell(text = "${item.totalWins}-${item.totalLosses}", weight = 0.8f)
    }
}

@Composable
private fun RowScope.DataCell(text: String, weight: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight),
    )
}
