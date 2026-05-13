package com.betcoin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.betcoin.ui.components.BetCoinAvatar
import com.betcoin.ui.components.BetCoinButton
import com.betcoin.ui.components.BetCoinCard
import com.betcoin.ui.components.BetCoinChip
import com.betcoin.ui.components.BetCoinInput
import com.betcoin.ui.components.ButtonVariant
import com.betcoin.ui.theme.BetCoinTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single [ComponentActivity] for BetCoin, annotated with [AndroidEntryPoint]
 * to enable Hilt injection. Hosts the Compose NavHost via [setContent].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BetCoinTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "BetCoin",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    BetCoinAvatar(initials = "BC")
                    Spacer(modifier = Modifier.height(12.dp))

                    BetCoinCard {
                        Text("Welcome to BetCoin")
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    BetCoinChip(label = "Over 2.5", selected = false, onClick = {})
                    Spacer(modifier = Modifier.height(12.dp))

                    BetCoinInput(
                        value = "",
                        onValueChange = {},
                        placeholder = "Enter bet amount",
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    BetCoinButton(text = "Place Bet", onClick = {})
                    Spacer(modifier = Modifier.height(8.dp))

                    BetCoinButton(
                        text = "Cancel",
                        variant = ButtonVariant.Secondary,
                        onClick = {},
                    )
                }
            }
        }
    }
}
