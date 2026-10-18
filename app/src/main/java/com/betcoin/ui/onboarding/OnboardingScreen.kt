package com.betcoin.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betcoin.ui.components.BetCoinButton

/** Test tag on the text that shows the recovery code. */
const val RECOVERY_CODE_TAG = "onboarding_recovery_code"

/**
 * Onboarding screen shown on first launch (spec §12).
 *
 * Asks for the 4-digit master admin PIN twice, then shows the recovery code once. Continue
 * stays disabled until "I've written this down" is ticked.
 *
 * @param viewModel the [OnboardingViewModel] that manages state
 * @param onOnboardingComplete invoked when the user continues past the recovery code
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onOnboardingComplete: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()

    LaunchedEffect(onboardingComplete) {
        if (onboardingComplete) {
            onOnboardingComplete()
        }
    }

    OnboardingContent(
        uiState = uiState,
        onPinChanged = viewModel::onPinChanged,
        onConfirmPinChanged = viewModel::onConfirmPinChanged,
        onSavePin = viewModel::onSavePin,
        onAcknowledgedChanged = viewModel::onAcknowledgedChanged,
        onContinue = viewModel::onContinue,
    )
}

/**
 * Stateless onboarding content for [uiState].
 */
@Composable
fun OnboardingContent(
    uiState: OnboardingUiState,
    onPinChanged: (String) -> Unit,
    onConfirmPinChanged: (String) -> Unit,
    onSavePin: () -> Unit,
    onAcknowledgedChanged: (Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (uiState) {
            is OnboardingUiState.EnterPin -> EnterPinStep(
                state = uiState,
                onPinChanged = onPinChanged,
                onConfirmPinChanged = onConfirmPinChanged,
                onSavePin = onSavePin,
            )
            is OnboardingUiState.ShowRecoveryCode -> RecoveryCodeStep(
                state = uiState,
                onAcknowledgedChanged = onAcknowledgedChanged,
                onContinue = onContinue,
            )
        }
    }
}

@Composable
private fun EnterPinStep(
    state: OnboardingUiState.EnterPin,
    onPinChanged: (String) -> Unit,
    onConfirmPinChanged: (String) -> Unit,
    onSavePin: () -> Unit,
) {
    Text(
        text = "Welcome to BetCoin",
        style = MaterialTheme.typography.headlineLarge,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Set up your master admin PIN",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(modifier = Modifier.height(24.dp))

    PinField(
        value = state.pin,
        onValueChange = onPinChanged,
        label = "4-digit PIN",
        isError = state.error != null,
        enabled = !state.isSaving,
        imeAction = ImeAction.Next,
        onDone = {},
    )
    Spacer(modifier = Modifier.height(8.dp))
    PinField(
        value = state.confirmPin,
        onValueChange = onConfirmPinChanged,
        label = "Confirm PIN",
        isError = state.error != null,
        enabled = !state.isSaving,
        imeAction = ImeAction.Done,
        onDone = onSavePin,
        supportingText = state.error,
    )
    Spacer(modifier = Modifier.height(16.dp))

    BetCoinButton(
        text = "Set PIN",
        onClick = onSavePin,
        enabled = !state.isSaving,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    enabled: Boolean,
    imeAction: ImeAction,
    onDone: () -> Unit,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        // Password semantics keep the digits masked and out of accessibility output (§5.5).
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        enabled = enabled,
        isError = isError,
        supportingText = supportingText?.let { message ->
            { Text(message, color = MaterialTheme.colorScheme.error) }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RecoveryCodeStep(
    state: OnboardingUiState.ShowRecoveryCode,
    onAcknowledgedChanged: (Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    // The PIN is already saved and the code is never shown again, so Back can't leave this step.
    BackHandler {}

    Text(
        text = "Save your recovery code",
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "If you forget the admin PIN, this code lets you set a new one. " +
            "It's shown only once, so write it down somewhere safe.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = state.recoveryCode,
        style = MaterialTheme.typography.headlineSmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag(RECOVERY_CODE_TAG),
    )
    Spacer(modifier = Modifier.height(24.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = state.acknowledged,
                role = Role.Checkbox,
                onValueChange = onAcknowledgedChanged,
            )
            .padding(vertical = 8.dp),
    ) {
        Checkbox(checked = state.acknowledged, onCheckedChange = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = "I've written this down", style = MaterialTheme.typography.bodyLarge)
    }
    Spacer(modifier = Modifier.height(16.dp))

    BetCoinButton(
        text = "Continue",
        onClick = onContinue,
        enabled = state.acknowledged,
        modifier = Modifier.fillMaxWidth(),
    )
}
