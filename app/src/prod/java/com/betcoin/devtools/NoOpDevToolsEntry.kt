package com.betcoin.devtools

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import javax.inject.Inject

/** Prod [DevToolsEntry]: Dev Tools does not exist in release builds. */
class NoOpDevToolsEntry @Inject constructor() : DevToolsEntry {
    override fun NavGraphBuilder.devToolsDestinations(navController: NavHostController) = Unit

    @Composable
    override fun SettingsRow(navController: NavHostController) = Unit
}
