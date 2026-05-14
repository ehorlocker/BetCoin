package com.betcoin.ui.players

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betcoin.ui.components.BetCoinButton

/**
 * Manage Players screen — lists all players and provides management actions.
 *
 * @param viewModel the [ManagePlayersViewModel] that manages state
 * @param onNavigateBack callback invoked when the user wants to go back
 */
@Composable
fun ManagePlayersScreen(
    viewModel: ManagePlayersViewModel,
    onNavigateBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onSnackbarShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Manage Players",
                    style = MaterialTheme.typography.headlineMedium,
                )
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
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.players.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.players) { player ->
                        PlayerCard(
                            username = player.username,
                            balance = player.balance,
                            wins = player.totalWins,
                            losses = player.totalLosses,
                            onDelete = { viewModel.onDeletePlayerClicked(player.id) },
                            onRename = { viewModel.onRenamePlayerClicked(player.id) },
                            onBailout = { viewModel.onBailoutPlayer(player.id) },
                            onResetPin = { /* TODO */ },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            BetCoinButton(
                text = "Add Player",
                onClick = { viewModel.onAddPlayerClicked() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (uiState.showAddPlayerDialog) {
        AddPlayerDialog(
            username = uiState.newUsername,
            pin = uiState.newPin,
            error = uiState.addPlayerError,
            onUsernameChange = viewModel::onUsernameChanged,
            onPinChange = viewModel::onPinChanged,
            onConfirm = viewModel::onConfirmAddPlayer,
            onDismiss = { viewModel.onDismissAddPlayerDialog() },
        )
    }

    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissDeleteConfirmation() },
            title = { Text("Delete Player") },
            text = { Text("Are you sure you want to delete this player?") },
            confirmButton = {
                TextButton(onClick = { viewModel.onConfirmDeletePlayer() }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissDeleteConfirmation() }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (uiState.showRenameDialog) {
        RenamePlayerDialog(
            username = uiState.renameUsername,
            error = uiState.renameError,
            onUsernameChange = viewModel::onRenameUsernameChanged,
            onConfirm = viewModel::onConfirmRenamePlayer,
            onDismiss = { viewModel.onDismissRenameDialog() },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No players yet. Add one to get started!",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlayerCard(
    username: String,
    balance: Long,
    wins: Int,
    losses: Int,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onBailout: () -> Unit,
    onResetPin: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = username,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Balance: $balance  |  Wins: $wins  |  Losses: $losses",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onRename, modifier = Modifier.weight(1f)) {
                    Text("Rename")
                }
                TextButton(onClick = onResetPin, modifier = Modifier.weight(1f)) {
                    Text("Edit PIN")
                }
                TextButton(onClick = onBailout, modifier = Modifier.weight(1f)) {
                    Text("Bailout")
                }
                TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun AddPlayerDialog(
    username: String,
    pin: String,
    error: String?,
    onUsernameChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Player") },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = onPinChange,
                    label = { Text("PIN (4 digits)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
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
private fun RenamePlayerDialog(
    username: String,
    error: String?,
    onUsernameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Player") },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text("New Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
