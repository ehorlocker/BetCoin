package com.betcoin.ui.players

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betcoin.data.database.entity.User
import com.betcoin.ui.components.BetCoinAvatar
import com.betcoin.ui.components.BetCoinButton
import com.betcoin.ui.components.BetCoinCard

/**
 * Manage Players screen (§4.2): lists players, adds new ones, and leads to Player Detail.
 *
 * It has no destructive or balance actions: delete and rename live in the Admin Panel, bailout on
 * Player Detail.
 *
 * @param viewModel the [ManagePlayersViewModel] that manages state
 * @param onNavigateBack called when the user wants to go back
 * @param onPlayerClick called with a player's ID when their row is tapped
 * @param onAdminPanel called when the gear icon is tapped (Admin Panel, BET-29)
 */
@Composable
fun ManagePlayersScreen(
    viewModel: ManagePlayersViewModel,
    onNavigateBack: () -> Unit = {},
    onPlayerClick: (Long) -> Unit = {},
    onAdminPanel: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var isNavigatingBack by remember { mutableStateOf(false) }

    BackHandler(enabled = !isNavigatingBack) {
        isNavigatingBack = true
        onNavigateBack()
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onSnackbarShown()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                Text(
                    text = "Manage Players",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onAdminPanel) {
                    Icon(imageVector = Icons.Filled.Settings, contentDescription = "Admin Panel")
                }
                TextButton(onClick = onNavigateBack) {
                    Text("Back")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            when {
                uiState.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                uiState.players.isEmpty() -> EmptyState(modifier = Modifier.weight(1f))
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.players, key = { it.id }) { player ->
                        PlayerRow(
                            player = player,
                            avatar = uiState.avatars[player.id],
                            onClick = { onPlayerClick(player.id) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            BetCoinButton(
                text = "Add Player",
                onClick = viewModel::onAddPlayerClicked,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    uiState.addPlayerForm?.let { form ->
        AddPlayerDialog(
            form = form,
            onUsernameChange = viewModel::onUsernameChanged,
            onPinChange = viewModel::onPinChanged,
            onConfirmPinChange = viewModel::onConfirmPinChanged,
            onConfirm = viewModel::onConfirmAddPlayer,
            onDismiss = viewModel::onDismissAddPlayerDialog,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No players yet. Add one to get started!",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A player's avatar and username. Tapping opens Player Detail. */
@Composable
private fun PlayerRow(player: User, avatar: Any?, onClick: () -> Unit) {
    BetCoinCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BetCoinAvatar(initials = initialsOf(player.username), imageModel = avatar)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = player.username,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun AddPlayerDialog(
    form: ManagePlayersViewModel.AddPlayerForm,
    onUsernameChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onConfirmPinChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Player") },
        text = {
            Column {
                OutlinedTextField(
                    value = form.username,
                    onValueChange = onUsernameChange,
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                PinTextField(value = form.pin, onValueChange = onPinChange, label = "PIN (4 digits)")
                Spacer(modifier = Modifier.height(8.dp))
                PinTextField(value = form.confirmPin, onValueChange = onConfirmPinChange, label = "Confirm PIN")
                if (form.error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = form.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !form.isSaving) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun PinTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Up to two initials for the default avatar: "Jay" → "J", "Mary Jane" → "MJ". */
internal fun initialsOf(username: String): String =
    username.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
