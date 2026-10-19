package com.betcoin.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betcoin.ui.components.PinTextField
import com.betcoin.ui.components.RecoveryCodeReveal

/** Test tag on the text that shows the regenerated recovery code. */
const val ADMIN_SETTINGS_RECOVERY_CODE_TAG = "admin_settings_recovery_code"

/**
 * Admin Panel (spec §4.2): Player Management, Bet Management and App Settings.
 *
 * @param viewModel the [AdminViewModel] that manages state
 * @param onNavigateBack invoked when Back is pressed and leaving is allowed
 */
@Composable
fun AdminPanelScreen(
    viewModel: AdminViewModel,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AdminPanelContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onChangePinClicked = viewModel::onChangePinClicked,
        onChangePinPinChanged = viewModel::onChangePinPinChanged,
        onChangePinConfirmChanged = viewModel::onChangePinConfirmChanged,
        onChangePinSave = viewModel::onChangePinSave,
        onChangePinDismiss = viewModel::onChangePinDismiss,
        onRegenerateClicked = viewModel::onRegenerateClicked,
        onRegenerateConfirm = viewModel::onRegenerateConfirm,
        onRegenerateCancel = viewModel::onRegenerateCancel,
        onRecoveryCodeAcknowledged = viewModel::onRecoveryCodeAcknowledged,
        onRecoveryCodeDone = viewModel::onRecoveryCodeDone,
        onMessageShown = viewModel::onMessageShown,
    )
}

/** Stateless Admin Panel content for [uiState]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelContent(
    uiState: AdminUiState,
    onNavigateBack: () -> Unit,
    onChangePinClicked: () -> Unit,
    onChangePinPinChanged: (String) -> Unit,
    onChangePinConfirmChanged: (String) -> Unit,
    onChangePinSave: () -> Unit,
    onChangePinDismiss: () -> Unit,
    onRegenerateClicked: () -> Unit,
    onRegenerateConfirm: () -> Unit,
    onRegenerateCancel: () -> Unit,
    onRecoveryCodeAcknowledged: (Boolean) -> Unit,
    onRecoveryCodeDone: () -> Unit,
    onMessageShown: () -> Unit,
) {
    // Leaving while a code is being generated or shown (or a PIN is saving) would cancel the
    // work after the repository may already have committed it; the new code is shown only once.
    val backEnabled = uiState.recoveryCode == null && uiState.changePin?.isSaving != true
    BackHandler { if (backEnabled) onNavigateBack() }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Admin Panel") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = backEnabled) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            adminPlayerSection()
            adminBetSection()
            appSettingsSection(onChangePinClicked, onRegenerateClicked)
        }
    }

    uiState.changePin?.let { dialog ->
        ChangePinDialog(
            state = dialog,
            onPinChanged = onChangePinPinChanged,
            onConfirmChanged = onChangePinConfirmChanged,
            onSave = onChangePinSave,
            onDismiss = onChangePinDismiss,
        )
    }

    when (val code = uiState.recoveryCode) {
        null -> Unit
        RecoveryCodeDialogState.ConfirmRegenerate -> AlertDialog(
            onDismissRequest = onRegenerateCancel,
            title = { Text("Regenerate recovery code?") },
            text = { Text("Your current recovery code will stop working.") },
            confirmButton = { TextButton(onClick = onRegenerateConfirm) { Text("Regenerate") } },
            dismissButton = { TextButton(onClick = onRegenerateCancel) { Text("Cancel") } },
        )
        RecoveryCodeDialogState.Generating -> Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(24.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Creating recovery code...", modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
        is RecoveryCodeDialogState.Show -> Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                RecoveryCodeReveal(
                    code = code.code,
                    acknowledged = code.acknowledged,
                    onAcknowledgedChanged = onRecoveryCodeAcknowledged,
                    onContinue = onRecoveryCodeDone,
                    codeTestTag = ADMIN_SETTINGS_RECOVERY_CODE_TAG,
                    continueLabel = "Done",
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ChangePinDialog(
    state: ChangePinDialogState,
    onPinChanged: (String) -> Unit,
    onConfirmChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = { Text("Change Admin PIN") },
        text = {
            Column {
                PinTextField(
                    value = state.pin,
                    onValueChange = onPinChanged,
                    label = "New PIN",
                    isError = state.error != null,
                    enabled = !state.isSaving,
                    imeAction = ImeAction.Next,
                )
                Spacer(modifier = Modifier.height(8.dp))
                PinTextField(
                    value = state.confirmPin,
                    onValueChange = onConfirmChanged,
                    label = "Confirm PIN",
                    isError = state.error != null,
                    enabled = !state.isSaving,
                    imeAction = ImeAction.Done,
                    onImeAction = onSave,
                    supportingText = state.error,
                )
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = !state.isSaving) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("Cancel") } },
    )
}

private fun LazyListScope.appSettingsSection(
    onChangePinClicked: () -> Unit,
    onRegenerateClicked: () -> Unit,
) {
    item { AdminSectionHeader("App Settings") }
    item {
        ListItem(
            headlineContent = { Text("Change Admin PIN") },
            supportingContent = { Text("Choose a new 4-digit PIN for the Admin Panel") },
            modifier = Modifier.clickable(onClick = onChangePinClicked),
        )
    }
    item {
        ListItem(
            headlineContent = { Text("Regenerate Recovery Code") },
            supportingContent = { Text("Create a new code; the current one stops working") },
            modifier = Modifier.clickable(onClick = onRegenerateClicked),
        )
    }
}

/** Section header used by every Admin Panel section. */
@Composable
internal fun AdminSectionHeader(title: String) {
    Column {
        HorizontalDivider()
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
    }
}

/** Muted placeholder line for sections that BET-30/BET-31 fill in. */
@Composable
internal fun AdminPlaceholderLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    )
}
