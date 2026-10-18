package com.betcoin.ui.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.BetCoinException
import com.betcoin.data.model.DuplicateUsernameException
import com.betcoin.data.repository.UserRepository
import com.betcoin.data.security.PinHasher
import com.betcoin.ui.components.PIN_LENGTH
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Manage Players screen (§4.2).
 *
 * Lists players and creates new ones. Destructive and balance actions live elsewhere: delete and
 * rename in the Admin Panel, bailout on Player Detail.
 */
@HiltViewModel
class ManagePlayersViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    /** State of the Add Player dialog. The PIN fields are only held while the dialog is open. */
    data class AddPlayerForm(
        val username: String = "",
        val pin: String = "",
        val confirmPin: String = "",
        val error: String? = null,
        val isSaving: Boolean = false,
    )

    data class UiState(
        val isLoading: Boolean = true,
        val players: List<User> = emptyList(),
        /** Non-null while the Add Player dialog is open. */
        val addPlayerForm: AddPlayerForm? = null,
        val snackbarMessage: String? = null,
    )

    private data class LocalState(
        val addPlayerForm: AddPlayerForm? = null,
        val snackbarMessage: String? = null,
    )

    private val localState = MutableStateFlow(LocalState())

    val uiState: StateFlow<UiState> =
        combine(userRepository.getAllUsers(), localState) { users, local ->
            UiState(
                isLoading = false,
                players = users,
                addPlayerForm = local.addPlayerForm,
                snackbarMessage = local.snackbarMessage,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun onAddPlayerClicked() {
        localState.update { it.copy(addPlayerForm = AddPlayerForm()) }
    }

    fun onUsernameChanged(username: String) {
        updateForm { it.copy(username = username, error = null) }
    }

    fun onPinChanged(pin: String) {
        updateForm { it.copy(pin = pin.filter(Char::isDigit).take(PIN_LENGTH), error = null) }
    }

    fun onConfirmPinChanged(pin: String) {
        updateForm { it.copy(confirmPin = pin.filter(Char::isDigit).take(PIN_LENGTH), error = null) }
    }

    fun onDismissAddPlayerDialog() {
        localState.update { it.copy(addPlayerForm = null) }
    }

    fun onConfirmAddPlayer() {
        val form = localState.value.addPlayerForm ?: return
        if (form.isSaving) return
        val username = form.username.trim()
        val error = validate(username, form.pin, form.confirmPin)
        if (error != null) {
            updateForm { it.copy(error = error) }
            return
        }

        updateForm { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                userRepository.createUser(username, form.pin)
                localState.update {
                    it.copy(addPlayerForm = null, snackbarMessage = "Player $username created")
                }
            } catch (e: BetCoinException) {
                updateForm { it.copy(isSaving = false, error = messageFor(e)) }
            }
        }
    }

    fun onSnackbarShown() {
        localState.update { it.copy(snackbarMessage = null) }
    }

    /** Applies [transform] to the open Add Player form; does nothing once it's closed. */
    private fun updateForm(transform: (AddPlayerForm) -> AddPlayerForm) {
        localState.update { state ->
            state.addPlayerForm?.let { state.copy(addPlayerForm = transform(it)) } ?: state
        }
    }

    /** §10 username and PIN rules. Uniqueness is checked by the repository. */
    private fun validate(username: String, pin: String, confirmPin: String): String? = when {
        username.isEmpty() -> "Username cannot be empty"
        username.length > MAX_USERNAME_LENGTH -> "Username must be at most $MAX_USERNAME_LENGTH characters"
        !PinHasher.isValidPin(pin) -> "PIN must be exactly 4 digits"
        pin != confirmPin -> "PINs don't match"
        else -> null
    }

    private fun messageFor(e: BetCoinException): String = when (e) {
        is DuplicateUsernameException -> "Username already exists"
        else -> e.message ?: "Couldn't create player"
    }

    private companion object {
        const val MAX_USERNAME_LENGTH = 20
    }
}
