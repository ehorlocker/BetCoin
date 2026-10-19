package com.betcoin.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.betcoin.data.database.entity.User
import com.betcoin.ui.components.BetCoinAvatar
import com.betcoin.ui.components.PinTextField
import com.betcoin.ui.components.formatCoins
import com.betcoin.ui.players.initialsOf

/** Test tag prefix of a player row; the player id is appended. */
const val ADMIN_PLAYER_ROW_TAG_PREFIX = "admin_player_"

/** Test tag of the "Edit Username" menu item. */
const val ADMIN_PLAYER_MENU_EDIT_USERNAME_TAG = "admin_player_menu_edit_username"

/** Test tag of the "Set Balance" menu item. */
const val ADMIN_PLAYER_MENU_SET_BALANCE_TAG = "admin_player_menu_set_balance"

/** Test tag of the "Reset Balance" menu item. */
const val ADMIN_PLAYER_MENU_RESET_BALANCE_TAG = "admin_player_menu_reset_balance"

/** Test tag of the "Reset PIN" menu item. */
const val ADMIN_PLAYER_MENU_RESET_PIN_TAG = "admin_player_menu_reset_pin"

/** Test tag of the "Delete Player" menu item. */
const val ADMIN_PLAYER_MENU_DELETE_TAG = "admin_player_menu_delete"

/** Test tag of the text field in the Set Balance and Edit Username dialogs. */
const val ADMIN_PLAYER_DIALOG_INPUT_TAG = "admin_player_dialog_input"

/** Test tag of the confirm button of every player dialog. */
const val ADMIN_PLAYER_DIALOG_CONFIRM_TAG = "admin_player_dialog_confirm"

/** Test tag of the sign toggle in the Set Balance dialog. */
const val ADMIN_PLAYER_DIALOG_SIGN_TAG = "admin_player_dialog_sign"

/** Test tag of the cancel button of every player dialog. */
const val ADMIN_PLAYER_DIALOG_CANCEL_TAG = "admin_player_dialog_cancel"

/** Test tag of the "New PIN" field in the Reset PIN dialog. */
const val ADMIN_PLAYER_DIALOG_PIN_TAG = "admin_player_dialog_pin"

/** Test tag of the "Confirm PIN" field in the Reset PIN dialog. */
const val ADMIN_PLAYER_DIALOG_CONFIRM_PIN_TAG = "admin_player_dialog_confirm_pin"

/** Callbacks for the Player Management section and its dialogs. */
data class AdminPlayerActions(
    val onDelete: (User) -> Unit,
    val onResetBalance: (User) -> Unit,
    val onSetBalance: (User) -> Unit,
    val onEditUsername: (User) -> Unit,
    val onResetPin: (User) -> Unit,
    val onInputChanged: (String) -> Unit,
    val onToggleSign: () -> Unit,
    val onConfirmPinChanged: (String) -> Unit,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * Player Management section of the Admin Panel: one row per [players] entry; tapping a row opens
 * a menu with the player actions.
 */
fun LazyListScope.adminPlayerSection(players: List<User>, actions: AdminPlayerActions) {
    item { AdminSectionHeader("Player Management") }
    if (players.isEmpty()) {
        item { AdminPlaceholderLine("No players yet") }
    } else {
        items(players, key = { "player_${it.id}" }) { player -> AdminPlayerRow(player, actions) }
    }
}

@Composable
private fun AdminPlayerRow(player: User, actions: AdminPlayerActions) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Box {
        ListItem(
            leadingContent = { BetCoinAvatar(initials = initialsOf(player.username), size = 40) },
            headlineContent = { Text(player.username) },
            supportingContent = { Text("${formatCoins(player.balance)} BetCoin") },
            modifier = Modifier
                .testTag(ADMIN_PLAYER_ROW_TAG_PREFIX + player.id)
                .clickable(onClickLabel = "Player actions", role = Role.Button) { menuOpen = true },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            PlayerMenuItem("Edit Username", ADMIN_PLAYER_MENU_EDIT_USERNAME_TAG) {
                menuOpen = false
                actions.onEditUsername(player)
            }
            PlayerMenuItem("Set Balance", ADMIN_PLAYER_MENU_SET_BALANCE_TAG) {
                menuOpen = false
                actions.onSetBalance(player)
            }
            PlayerMenuItem("Reset Balance", ADMIN_PLAYER_MENU_RESET_BALANCE_TAG) {
                menuOpen = false
                actions.onResetBalance(player)
            }
            PlayerMenuItem("Reset PIN", ADMIN_PLAYER_MENU_RESET_PIN_TAG) {
                menuOpen = false
                actions.onResetPin(player)
            }
            PlayerMenuItem("Delete Player", ADMIN_PLAYER_MENU_DELETE_TAG) {
                menuOpen = false
                actions.onDelete(player)
            }
        }
    }
}

