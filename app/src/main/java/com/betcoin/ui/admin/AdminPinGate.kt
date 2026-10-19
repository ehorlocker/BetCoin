package com.betcoin.ui.admin

import androidx.compose.runtime.Composable
import com.betcoin.ui.components.PinEntryDialog

/**
 * Admin PIN prompt shown before the Admin Panel opens, with a "Forgot admin PIN?" link.
 *
 * @param verifier Checks a complete PIN; must not throw (see [AdminGateViewModel.verifyPin]).
 * @param onVerified Called once the admin PIN is accepted.
 * @param onForgotPin Called when the user taps "Forgot admin PIN?".
 * @param onDismiss Called when the user cancels.
 */
@Composable
fun AdminPinGate(
    verifier: suspend (String) -> Boolean,
    onVerified: () -> Unit,
    onForgotPin: () -> Unit,
    onDismiss: () -> Unit,
) {
    PinEntryDialog(
        label = "Admin",
        title = "Admin Panel",
        verifier = verifier,
        onVerified = onVerified,
        onDismiss = onDismiss,
        onForgotPin = onForgotPin,
    )
}
