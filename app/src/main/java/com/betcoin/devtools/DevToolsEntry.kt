package com.betcoin.devtools

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

/**
 * Flavor-specific hook for the Dev Tools screen (spec §14). The dev flavor registers the
 * destination and shows a Settings row; the prod flavor does nothing.
 */
interface DevToolsEntry {
    /** Registers Dev Tools destinations. No-op on prod. */
    fun NavGraphBuilder.devToolsDestinations(navController: NavHostController)

    /** Settings row that opens Dev Tools. Draws nothing on prod. */
    @Composable
    fun SettingsRow(navController: NavHostController)
}
