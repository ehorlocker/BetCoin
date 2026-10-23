package com.betcoin.ui.newbet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betcoin.ui.components.BetCoinButton
import com.betcoin.ui.components.BetCoinCard
import com.betcoin.ui.components.BetCoinChip
import com.betcoin.ui.components.BetCoinInput
import com.betcoin.ui.components.ButtonVariant
import com.betcoin.ui.components.ConsentSequence
import com.betcoin.ui.components.ConsentStep
import com.betcoin.ui.components.PinEntryDialog

/** Test tags for the New Bet screen's inputs. */
const val NEW_BET_PROMPT_TAG = "new_bet_prompt"
const val NEW_BET_OUTCOME_TAG_PREFIX = "new_bet_outcome_"
const val NEW_BET_PLAYER_TAG = "new_bet_player"
const val NEW_BET_WAGER_TAG = "new_bet_wager"
const val NEW_BET_ERROR_TAG = "new_bet_error"

/**
 * New Bet flow, Steps A (Create Bet), B (Add Participants) and C (Review & Lock), per §4.2.
 *
 * The top-bar Close button (and System Back on Steps A and B) asks "Discard this bet?" unless
 * Step A is still empty. System Back and the Back button on Step C return to Step B.
 *
 * @param viewModel the [NewBetViewModel] that manages state
 * @param onNavigateBack called once the flow is left (after discarding, if needed)
 * @param onLocked called with the bet's ID once Step C has locked it (the bet is now ACTIVE)
 */
@Composable
fun NewBetScreen(
    viewModel: NewBetViewModel,
    onNavigateBack: () -> Unit = {},
    onLocked: (betId: Long) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = !state.exited) { viewModel.onBackRequested() }

    LaunchedEffect(state.exited) {
        if (state.exited) onNavigateBack()
    }

    LaunchedEffect(state.lockedBetId) {
        state.lockedBetId?.let(onLocked)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "New Bet",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = when (state.step) {
                            NewBetStep.A -> "Step A · Create Bet"
                            NewBetStep.B -> "Step B · Add Participants"
                            NewBetStep.C -> "Step C · Review & Lock"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = viewModel::onCloseRequested) {
                    Text("Close")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.step) {
                NewBetStep.A -> StepA(state, viewModel)
                NewBetStep.B -> StepB(state, viewModel)
                NewBetStep.C -> StepC(state, viewModel)
            }
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(NEW_BET_ERROR_TAG),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    state.pendingJoin?.let { join ->
        val outcome = state.betOutcomes.find { it.id == join.outcomeId }?.outcomeText.orEmpty()
        PinEntryDialog(
            label = join.user.username,
            verifier = viewModel::verifyPin,
            onVerified = viewModel::onPinVerified,
            onDismiss = viewModel::onPinDismissed,
            title = "Join: $outcome for ${join.wager}",
        )
    }

    if (state.confirmingLock) {
        val steps = state.participants.mapNotNull { entry ->
            entry.user?.let { user ->
                ConsentStep(label = user.username, verifier = { pin -> viewModel.verifyParticipantPin(user.id, pin) })
            }
        }
        if (steps.isNotEmpty()) {
            ConsentSequence(
                steps = steps,
                onAllConfirmed = viewModel::onAllPinsConfirmed,
                onAbort = viewModel::onLockAborted,
                title = "Lock bet: ${state.betPrompt}",
            )
        }
    }

    if (state.showDiscardDialog) {
        AlertDialog(
            onDismissRequest = viewModel::onDiscardDismissed,
            title = { Text("Discard this bet?") },
            text = { Text("The bet and everyone added to it will be deleted. No BetCoin has moved.") },
            confirmButton = {
                TextButton(onClick = viewModel::onDiscardConfirmed, enabled = !state.isBusy) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDiscardDismissed) {
                    Text("Keep editing")
                }
            },
        )
    }
}

