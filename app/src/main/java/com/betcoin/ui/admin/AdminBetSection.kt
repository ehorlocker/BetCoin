package com.betcoin.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.BetWithDetails
import com.betcoin.ui.components.formatCoins

/** Test tag prefix of a bet row; the bet id is appended. */
const val ADMIN_BET_ROW_TAG_PREFIX = "admin_bet_"

/** Test tag of the "Force Cancel" menu item. */
const val ADMIN_BET_MENU_FORCE_CANCEL_TAG = "admin_bet_menu_force_cancel"

/** Test tag of the "Admin Resolve" menu item. */
const val ADMIN_BET_MENU_RESOLVE_TAG = "admin_bet_menu_resolve"

/** Test tag of the "Edit Prompt" menu item. */
const val ADMIN_BET_MENU_EDIT_PROMPT_TAG = "admin_bet_menu_edit_prompt"

/** Test tag of the "Remove Participant" menu item. */
const val ADMIN_BET_MENU_REMOVE_PARTICIPANT_TAG = "admin_bet_menu_remove_participant"

/** Test tag of the "Edit Wager" menu item. */
const val ADMIN_BET_MENU_EDIT_WAGER_TAG = "admin_bet_menu_edit_wager"

/** Test tag of the "Delete Bet" menu item. */
const val ADMIN_BET_MENU_DELETE_TAG = "admin_bet_menu_delete"

/** Test tag of the text field in the Edit Prompt and Edit Wager dialogs. */
const val ADMIN_BET_DIALOG_INPUT_TAG = "admin_bet_dialog_input"

/** Test tag of the confirm button of every bet dialog. */
const val ADMIN_BET_DIALOG_CONFIRM_TAG = "admin_bet_dialog_confirm"

/** Test tag of the cancel button of every bet dialog. */
const val ADMIN_BET_DIALOG_CANCEL_TAG = "admin_bet_dialog_cancel"

/** Test tag prefix of a radio option (outcome or participant); the outcome or participant id is appended. */
const val ADMIN_BET_OPTION_TAG_PREFIX = "admin_bet_option_"

/** Callbacks for the Bet Management section and its dialogs. */
data class AdminBetActions(
    val onForceCancel: (BetWithDetails) -> Unit,
    val onResolve: (BetWithDetails) -> Unit,
    val onEditPrompt: (BetWithDetails) -> Unit,
    val onRemoveParticipant: (BetWithDetails) -> Unit,
    val onEditWager: (BetWithDetails) -> Unit,
    val onDelete: (BetWithDetails) -> Unit,
    val onOutcomeSelected: (Long) -> Unit,
    val onParticipantSelected: (Long) -> Unit,
    val onInputChanged: (String) -> Unit,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * Bet Management section of the Admin Panel: one row per entry of [bets]; tapping a row opens a
 * menu with the actions valid for the bet's status.
 */
fun LazyListScope.adminBetSection(bets: List<BetWithDetails>, actions: AdminBetActions) {
    item { AdminSectionHeader("Bet Management") }
    if (bets.isEmpty()) {
        item { AdminPlaceholderLine("No bets yet") }
    } else {
        items(bets, key = { "bet_${it.bet.id}" }) { bet -> AdminBetRow(bet, actions) }
    }
}

@Composable
private fun AdminBetRow(details: BetWithDetails, actions: AdminBetActions) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val pot = details.participants.sumOf { it.participant.wagerAmount }
    Box {
        ListItem(
            headlineContent = { Text(details.bet.prompt) },
            supportingContent = {
                Text("${statusLabel(details.bet.status)} · ${details.participants.size} players · ${formatCoins(pot)} BetCoin pot")
            },
            modifier = Modifier
                .testTag(ADMIN_BET_ROW_TAG_PREFIX + details.bet.id)
                .clickable(onClickLabel = "Bet actions", role = Role.Button) { menuOpen = true },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (details.bet.status == BetStatus.ACTIVE) {
                BetMenuItem("Admin Resolve", ADMIN_BET_MENU_RESOLVE_TAG) {
                    menuOpen = false
                    actions.onResolve(details)
                }
                BetMenuItem("Edit Prompt", ADMIN_BET_MENU_EDIT_PROMPT_TAG) {
                    menuOpen = false
                    actions.onEditPrompt(details)
                }
                BetMenuItem("Edit Wager", ADMIN_BET_MENU_EDIT_WAGER_TAG) {
                    menuOpen = false
                    actions.onEditWager(details)
                }
                BetMenuItem("Remove Participant", ADMIN_BET_MENU_REMOVE_PARTICIPANT_TAG) {
                    menuOpen = false
                    actions.onRemoveParticipant(details)
                }
                BetMenuItem("Force Cancel", ADMIN_BET_MENU_FORCE_CANCEL_TAG) {
                    menuOpen = false
                    actions.onForceCancel(details)
                }
            } else {
                BetMenuItem("Edit Prompt", ADMIN_BET_MENU_EDIT_PROMPT_TAG) {
                    menuOpen = false
                    actions.onEditPrompt(details)
                }
                BetMenuItem("Delete Bet", ADMIN_BET_MENU_DELETE_TAG) {
                    menuOpen = false
                    actions.onDelete(details)
                }
            }
        }
    }
}

@Composable
private fun BetMenuItem(label: String, tag: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = onClick, modifier = Modifier.testTag(tag))
}

private fun statusLabel(status: BetStatus): String = when (status) {
    BetStatus.ACTIVE -> "Active"
    BetStatus.RESOLVED -> "Resolved"
    BetStatus.CANCELLED -> "Cancelled"
    BetStatus.PENDING_CONFIRMATION -> "Pending"
}

