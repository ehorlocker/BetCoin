package com.betcoin.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betcoin.ui.components.BetCoinButton
import com.betcoin.ui.components.BetCoinCard
import com.betcoin.ui.components.ButtonVariant

/**
 * Recovery screen shown at launch when an interrupted New Bet flow left a pending bet.
 *
 * Offers only Return to Bet or Discard Bet; there is intentionally no back/top bar navigation.
 */
@Composable
fun RecoveryScreen(
    viewModel: RecoveryViewModel,
    onReturnToBet: (Long) -> Unit,
    onGoHome: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState is RecoveryUiState.GoHome) {
        if (uiState is RecoveryUiState.GoHome) onGoHome()
    }

    RecoveryScreen(
        uiState = uiState,
        onReturnToBet = onReturnToBet,
        onDiscard = viewModel::discard,
    )
}

/** Stateless Recovery screen content. */
@Composable
fun RecoveryScreen(
    uiState: RecoveryUiState,
    onReturnToBet: (Long) -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            when (uiState) {
                is RecoveryUiState.Pending -> PendingContent(uiState, onReturnToBet, onDiscard)
                RecoveryUiState.Loading, RecoveryUiState.GoHome -> CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("recovery_loading"),
                )
            }
        }
    }
}

@Composable
private fun PendingContent(
    state: RecoveryUiState.Pending,
    onReturnToBet: (Long) -> Unit,
    onDiscard: () -> Unit,
) {
    val noun = if (state.participantCount == 1) "participant" else "participants"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "You have an unfinished bet",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("recovery_heading"),
        )
        Spacer(modifier = Modifier.height(24.dp))
        BetCoinCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = state.prompt,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("recovery_prompt"),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "${state.participantCount} $noun added so far",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("recovery_participant_count"),
        )
        Spacer(modifier = Modifier.height(32.dp))
        BetCoinButton(
            text = "Return to Bet",
            onClick = { onReturnToBet(state.betId) },
            enabled = !state.isDiscarding,
            modifier = Modifier.fillMaxWidth().testTag("recovery_return_to_bet"),
        )
        Spacer(modifier = Modifier.height(12.dp))
        BetCoinButton(
            text = "Discard Bet",
            onClick = onDiscard,
            enabled = !state.isDiscarding,
            variant = ButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth().testTag("recovery_discard"),
        )
    }
}
