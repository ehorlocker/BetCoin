package com.betcoin.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betcoin.ui.components.BetCoinButton
import com.betcoin.ui.components.PinTextField
import com.betcoin.ui.components.RecoveryCodeReveal

/** Test tag on the text that shows the new recovery code. */
const val ADMIN_RECOVERY_CODE_TAG = "admin_recovery_new_code"

/**
 * Reset Admin PIN screen (spec §5.6): recovery code, new PIN, then the replacement code.
 *
 * @param viewModel the [AdminPinRecoveryViewModel] that manages state
 * @param onNavigateBack invoked when Back is pressed on the first step
 * @param onRecovered invoked when the user continues after acknowledging the new code
 */
@Composable
fun AdminPinRecoveryScreen(
    viewModel: AdminPinRecoveryViewModel,
    onNavigateBack: () -> Unit,
    onRecovered: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recovered by viewModel.recovered.collectAsStateWithLifecycle()

    LaunchedEffect(recovered) {
        if (recovered) onRecovered()
    }

    AdminPinRecoveryContent(
        uiState = uiState,
        onBack = { if (!viewModel.onBack()) onNavigateBack() },
        onCodeChanged = viewModel::onCodeChanged,
        onSubmitCode = viewModel::onSubmitCode,
        onPinChanged = viewModel::onPinChanged,
        onConfirmPinChanged = viewModel::onConfirmPinChanged,
        onSavePin = viewModel::onSavePin,
        onAcknowledgedChanged = viewModel::onAcknowledgedChanged,
        onContinue = viewModel::onContinue,
    )
}

/** Stateless Reset Admin PIN content for [uiState]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPinRecoveryContent(
    uiState: AdminPinRecoveryUiState,
    onBack: () -> Unit,
    onCodeChanged: (String) -> Unit,
    onSubmitCode: () -> Unit,
    onPinChanged: (String) -> Unit,
    onConfirmPinChanged: (String) -> Unit,
    onSavePin: () -> Unit,
    onAcknowledgedChanged: (Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    // Once the new code is shown the PIN is already reset and the code is never shown again,
    // so Back can't leave that step.
    val backEnabled = uiState !is AdminPinRecoveryUiState.ShowNewCode
    BackHandler { if (backEnabled) onBack() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Reset Admin PIN") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = backEnabled) {
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (uiState) {
                is AdminPinRecoveryUiState.EnterCode -> EnterCodeStep(uiState, onCodeChanged, onSubmitCode)
                is AdminPinRecoveryUiState.EnterNewPin -> EnterNewPinStep(
                    state = uiState,
                    onPinChanged = onPinChanged,
                    onConfirmPinChanged = onConfirmPinChanged,
                    onSavePin = onSavePin,
                )
                is AdminPinRecoveryUiState.ShowNewCode -> {
                    RecoveryCodeReveal(
                        code = uiState.recoveryCode,
                        acknowledged = uiState.acknowledged,
                        onAcknowledgedChanged = onAcknowledgedChanged,
                        onContinue = onContinue,
                        codeTestTag = ADMIN_RECOVERY_CODE_TAG,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your old recovery code no longer works.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun EnterCodeStep(
    state: AdminPinRecoveryUiState.EnterCode,
    onCodeChanged: (String) -> Unit,
    onSubmitCode: () -> Unit,
) {
    Text(
        text = "Enter the recovery code you wrote down when you set up the admin PIN.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(24.dp))
    OutlinedTextField(
        value = state.code,
        onValueChange = onCodeChanged,
        label = { Text("Recovery code") },
        singleLine = true,
        isError = state.error != null,
        supportingText = state.error?.let { message ->
            { Text(message, color = MaterialTheme.colorScheme.error) }
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmitCode() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(16.dp))
    BetCoinButton(text = "Next", onClick = onSubmitCode, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun EnterNewPinStep(
    state: AdminPinRecoveryUiState.EnterNewPin,
    onPinChanged: (String) -> Unit,
    onConfirmPinChanged: (String) -> Unit,
    onSavePin: () -> Unit,
) {
    Text(
        text = "Choose a new admin PIN",
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(24.dp))
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
        onValueChange = onConfirmPinChanged,
        label = "Confirm PIN",
        isError = state.error != null,
        enabled = !state.isSaving,
        imeAction = ImeAction.Done,
        onImeAction = onSavePin,
        supportingText = state.error,
    )
    Spacer(modifier = Modifier.height(16.dp))
    BetCoinButton(
        text = "Save PIN",
        onClick = onSavePin,
        enabled = !state.isSaving,
        modifier = Modifier.fillMaxWidth(),
    )
}
