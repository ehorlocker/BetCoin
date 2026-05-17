package com.betcoin.ui.leaderboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Leaderboard",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
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
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
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
    ) {
        HeaderCell("Rank", weight = 0.5f)
        HeaderCell("Username", weight = 1.5f)
        HeaderCell("Balance", weight = 1f)
        HeaderCell("W-L", weight = 0.8f)
        HeaderCell("Earnings", weight = 1f)
        HeaderCell("Losses", weight = 1f)
        HeaderCell("Bailouts", weight = 0.9f)
        HeaderCell("Debt", weight = 1f)
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
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
    ) {
        DataCell(text = item.rank.toString(), weight = 0.5f)
        DataCell(text = item.username, weight = 1.5f)
        DataCell(text = item.balance.toString(), weight = 1f)
        DataCell(text = "${item.totalWins}-${item.totalLosses}", weight = 0.8f)
        DataCell(text = item.totalEarnings.toString(), weight = 1f)
        DataCell(text = item.totalLost.toString(), weight = 1f)
        DataCell(text = item.bailoutCount.toString(), weight = 0.9f)
        DataCell(text = item.totalDebt.toString(), weight = 1f)
    }
}

@Composable
private fun RowScope.DataCell(text: String, weight: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(weight),
    )
}
