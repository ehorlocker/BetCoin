package com.betcoin.ui.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.database.entity.User
import com.betcoin.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Manage Players screen.
 */
@HiltViewModel
class ManagePlayersViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val players: List<User> = emptyList(),
        val showAddPlayerDialog: Boolean = false,
        val newUsername: String = "",
        val newPin: String = "",
        val addPlayerError: String? = null,
        val showDeleteConfirmation: Boolean = false,
        val selectedPlayerId: Long? = null,
        val showRenameDialog: Boolean = false,
        val renameUsername: String = "",
        val renameError: String? = null,
        val snackbarMessage: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        viewModelScope.launch {
            userRepository.getAllUsers().collectLatest { users ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    players = users,
                )
            }
        }
    }

    fun onAddPlayerClicked() {
        _uiState.value = _uiState.value.copy(
            showAddPlayerDialog = true,
            newUsername = "",
            newPin = "",
            addPlayerError = null,
        )
    }

    fun onUsernameChanged(username: String) {
        _uiState.value = _uiState.value.copy(newUsername = username)
    }

    fun onPinChanged(pin: String) {
        _uiState.value = _uiState.value.copy(newPin = pin)
    }

    fun onConfirmAddPlayer() {
        val currentState = _uiState.value
        val username = currentState.newUsername.trim()
        val pin = currentState.newPin

        val error = when {
            username.isEmpty() -> "Username cannot be empty"
            !pin.matches(Regex("^\\d{4}$")) -> "PIN must be exactly 4 digits"
            currentState.players.any { it.username == username } -> "Username already exists"
            else -> null
        }

        if (error != null) {
            _uiState.value = currentState.copy(addPlayerError = error)
            return
        }

        viewModelScope.launch {
            userRepository.createUser(username, pin)
            _uiState.value = currentState.copy(
                showAddPlayerDialog = false,
                addPlayerError = null,
                snackbarMessage = "Player $username created successfully",
            )
        }
    }

    fun onDeletePlayerClicked(playerId: Long) {
        _uiState.value = _uiState.value.copy(
            showDeleteConfirmation = true,
            selectedPlayerId = playerId,
        )
    }

    fun onConfirmDeletePlayer() {
        val playerId = _uiState.value.selectedPlayerId ?: return
        viewModelScope.launch {
            try {
                userRepository.deleteUser(playerId)
                _uiState.value = _uiState.value.copy(
                    showDeleteConfirmation = false,
                    selectedPlayerId = null,
                    snackbarMessage = "Player deleted",
                )
            } catch (e: IllegalStateException) {
                _uiState.value = _uiState.value.copy(
                    showDeleteConfirmation = false,
                    selectedPlayerId = null,
                    snackbarMessage = "Cannot delete: ${e.message}",
                )
            }
        }
    }

    fun onRenamePlayerClicked(playerId: Long) {
        val player = _uiState.value.players.find { it.id == playerId }
        _uiState.value = _uiState.value.copy(
            showRenameDialog = true,
            selectedPlayerId = playerId,
            renameUsername = player?.username ?: "",
            renameError = null,
        )
    }

    fun onRenameUsernameChanged(username: String) {
        _uiState.value = _uiState.value.copy(renameUsername = username)
    }

    fun onConfirmRenamePlayer() {
        val currentState = _uiState.value
        val playerId = currentState.selectedPlayerId ?: return
        val newUsername = currentState.renameUsername.trim()

        val error = when {
            newUsername.isEmpty() -> "Username cannot be empty"
            currentState.players.any { it.id != playerId && it.username == newUsername } -> "Username already exists"
            else -> null
        }

        if (error != null) {
            _uiState.value = currentState.copy(renameError = error)
            return
        }

        viewModelScope.launch {
            try {
                userRepository.updateUsername(playerId, newUsername)
                _uiState.value = currentState.copy(
                    showRenameDialog = false,
                    selectedPlayerId = null,
                    renameError = null,
                    snackbarMessage = "Player renamed to $newUsername",
                )
            } catch (e: Exception) {
                _uiState.value = currentState.copy(
                    renameError = e.message ?: "Rename failed",
                )
            }
        }
    }

    fun onBailoutPlayer(playerId: Long) {
        val player = _uiState.value.players.find { it.id == playerId }
        viewModelScope.launch {
            try {
                userRepository.bailout(playerId)
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = "Bailout granted to ${player?.username}",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = "Failed to grant bailout",
                )
            }
        }
    }

    fun onSnackbarShown() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    fun onDismissAddPlayerDialog() {
        _uiState.value = _uiState.value.copy(showAddPlayerDialog = false)
    }

    fun onDismissDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = false, selectedPlayerId = null)
    }

    fun onDismissRenameDialog() {
        _uiState.value = _uiState.value.copy(showRenameDialog = false, selectedPlayerId = null, renameError = null)
    }
}
