package com.betcoin.ui.bethistory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betcoin.data.model.BetStatus
import com.betcoin.ui.components.BetCoinCard
import com.betcoin.ui.components.formatCoins
import java.text.DateFormat
import java.util.Date

/**
 * Bet History screen (§4.2): every non-pending bet, newest first, with All / Active / Resolved /
 * Cancelled tabs. Tapping a row opens Bet Detail.
 *
 * @param viewModel the [BetHistoryViewModel] that manages state
 * @param onNavigateBack called when the user wants to go back
 * @param onBetClick called with a bet's ID when its row is tapped
 */
@Composable
fun BetHistoryScreen(
    viewModel: BetHistoryViewModel,
    onNavigateBack: () -> Unit = {},
    onBetClick: (Long) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BetHistoryContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onFilterSelected = viewModel::selectFilter,
        onBetClick = onBetClick,
    )
}

/** Stateless Bet History UI, driven directly by tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetHistoryContent(
    uiState: BetHistoryUiState,
    onNavigateBack: () -> Unit = {},
    onFilterSelected: (BetHistoryFilter) -> Unit = {},
    onBetClick: (Long) -> Unit = {},
) {
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
                    text = "Bet History",
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
                .padding(padding),
        ) {
            if (!uiState.isError) {
                PrimaryTabRow(selectedTabIndex = uiState.filter.ordinal) {
                    BetHistoryFilter.entries.forEach { filter ->
                        Tab(
                            selected = filter == uiState.filter,
                            onClick = { onFilterSelected(filter) },
                            text = { Text(filter.label) },
                        )
                    }
                }
            }
            when {
                uiState.isLoading -> CenteredBox { CircularProgressIndicator() }
                uiState.isError -> CenteredBox { MessageText("Couldn't load bets.") }
                uiState.rows.isEmpty() -> CenteredBox { MessageText(uiState.filter.emptyMessage) }
                else -> LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.rows, key = { it.betId }) { row ->
                        BetHistoryRowCard(row = row, onClick = { onBetClick(row.betId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun MessageText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BetHistoryRowCard(row: BetHistoryRow, onClick: () -> Unit) {
    BetCoinCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bet_history_row_${row.betId}"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = row.prompt,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(row.status)
            }
            val participants = if (row.participantCount == 1) "1 participant" else "${row.participantCount} participants"
            Text(
                text = "${formatDate(row.createdAt)} · $participants · Pot: ${formatCoins(row.pot)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Duplicated from BetDetailScreen (private there) to avoid conflicts with parallel work on that file.
@Composable
private fun StatusBadge(status: BetStatus) {
    val color = when (status) {
        BetStatus.ACTIVE -> MaterialTheme.colorScheme.tertiary
        BetStatus.RESOLVED -> MaterialTheme.colorScheme.primary
        BetStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
        BetStatus.PENDING_CONFIRMATION -> MaterialTheme.colorScheme.secondary
    }
    val label = when (status) {
        BetStatus.PENDING_CONFIRMATION -> "PENDING"
        else -> status.name
    }
    Surface(
        shape = RoundedCornerShape(9999.dp),
        color = Color.Transparent,
        modifier = Modifier
            .testTag("status_badge")
            .border(1.dp, color, RoundedCornerShape(9999.dp)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
