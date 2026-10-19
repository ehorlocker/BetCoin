package com.betcoin

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.betcoin.devtools.DevToolsEntry
import com.betcoin.navigation.NavGraph
import com.betcoin.navigation.StartDestinationResolver
import com.betcoin.ui.components.AppRootLayout
import com.betcoin.ui.theme.AppThemeViewModel
import com.betcoin.ui.theme.BetCoinTheme
import com.betcoin.ui.theme.isDarkTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The single [ComponentActivity] for BetCoin, annotated with [AndroidEntryPoint]
 * to enable Hilt injection. Hosts the Compose NavHost via [setContent].
 *
 * Runs edge-to-edge (spec §4.1.1) and wraps everything in [AppRootLayout], which draws the
 * dev build banner on the dev flavor.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var startDestinationResolver: StartDestinationResolver

    @Inject
    lateinit var devToolsEntry: DevToolsEntry

    private val appThemeViewModel: AppThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            // Render nothing until the stored preference is known, so the wrong theme never flashes.
            val themePreference = appThemeViewModel.themePreference
                .collectAsStateWithLifecycle().value ?: return@setContent
            val darkTheme = themePreference.isDarkTheme()

            // System bar icon style follows the resolved theme.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
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
                        var startDestination by rememberSaveable { mutableStateOf<String?>(null) }

                        LaunchedEffect(Unit) {
                            if (startDestination == null) {
                                startDestination = startDestinationResolver.resolve()
                            }
                        }

                        if (startDestination != null) {
                            NavGraph(
                                navController = navController,
                                startDestination = startDestination!!,
                                devToolsEntry = devToolsEntry,
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
}
