package com.betcoin.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betcoin.BuildConfig
import com.betcoin.ui.components.BetCoinButton

/**
 * Home screen — the main hub of the BetCoin app.
 *
 * Shows active bet count, user count, and navigation buttons
 * to other app sections.
 *
 * @param viewModel the [HomeViewModel] that manages state
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
) {
    val activeBetCount by viewModel.activeBetCount.collectAsStateWithLifecycle()
    val userCount by viewModel.userCount.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (BuildConfig.FLAVOR == "dev") {
            Text(
                text = "DEV MODE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Text(
            text = "BetCoin",
            style = MaterialTheme.typography.headlineLarge,
        )
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

        BetCoinButton(
            text = "New Bet",
            onClick = { /* TODO navigate to create bet */ },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        BetCoinButton(
            text = "Leaderboard",
            onClick = { /* TODO navigate to leaderboard */ },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        BetCoinButton(
            text = "Bet History",
            onClick = { /* TODO navigate to history */ },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        BetCoinButton(
            text = "Manage Players",
            onClick = { /* TODO navigate to players */ },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
