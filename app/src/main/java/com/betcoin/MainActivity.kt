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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.betcoin.devtools.DevToolsEntry
import com.betcoin.navigation.NavGraph
import com.betcoin.navigation.LaunchErrorScreen
import com.betcoin.navigation.LaunchUiState
import com.betcoin.navigation.LaunchViewModel
import com.betcoin.ui.components.AppRootLayout
import com.betcoin.ui.theme.AppThemeViewModel
import com.betcoin.ui.theme.BetCoinTheme
import com.betcoin.ui.theme.LegacyDarkScrim
import com.betcoin.ui.theme.applyApplicationNightMode
import com.betcoin.ui.theme.isDarkTheme
import com.betcoin.ui.theme.systemBarStyle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The single [ComponentActivity] for BetCoin, annotated with [AndroidEntryPoint]
 * to enable Hilt injection. Hosts the Compose NavHost via [setContent].
 *
 * Runs edge-to-edge (spec §4.1.1) and wraps everything in [AppRootLayout], which draws the
 * dev build banner on the dev flavor.
 *
 * The start destination comes from [LaunchViewModel]: a spinner while it resolves, the
 * [NavGraph] once ready, and [LaunchErrorScreen] with Retry if resolving failed.
 *
 * The theme comes from [AppThemeViewModel]: nothing is drawn until the stored preference is
 * known, and the system bar icon style follows the resolved light/dark theme (see
 * [systemBarStyle]). On the dev flavor the status bar sits over the banner's errorContainer,
 * which contrasts with the same icon rule in both themes.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val launchViewModel: LaunchViewModel by viewModels()

    @Inject
    lateinit var devToolsEntry: DevToolsEntry

    private val appThemeViewModel: AppThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Until the stored preference is known, follow the device mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, LegacyDarkScrim),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, LegacyDarkScrim),
        )
        setContent {
            // Render nothing until the stored preference is known, so the wrong theme never flashes.
            val themePreference = appThemeViewModel.themePreference
                .collectAsStateWithLifecycle().value ?: return@setContent
            val darkTheme = themePreference.isDarkTheme()

            // Persist the stored theme as the app night mode so the next cold start matches it.
            // On API 31+ a change also recreates the activity (ViewModels survive, plain remember does not).
            LaunchedEffect(themePreference) {
                applyApplicationNightMode(this@MainActivity, themePreference)
            }

            // System bar icon style follows the resolved theme.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = systemBarStyle(darkTheme),
                    navigationBarStyle = systemBarStyle(darkTheme),
                )
                onDispose {}
            }

            BetCoinTheme(themePreference) {
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
                                devToolsEntry = devToolsEntry,
                            )
                            LaunchUiState.Error -> LaunchErrorScreen(onRetry = launchViewModel::retry)
                        }
                    }
                }
            }
        }
    }
}
