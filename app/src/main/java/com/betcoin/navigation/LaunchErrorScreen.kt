package com.betcoin.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.betcoin.ui.common.GENERIC_ERROR_MESSAGE
import com.betcoin.ui.components.BetCoinButton
import com.betcoin.ui.theme.BetCoinTheme

/** Title of the launch error screen. */
const val LAUNCH_ERROR_TITLE = "BetCoin couldn't start"

/** Shown when the start destination could not be resolved; offers a Retry. */
@Composable
fun LaunchErrorScreen(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = LAUNCH_ERROR_TITLE,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = GENERIC_ERROR_MESSAGE,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        BetCoinButton(text = "Retry", onClick = onRetry)
    }
}

@Preview(showBackground = true)
@Composable
private fun LaunchErrorScreenPreview() {
    BetCoinTheme { LaunchErrorScreen(onRetry = {}) }
}
