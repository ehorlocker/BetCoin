package com.example.betcoin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
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
            // Placeholder — NavHost will be added in a later ticket
            Text("BetCoin")
        }
    }
}
