package com.betcoin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.betcoin.ui.theme.BetCoinInputBackground
import com.betcoin.ui.theme.BetCoinOutline
import com.betcoin.ui.theme.BetCoinTertiary
import kotlinx.coroutines.launch

/** Number of digits in a player or admin PIN (§5.5). */
const val PIN_LENGTH = 4

/** Test tag of the PIN text field, for Compose UI tests. */
const val PIN_INPUT_TAG = "pin_entry_input"

/**
 * Reusable modal PIN entry dialog (§5.5).
 *
 * Shows whose PIN is being asked for and a masked 4-digit input that submits itself on the 4th
 * digit. A wrong PIN shows "Wrong PIN" inline and clears the field so the same person can retry;
 * there is no lockout. The entered digits live only in this dialog's state and are cleared after
 * every attempt.
 *
 * The dialog is a regular (not full-screen) window above the app root, so the dev banner (§14)
 * stays visible under the scrim. Tapping outside doesn't dismiss it, so a stray tap can't abort a
 * consent sequence; Cancel and Back call [onDismiss].
 *
 * @param label Whose PIN is asked for, e.g. a player's username or "Admin".
 * @param verifier Checks a complete PIN and returns whether it is correct. Runs in a coroutine, so
 *   it can hash off the main thread. It must not throw: catch failures inside the lambda, because an
 *   exception escapes uncaught and crashes the app.
 * @param onVerified Called once the verifier accepts a PIN.
 * @param onDismiss Called when the user cancels.
 * @param title Optional action summary shown above the prompt, e.g. "Resolve: Jay wins".
 * @param progress Optional position in a sequence, e.g. "1 of 3".
 * @param onForgotPin When non-null, a "Forgot admin PIN?" button is shown at the start of the
 *   Cancel row and calls this. Used only for the admin PIN.
 */
@Composable
fun PinEntryDialog(
    label: String,
    verifier: suspend (String) -> Boolean,
    onVerified: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    progress: String? = null,
    onForgotPin: (() -> Unit)? = null,
) {
    var pin by remember { mutableStateOf("") }
    var wrongPin by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    fun onPinChange(value: String) {
        if (verifying) return
        val digits = value.filter { it.isDigit() }.take(PIN_LENGTH)
        if (digits.isNotEmpty()) wrongPin = false
        pin = digits
        if (digits.length == PIN_LENGTH) {
            verifying = true
            scope.launch {
                val accepted = verifier(digits)
                pin = ""
                verifying = false
                if (accepted) onVerified() else wrongPin = true
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (progress != null) {
                    Text(
                        text = progress,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = "$label, enter your PIN",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(24.dp))
                PinField(
                    pin = pin,
                    onPinChange = ::onPinChange,
                    enabled = !verifying,
                    modifier = Modifier.focusRequester(focusRequester),
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Reserve the error line so the dialog doesn't jump when it appears.
                Text(
                    text = if (wrongPin) "Wrong PIN" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (onForgotPin != null) {
                        TextButton(onClick = onForgotPin) { Text("Forgot admin PIN?") }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
        // Inside the dialog's own composition, so the field is attached when focus is requested.
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

/** Four boxes that show a dot per entered digit, backed by a numeric password text field. */
@Composable
private fun PinField(
    pin: String,
    onPinChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = pin,
        onValueChange = onPinChange,
        modifier = modifier.testTag(PIN_INPUT_TAG),
        readOnly = !enabled,
        singleLine = true,
        // Password semantics keep accessibility services from reading the digits (§5.5); the
        // decoration box below draws the dots.
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(PIN_LENGTH) { index ->
                    val filled = index < pin.length
                    val active = index == pin.length
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(BetCoinInputBackground, RoundedCornerShape(12.dp))
                            .border(
                                width = 2.dp,
                                color = if (active && enabled) BetCoinTertiary else BetCoinOutline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (filled) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(50)),
                            )
                        }
                    }
                }
            }
        },
    )
}

/**
 * One participant's turn in a [ConsentSequence].
 *
 * @param label Whose PIN is asked for. Also identifies the step, so labels must be stable.
 * @param verifier Same contract as [PinEntryDialog]'s verifier: it must not throw.
 */
data class ConsentStep(
    val label: String,
    val verifier: suspend (String) -> Boolean,
)

/**
 * Tracks progress through a [ConsentSequence]. Holds only the current position, never a PIN.
 */
class ConsentSequenceState(val steps: List<ConsentStep>) {
    init {
        require(steps.isNotEmpty()) { "A consent sequence needs at least one step" }
    }

    private var index by mutableIntStateOf(0)

    /** True once every step has been confirmed. */
    var isComplete by mutableStateOf(false)
        private set

    val currentIndex: Int get() = index
    val currentStep: ConsentStep get() = steps[index]

    /** "n of m" for the current step. */
    val progressText: String get() = "${index + 1} of ${steps.size}"

    /**
     * Marks the current step confirmed and moves to the next one.
     *
     * @return true only on the call that confirms the last step.
     */
    fun confirmCurrent(): Boolean {
        if (isComplete) return false
        if (index == steps.lastIndex) {
            isComplete = true
            return true
        }
        index++
        return false
    }
}

/**
 * Unanimous consent (§4.2): asks each step's PIN in list order with a [PinEntryDialog] showing
 * "n of m". Dismissing any dialog aborts the whole action through [onAbort]. Each participant gets
 * a fresh dialog, so no entered PIN carries over. [onAllConfirmed] is called exactly once, after
 * the last PIN is accepted.
 *
 * Progress is keyed on the step labels, not on [steps] itself, so a list rebuilt on recomposition
 * (with new verifier lambdas) keeps its place and uses the latest verifiers. A change in the
 * labels starts the sequence over.
 *
 * Callers must stop composing [ConsentSequence] in [onAbort] and once [onAllConfirmed] is called.
 * Removing it cancels any verification still in flight.
 *
 * @param steps Participants to confirm, in order. Each verifier must not throw.
 * @param onAllConfirmed Called once every PIN has been accepted.
 * @param onAbort Called if any participant cancels.
 * @param title Optional action summary shown in every dialog, e.g. "Resolve: Jay wins".
 */
@Composable
fun ConsentSequence(
    steps: List<ConsentStep>,
    onAllConfirmed: () -> Unit,
    onAbort: () -> Unit,
    title: String? = null,
) {
    // ConsentStep holds a lambda, so a rebuilt list never equals the old one; key on the labels.
    val state = remember(steps.map { it.label }) { ConsentSequenceState(steps) }
    if (state.isComplete) return

    key(state.currentIndex) {
        val step = steps[state.currentIndex]
        PinEntryDialog(
            label = step.label,
            verifier = step.verifier,
            onVerified = { if (state.confirmCurrent()) onAllConfirmed() },
            onDismiss = onAbort,
            title = title,
            progress = state.progressText,
        )
    }
}
