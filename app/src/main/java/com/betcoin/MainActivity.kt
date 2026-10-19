package com.betcoin

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.betcoin.navigation.NavGraph
import com.betcoin.navigation.LaunchErrorScreen
import com.betcoin.navigation.LaunchUiState
import com.betcoin.navigation.LaunchViewModel
import com.betcoin.ui.components.AppRootLayout
import com.betcoin.ui.theme.BetCoinTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single [ComponentActivity] for BetCoin, annotated with [AndroidEntryPoint]
 * to enable Hilt injection. Hosts the Compose NavHost via [setContent].
 *
 * Runs edge-to-edge (spec §4.1.1) and wraps everything in [AppRootLayout], which draws the
 * dev build banner on the dev flavor.
 *
 * The start destination comes from [LaunchViewModel]: a spinner while it resolves, the
 * [NavGraph] once ready, and [LaunchErrorScreen] with Retry if resolving failed.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val launchViewModel: LaunchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // BetCoin is dark-only, so system bar icons stay light whatever the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            BetCoinTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRootLayout {
                        val navController = rememberNavController()
                        val state by launchViewModel.uiState.collectAsStateWithLifecycle()

                        when (val current = state) {
                            LaunchUiState.Loading -> Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(WindowInsets.safeDrawing),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                            is LaunchUiState.Ready -> NavGraph(
                                navController = navController,
                                startDestination = current.startDestination,
                            )
                            LaunchUiState.Error -> LaunchErrorScreen(onRetry = launchViewModel::retry)
                        }
                    }
                }
            }
        }
    }
}