/** The Bet Management dialog described by [dialog]. */
@Composable
fun AdminBetDialog(dialog: BetDialogState, actions: AdminBetActions) {
    val prompt = dialog.bet.bet.prompt
    when (dialog) {
        is BetDialogState.ConfirmForceCancel -> ConfirmBetDialog(
            title = "Force cancel bet?",
            text = "\"$prompt\" will be cancelled and every wager refunded. No participant consent is needed.",
            confirmLabel = "Force Cancel",
            isBusy = dialog.isWorking,
            actions = actions,
        )
        is BetDialogState.ConfirmDelete -> ConfirmBetDialog(
            title = "Delete bet?",
            text = "\"$prompt\" and all its outcomes and participants will be permanently removed from " +
                "history. Balances and stats don't change.",
            confirmLabel = "Delete",
            isBusy = dialog.isWorking,
            actions = actions,
        )
        is BetDialogState.Resolve -> FormBetDialog(
            title = "Admin resolve",
            confirmLabel = "Resolve",
            confirmEnabled = dialog.selectedOutcomeId != null,
            isBusy = dialog.isSaving,
            actions = actions,
        ) {
            dialog.bet.outcomes.forEach { outcome ->
                val picked = dialog.bet.participants.count { it.participant.outcomeId == outcome.id }
                BetOptionRow(
                    label = "${outcome.outcomeText} ($picked picked)",
                    id = outcome.id,
                    selected = dialog.selectedOutcomeId == outcome.id,
                    enabled = !dialog.isSaving,
                    onSelected = actions.onOutcomeSelected,
                )
            }
            BetDialogError(dialog.error)
        }
        is BetDialogState.EditPrompt -> FormBetDialog(
            title = "Edit prompt",
            confirmLabel = "Save",
            isBusy = dialog.isSaving,
            actions = actions,
        ) {
            BetTextField(
                value = dialog.input,
                label = "Prompt",
                keyboardType = KeyboardType.Text,
                singleLine = false,
                error = dialog.error,
                enabled = !dialog.isSaving,
                actions = actions,
            )
        }
        is BetDialogState.RemoveParticipant -> FormBetDialog(
            title = "Remove participant",
            confirmLabel = "Remove",
            isBusy = dialog.isSaving,
            actions = actions,
        ) {
            ParticipantOptions(dialog.bet, dialog.selectedParticipantId, !dialog.isSaving, actions)
            Text(
                "Their wager is refunded.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            BetDialogError(dialog.error)
        }
        is BetDialogState.EditWager -> FormBetDialog(
            title = "Edit wager",
            confirmLabel = "Save",
            isBusy = dialog.isSaving,
            actions = actions,
        ) {
            ParticipantOptions(dialog.bet, dialog.selectedParticipantId, !dialog.isSaving, actions)
            if (dialog.selectedParticipantId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                BetTextField(
                    value = dialog.input,
                    label = "Wager",
                    keyboardType = KeyboardType.Number,
                    singleLine = true,
                    error = dialog.error,
                    enabled = !dialog.isSaving,
                    actions = actions,
                )
            } else {
                BetDialogError(dialog.error)
            }
        }
    }
}

@Composable
private fun ParticipantOptions(
    bet: BetWithDetails,
    selectedId: Long?,
    enabled: Boolean,
    actions: AdminBetActions,
) {
    bet.participants.forEach { p ->
        BetOptionRow(
            label = "${adminParticipantName(p)} · ${p.outcome.outcomeText} · ${formatCoins(p.participant.wagerAmount)}",
            id = p.participant.id,
            selected = selectedId == p.participant.id,
            enabled = enabled,
            onSelected = actions.onParticipantSelected,
        )
    }
}

@Composable
private fun BetOptionRow(label: String, id: Long, selected: Boolean, enabled: Boolean, onSelected: (Long) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ADMIN_BET_OPTION_TAG_PREFIX + id)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) { onSelected(id) }
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun BetDialogError(error: String?) {
    if (error != null) {
        Text(
            error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ConfirmBetDialog(
    title: String,
    text: String,
    confirmLabel: String,
    isBusy: Boolean,
    actions: AdminBetActions,
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) actions.onDismiss() },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = actions.onConfirm,
                enabled = !isBusy,
                modifier = Modifier.testTag(ADMIN_BET_DIALOG_CONFIRM_TAG),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(
                onClick = actions.onDismiss,
                enabled = !isBusy,
                modifier = Modifier.testTag(ADMIN_BET_DIALOG_CANCEL_TAG),
            ) { Text("Cancel") }
        },
    )
}

@Composable
private fun FormBetDialog(
    title: String,
    confirmLabel: String,
    isBusy: Boolean,
    actions: AdminBetActions,
    confirmEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) actions.onDismiss() },
        title = { Text(title) },
        text = { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { content() } },
        confirmButton = {
            TextButton(
                onClick = actions.onConfirm,
                enabled = confirmEnabled && !isBusy,
                modifier = Modifier.testTag(ADMIN_BET_DIALOG_CONFIRM_TAG),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(
                onClick = actions.onDismiss,
                enabled = !isBusy,
                modifier = Modifier.testTag(ADMIN_BET_DIALOG_CANCEL_TAG),
            ) { Text("Cancel") }
        },
    )
}

@Composable
private fun BetTextField(
    value: String,
    label: String,
    keyboardType: KeyboardType,
    singleLine: Boolean,
    error: String?,
    enabled: Boolean,
    actions: AdminBetActions,
) {
    OutlinedTextField(
        value = value,
        onValueChange = actions.onInputChanged,
        label = { Text(label) },
        singleLine = singleLine,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { message -> { Text(message, color = MaterialTheme.colorScheme.error) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onDone = { actions.onConfirm() }),
        modifier = Modifier.fillMaxWidth().testTag(ADMIN_BET_DIALOG_INPUT_TAG),
    )
}