@Composable
private fun PlayerMenuItem(label: String, tag: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = onClick, modifier = Modifier.testTag(tag))
}

/** The Player Management dialog described by [state]. */
@Composable
fun AdminPlayerDialog(state: PlayerDialogState, actions: AdminPlayerActions) {
    val name = state.user.username
    when (state) {
        is PlayerDialogState.ConfirmDelete -> ConfirmPlayerDialog(
            title = "Delete $name?",
            text = "Their bet history stays, shown under their old name. This can't be undone.",
            confirmLabel = "Delete",
            isWorking = state.isWorking,
            actions = actions,
        )
        is PlayerDialogState.ConfirmResetBalance -> ConfirmPlayerDialog(
            title = "Reset $name's balance?",
            text = "Gives a forced bailout: +1,000 BetCoin, +1 bailout and +1,000 debt.",
            confirmLabel = "Bail out",
            isWorking = state.isWorking,
            actions = actions,
        )
        is PlayerDialogState.SetBalance -> FormPlayerDialog(
            title = "Set $name's balance",
            isSaving = state.isSaving,
            actions = actions,
        ) {
            PlayerTextField(
                value = state.input,
                label = "Balance",
                keyboardType = KeyboardType.Number,
                error = state.error,
                enabled = !state.isSaving,
                onValueChange = actions.onInputChanged,
                onDone = actions.onConfirm,
                trailingContent = {
                    TextButton(
                        onClick = actions.onToggleSign,
                        enabled = !state.isSaving,
                        modifier = Modifier
                            .testTag(ADMIN_PLAYER_DIALOG_SIGN_TAG)
                            .semantics { contentDescription = "Toggle negative" },
                    ) { Text("±") }
                },
            )
        }
        is PlayerDialogState.EditUsername -> FormPlayerDialog(
            title = "Edit username",
            isSaving = state.isSaving,
            actions = actions,
        ) {
            PlayerTextField(
                value = state.input,
                label = "Username",
                keyboardType = KeyboardType.Text,
                error = state.error,
                enabled = !state.isSaving,
                onValueChange = actions.onInputChanged,
                onDone = actions.onConfirm,
            )
        }
        is PlayerDialogState.ResetPin -> FormPlayerDialog(
            title = "Reset $name's PIN",
            isSaving = state.isSaving,
            actions = actions,
        ) {
            PinTextField(
                value = state.pin,
                onValueChange = actions.onInputChanged,
                label = "New PIN",
                isError = state.error != null,
                enabled = !state.isSaving,
                imeAction = ImeAction.Next,
                modifier = Modifier.testTag(ADMIN_PLAYER_DIALOG_PIN_TAG),
            )
            Spacer(modifier = Modifier.height(8.dp))
            PinTextField(
                value = state.confirmPin,
                onValueChange = actions.onConfirmPinChanged,
                label = "Confirm PIN",
                isError = state.error != null,
                enabled = !state.isSaving,
                imeAction = ImeAction.Done,
                onImeAction = actions.onConfirm,
                supportingText = state.error,
                modifier = Modifier.testTag(ADMIN_PLAYER_DIALOG_CONFIRM_PIN_TAG),
            )
        }
    }
}

@Composable
private fun ConfirmPlayerDialog(
    title: String,
    text: String,
    confirmLabel: String,
    isWorking: Boolean,
    actions: AdminPlayerActions,
) {
    AlertDialog(
        onDismissRequest = { if (!isWorking) actions.onDismiss() },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = actions.onConfirm,
                enabled = !isWorking,
                modifier = Modifier.testTag(ADMIN_PLAYER_DIALOG_CONFIRM_TAG),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(
                onClick = actions.onDismiss,
                enabled = !isWorking,
                modifier = Modifier.testTag(ADMIN_PLAYER_DIALOG_CANCEL_TAG),
            ) { Text("Cancel") }
        },
    )
}

@Composable
private fun FormPlayerDialog(
    title: String,
    isSaving: Boolean,
    actions: AdminPlayerActions,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) actions.onDismiss() },
        title = { Text(title) },
        text = { Column { content() } },
        confirmButton = {
            TextButton(
                onClick = actions.onConfirm,
                enabled = !isSaving,
                modifier = Modifier.testTag(ADMIN_PLAYER_DIALOG_CONFIRM_TAG),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(
                onClick = actions.onDismiss,
                enabled = !isSaving,
                modifier = Modifier.testTag(ADMIN_PLAYER_DIALOG_CANCEL_TAG),
            ) { Text("Cancel") }
        },
    )
}

@Composable
private fun PlayerTextField(
    value: String,
    label: String,
    keyboardType: KeyboardType,
    error: String?,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        trailingIcon = trailingContent,
        supportingText = error?.let { message -> { Text(message, color = MaterialTheme.colorScheme.error) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().testTag(ADMIN_PLAYER_DIALOG_INPUT_TAG),
    )
}
