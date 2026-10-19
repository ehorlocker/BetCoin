package com.betcoin.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betcoin.data.model.ThemePreference

/**
 * Settings screen (ungated): theme picker, app version and the flavor's Dev Tools row.
 *
 * @param viewModel the [SettingsViewModel] that manages state
 * @param versionLabel text for the Version row, see [appVersionLabel]
 * @param onBack invoked when Back is pressed
 * @param devToolsRow flavor-provided slot shown at the bottom (empty on prod)
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    versionLabel: String,
    onBack: () -> Unit,
    devToolsRow: @Composable () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        versionLabel = versionLabel,
        onThemeSelected = viewModel::onThemeSelected,
        onErrorShown = viewModel::onErrorShown,
        onBack = onBack,
        devToolsRow = devToolsRow,
    )
}

/** Stateless Settings screen; see the [SettingsViewModel] overload. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    versionLabel: String,
    onThemeSelected: (ThemePreference) -> Unit,
    onErrorShown: () -> Unit,
    onBack: () -> Unit,
    devToolsRow: @Composable () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onErrorShown()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Column(modifier = Modifier.selectableGroup()) {
                ThemeOption("System default", ThemePreference.SYSTEM, uiState.themePreference, onThemeSelected)
                ThemeOption("Light", ThemePreference.LIGHT, uiState.themePreference, onThemeSelected)
                ThemeOption("Dark", ThemePreference.DARK, uiState.themePreference, onThemeSelected)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ListItem(
                headlineContent = { Text("Version") },
                trailingContent = { Text(versionLabel) },
            )
            devToolsRow()
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    option: ThemePreference,
    current: ThemePreference,
    onSelected: (ThemePreference) -> Unit,
) {
    val selected = option == current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = { onSelected(option) }, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