@Composable
private fun StepA(state: NewBetViewModel.UiState, viewModel: NewBetViewModel) {
    SectionLabel("Prompt")
    BetCoinInput(
        value = state.prompt,
        onValueChange = viewModel::onPromptChanged,
        placeholder = "Who wins the next game of Smash?",
        modifier = Modifier.testTag(NEW_BET_PROMPT_TAG),
    )
    state.promptError?.let { FieldError(it) }

    SectionLabel("Outcomes (${state.outcomes.size} of ${NewBetViewModel.MAX_OUTCOMES})")
    val errors = state.outcomeErrors
    state.outcomes.forEachIndexed { index, outcome ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            BetCoinInput(
                value = outcome,
                onValueChange = { viewModel.onOutcomeChanged(index, it) },
                placeholder = "Outcome ${index + 1}",
                modifier = Modifier
                    .weight(1f)
                    .testTag("$NEW_BET_OUTCOME_TAG_PREFIX$index"),
            )
            if (state.canRemoveOutcome) {
                TextButton(
                    onClick = { viewModel.onRemoveOutcome(index) },
                    modifier = Modifier.semantics { contentDescription = "Remove outcome ${index + 1}" },
                ) {
                    Text("✕")
                }
            }
        }
        errors.getOrNull(index)?.let { FieldError(it) }
    }
    BetCoinButton(
        text = "Add Outcome",
        onClick = viewModel::onAddOutcome,
        enabled = state.canAddOutcome,
        variant = ButtonVariant.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
    BetCoinButton(
        text = "Next",
        onClick = viewModel::onNext,
        enabled = state.canProceedToStepB,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepB(
    state: NewBetViewModel.UiState,
    viewModel: NewBetViewModel,
) {
    Text(
        text = state.betPrompt,
        style = MaterialTheme.typography.titleLarge,
    )

    SectionLabel("Participants (${state.participants.size})")
    if (state.participants.isEmpty()) {
        Text(
            text = "No one has joined yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    state.participants.forEach { entry ->
        val name = entry.user?.username ?: entry.participant.deletedUsername.orEmpty()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${entry.outcome.outcomeText} · ${entry.participant.wagerAmount} BetCoin",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = { viewModel.onRemoveParticipant(entry.participant.id) },
                enabled = !state.isBusy,
                modifier = Modifier.semantics { contentDescription = "Remove $name" },
            ) {
                Text("✕")
            }
        }
    }

    BetCoinCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Add a participant")
            if (state.availableUsers.isEmpty()) {
                Text(
                    text = "Every player has joined.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                PlayerDropdown(state, viewModel::onUserSelected)
                state.selectedUser?.let { user ->
                    Text(
                        text = "Balance: ${user.balance} BetCoin",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(text = "Pick", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.betOutcomes.forEach { outcome ->
                        BetCoinChip(
                            label = outcome.outcomeText,
                            selected = outcome.id == state.selectedOutcomeId,
                            onClick = { viewModel.onOutcomeSelected(outcome.id) },
                        )
                    }
                }
                BetCoinInput(
                    value = state.wager,
                    onValueChange = viewModel::onWagerChanged,
                    placeholder = "Wager",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag(NEW_BET_WAGER_TAG),
                )
                state.wagerError?.let { FieldError(it) }
                BetCoinButton(
                    text = "Add Participant",
                    onClick = viewModel::onAddParticipant,
                    enabled = state.canAddParticipant,
                    variant = ButtonVariant.BettingAction,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (state.participants.size < NewBetViewModel.MIN_PARTICIPANTS) {
        Text(
            text = "Add at least ${NewBetViewModel.MIN_PARTICIPANTS} participants to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    BetCoinButton(
        text = "Continue",
        onClick = viewModel::onContinue,
        enabled = state.canContinue,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StepC(state: NewBetViewModel.UiState, viewModel: NewBetViewModel) {
    Text(
        text = state.betPrompt,
        style = MaterialTheme.typography.titleLarge,
    )

    SectionLabel("Outcomes")
    state.betOutcomes.forEach { outcome ->
        Text(text = outcome.outcomeText, style = MaterialTheme.typography.bodyLarge)
    }

    SectionLabel("Participants (${state.participants.size})")
    state.participants.forEach { entry ->
        val name = entry.user?.username ?: entry.participant.deletedUsername.orEmpty()
        BetCoinCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(text = name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${entry.outcome.outcomeText} · ${entry.participant.wagerAmount} BetCoin",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    val pot = state.participants.sumOf { it.participant.wagerAmount }
    Text(
        text = "Total pot: $pot BetCoin",
        style = MaterialTheme.typography.titleSmall,
    )

    state.lockBlocker?.let { FieldError(it) }
    Text(
        text = "Each participant will re-enter their PIN in this order, then the wagers are deducted.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    BetCoinButton(
        text = "Lock Bet",
        onClick = viewModel::onLockRequested,
        enabled = state.canLock,
        variant = ButtonVariant.BettingAction,
        modifier = Modifier.fillMaxWidth(),
    )
    BetCoinButton(
        text = "Back",
        onClick = viewModel::onBackRequested,
        enabled = !state.isBusy && !state.confirmingLock,
        variant = ButtonVariant.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerDropdown(state: NewBetViewModel.UiState, onSelected: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = state.selectedUser?.username.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Player") },
            placeholder = { Text("Choose a player") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .testTag(NEW_BET_PLAYER_TAG),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // The menu is a popup window, so it doesn't inherit the root's testTagsAsResourceId.
            modifier = Modifier.semantics { testTagsAsResourceId = true },
        ) {
            state.availableUsers.forEach { user ->
                DropdownMenuItem(
                    text = { Text("${user.username} (${user.balance})") },
                    onClick = {
                        onSelected(user.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun FieldError(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
