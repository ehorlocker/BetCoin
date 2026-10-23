package com.betcoin.devtools

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import javax.inject.Inject

/** Route of the Dev Tools screen (dev flavor only). */
const val DEV_TOOLS_ROUTE = "dev_tools"

/** Dev [DevToolsEntry]: registers the Dev Tools route and a Settings row that opens it. */
class DevDevToolsEntry @Inject constructor() : DevToolsEntry {
    override fun NavGraphBuilder.devToolsDestinations(navController: NavHostController) {
        composable(DEV_TOOLS_ROUTE) {
            DevToolsRoute(onNavigateBack = { navController.popBackStack() })
        }
    }

    @Composable
    override fun SettingsRow(navController: NavHostController) {
        ListItem(
            headlineContent = { Text("Dev Tools") },
            supportingContent = { Text("Test-data tools (dev build only)") },
            modifier = Modifier.clickable { navController.navigate(DEV_TOOLS_ROUTE) },
        )
    }
}
