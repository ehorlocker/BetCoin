package com.betcoin.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** Default test tag of the code text in [RecoveryCodeReveal], for Compose UI tests. */
const val RECOVERY_CODE_REVEAL_TAG = "recovery_code_reveal"

/**
 * Masked numeric PIN input shared by Onboarding, PIN recovery and the Admin Panel.
 *
 * Password semantics keep the digits masked and out of accessibility output (§5.5). The caller
 * sanitizes [value] to digits and the maximum length in [onValueChange].
 *
 * @param imeAction Keyboard action button; [onImeAction] runs when it is pressed.
 * @param supportingText Optional error line shown under the field.
 */
@Composable
fun PinTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeAction() }),
        singleLine = true,
        enabled = enabled,
        isError = isError,
        supportingText = supportingText?.let { message ->
            { Text(message, color = MaterialTheme.colorScheme.error) }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Shows a freshly generated recovery code once: explanation, the code in monospace, an "I've
 * written this down" checkbox and a button that stays disabled until it is ticked.
 *
 * Renders its own centered column, so callers only provide placement via [modifier] and decide
 * Back handling.
 *
 * @param codeTestTag Test tag of the code text, so each screen can use its own.
 */
@Composable
fun RecoveryCodeReveal(
    code: String,
    acknowledged: Boolean,
    onAcknowledgedChanged: (Boolean) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    codeTestTag: String = RECOVERY_CODE_REVEAL_TAG,
    continueLabel: String = "Continue",
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
            text = code,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(codeTestTag),
        )
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = acknowledged,
                    role = Role.Checkbox,
                    onValueChange = onAcknowledgedChanged,
                )
                .padding(vertical = 8.dp),
        ) {
            Checkbox(checked = acknowledged, onCheckedChange = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "I've written this down", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(modifier = Modifier.height(16.dp))

        BetCoinButton(
            text = continueLabel,
            onClick = onContinue,
            enabled = acknowledged,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
