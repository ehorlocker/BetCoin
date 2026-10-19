package com.betcoin.ui.leaderboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betcoin.ui.components.BetCoinCard
import com.betcoin.ui.components.formatCoins

/** Matches [BetCoinCard]'s inner padding so header columns line up with row columns. */
private val CardContentPadding = 24.dp

/**
 * Leaderboard screen (§4.2): players ranked by net worth with their win-loss record.
 *
 * Tapping a row opens that player's Player Detail.
 *
 * @param viewModel the [LeaderboardViewModel] that manages state
 * @param onNavigateBack called when the user wants to go back
 * @param onPlayerClick called with a player's ID when their row is tapped
 */
@Composable
fun LeaderboardScreen(
    viewModel: LeaderboardViewModel,
    onNavigateBack: () -> Unit = {},
    onPlayerClick: (Long) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isNavigatingBack by remember { mutableStateOf(false) }

    BackHandler(enabled = !isNavigatingBack) {
        isNavigatingBack = true
        onNavigateBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Leaderboard",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onNavigateBack) {
                    Text("Back")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            when {
                uiState.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                uiState.rows.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No players yet. Add players from Manage Players.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    LeaderboardColumns(
                        rank = { HeaderText("Rank") },
                        username = { HeaderText("Username") },
                        netWorth = { HeaderText("Net Worth", textAlign = TextAlign.End) },
                        winLoss = { HeaderText("W-L", textAlign = TextAlign.End) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CardContentPadding, vertical = 8.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.rows, key = { it.userId }) { row ->
                            LeaderboardRow(row = row, onClick = { onPlayerClick(row.userId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderText(text: String, textAlign: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** One player's rank, username, net worth, and win-loss record. Tapping opens Player Detail. */
@Composable
private fun LeaderboardRow(row: LeaderboardViewModel.Row, onClick: () -> Unit) {
    BetCoinCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        LeaderboardColumns(
            rank = { Text(text = "${row.rank}", style = MaterialTheme.typography.titleMedium) },
            username = {
                Text(
                    text = row.username,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            netWorth = {
                Text(
                    text = formatCoins(row.netWorth),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (row.netWorth < 0) MaterialTheme.colorScheme.error else Color.Unspecified,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            winLoss = {
                Text(
                    text = "${row.wins}-${row.losses}",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Shared column layout so the header and the rows cannot drift apart. */
@Composable
private fun LeaderboardColumns(
    rank: @Composable () -> Unit,
    username: @Composable () -> Unit,
    netWorth: @Composable () -> Unit,
    winLoss: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(48.dp)) { rank() }
        Box(modifier = Modifier.weight(1f)) { username() }
        Box(modifier = Modifier.width(96.dp), contentAlignment = Alignment.CenterEnd) { netWorth() }
        Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) { winLoss() }
    }
}
