package com.betcoin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.betcoin.data.repository.SettingsRepository
import com.betcoin.navigation.NavGraph
import com.betcoin.navigation.Routes
import com.betcoin.ui.theme.BetCoinTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The single [ComponentActivity] for BetCoin, annotated with [AndroidEntryPoint]
 * to enable Hilt injection. Hosts the Compose NavHost via [setContent].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BetCoinTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    var startDestination by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        startDestination = if (settingsRepository.isFirstLaunch()) {
                            Routes.ONBOARDING
                        } else {
                            Routes.HOME
                        }
                    }

                    if (startDestination != null) {
                        NavGraph(
                            navController = navController,
                            startDestination = startDestination!!,
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
