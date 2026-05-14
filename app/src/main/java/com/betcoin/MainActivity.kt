package com.betcoin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.betcoin.data.repository.SettingsRepository
import com.betcoin.navigation.NavGraph
import com.betcoin.navigation.Routes
import com.betcoin.ui.theme.BetCoinTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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

        lifecycleScope.launch {
            val isFirstLaunch = settingsRepository.isFirstLaunch()
            val startDestination = if (isFirstLaunch) Routes.ONBOARDING else Routes.HOME

            setContent {
                BetCoinTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        val navController = rememberNavController()
                        NavGraph(
                            navController = navController,
                            startDestination = startDestination,
                        )
                    }
                }
            }
        }
    }
}
