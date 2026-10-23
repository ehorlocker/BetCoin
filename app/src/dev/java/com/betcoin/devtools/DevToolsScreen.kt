package com.betcoin.devtools

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private val DevToolsAction.title: String
    get() = when (this) {
        DevToolsAction.SEED_SAMPLE_DATA -> "Seed sample data"
        DevToolsAction.SEED_PENDING_BET -> "Seed pending bet"
        DevToolsAction.WIPE -> "Wipe dev database"
        DevToolsAction.RESET_ADMIN_PIN -> "Reset admin PIN"
    }

private val DevToolsAction.description: String
    get() = when (this) {
        DevToolsAction.SEED_SAMPLE_DATA -> "Replace the dev database with the fixed sample set"
        DevToolsAction.SEED_PENDING_BET -> "Add a pending bet with 2 players, then close the app"
        DevToolsAction.WIPE -> "Delete all data and avatars, then restart at Onboarding"
        DevToolsAction.RESET_ADMIN_PIN ->
            "Clear the admin PIN and recovery code; keep players and bets"
    }

private val DevToolsAction.confirmLabel: String
    get() = when (this) {
        DevToolsAction.SEED_SAMPLE_DATA, DevToolsAction.SEED_PENDING_BET -> "Seed"
        DevToolsAction.WIPE -> "Wipe"
        DevToolsAction.RESET_ADMIN_PIN -> "Reset"
    }

/** Stateful entry point: wires [DevToolsScreen] to [DevToolsViewModel] and handles app events. */
@Composable
fun DevToolsRoute(
    onNavigateBack: () -> Unit,
    viewModel: DevToolsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { event ->
            val activity = context.findActivity() ?: return@collect
            when (event) {
                DevToolsEvent.RestartApp -> restartApp(activity)
                DevToolsEvent.CloseApp -> closeApp(activity)
            }
        }
    }

    // Hold the message while the recovery-code dialog is up, otherwise the snackbar would
    // expire behind it; it shows right after "Done".
    LaunchedEffect(state.message, state.recoveryCode) {
        if (state.recoveryCode != null) return@LaunchedEffect
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    DevToolsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onRequest = viewModel::request,
        onConfirm = viewModel::confirm,
        onDismissConfirmation = viewModel::dismissConfirmation,
        onRecoveryCodeShown = viewModel::onRecoveryCodeShown,
    )
}

/** Stateless Dev Tools screen: database info panel plus the four dev actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevToolsScreen(
    state: DevToolsUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onRequest: (DevToolsAction) -> Unit,
    onConfirm: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onRecoveryCodeShown: () -> Unit,
) {
    // Leaving while an action runs would cancel it mid-way.
    BackHandler(enabled = state.busy) {}

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Dev Tools") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !state.busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            InfoCard(state.info)
            DevToolsAction.entries.forEach { action ->
                val enabled = !state.busy
                ListItem(
                    headlineContent = { Text(action.title) },
                    supportingContent = { Text(action.description) },
                    colors = ListItemDefaults.colors(
                        headlineColor = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                        supportingColor = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    ),
                    modifier = Modifier.clickable(enabled = enabled) { onRequest(action) },
                )
            }
        }
    }

    state.pendingConfirmation?.let { action ->
        AlertDialog(
            onDismissRequest = onDismissConfirmation,
            title = { Text("${action.title}?") },
            text = { Text(action.description) },
            confirmButton = { TextButton(onClick = onConfirm) { Text(action.confirmLabel) } },
            dismissButton = { TextButton(onClick = onDismissConfirmation) { Text("Cancel") } },
        )
    }

    state.recoveryCode?.let { code ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Recovery code") },
            text = {
                Column {
                    Text("Admin PIN is 0000. Save this recovery code; it's only shown once:")
                    Text(
                        text = code,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = onRecoveryCodeShown) { Text("Done") } },
        )
    }
}

@Composable
private fun InfoCard(info: DevDatabaseInfo?) {
    Card(modifier = Modifier.fillMaxWidth().padding(PaddingValues(16.dp))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Info", style = MaterialTheme.typography.titleMedium)
            if (info == null) {
                Text("Loading…", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Database ${info.databaseName}", style = MaterialTheme.typography.bodyMedium)
                Text("Schema version ${info.schemaVersion}", style = MaterialTheme.typography.bodyMedium)
                info.rowCounts.forEach { (table, count) ->
                    Text("$table: $count", style = MaterialTheme.typography.bodyMedium)
                }
                Text("App ${info.appVersion}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
