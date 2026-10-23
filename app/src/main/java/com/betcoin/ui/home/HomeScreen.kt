package com.betcoin.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betcoin.ui.components.BetCoinButton

/** A bet needs at least two participants, so New Bet stays disabled below this (§10). */
private const val MIN_PLAYERS_FOR_BET = 2

/**
 * Home screen — the main hub of the BetCoin app.
 *
 * Shows active bet count, user count, and navigation buttons
 * to other app sections.
 *
 * @param viewModel the [HomeViewModel] that manages state
 * @param onSettings invoked when the gear icon in the top-end corner is tapped
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNewBet: () -> Unit = {},
    onLeaderboard: () -> Unit = {},
    onBetHistory: () -> Unit = {},
    onManagePlayers: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val activeBetCount by viewModel.activeBetCount.collectAsStateWithLifecycle()
    val userCount by viewModel.userCount.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Balances the gear so the title stays centred.
            Spacer(modifier = Modifier.size(48.dp))
            Text(
                text = "BetCoin",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Active bets: $activeBetCount",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Players: $userCount",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(32.dp))

        val canStartBet = userCount >= MIN_PLAYERS_FOR_BET
        BetCoinButton(
            text = "New Bet",
            onClick = onNewBet,
            enabled = canStartBet,
            modifier = Modifier.fillMaxWidth().testTag("home_new_bet"),
        )
        if (!canStartBet) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Add at least $MIN_PLAYERS_FOR_BET players in Manage Players to start a bet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        BetCoinButton(
            text = "Leaderboard",
            onClick = onLeaderboard,
            modifier = Modifier.fillMaxWidth().testTag("home_leaderboard"),
        )
        Spacer(modifier = Modifier.height(12.dp))
        BetCoinButton(
            text = "Bet History",
            onClick = onBetHistory,
            modifier = Modifier.fillMaxWidth().testTag("home_bet_history"),
        )
        Spacer(modifier = Modifier.height(12.dp))
        BetCoinButton(
            text = "Manage Players",
            onClick = onManagePlayers,
            modifier = Modifier.fillMaxWidth().testTag("home_manage_players"),
        )
    }
}
