package com.betcoin.devtools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The operations the Dev Tools screen can run; a seam that keeps the ViewModel JVM-testable. */
interface DevToolsActions {
    suspend fun seedSampleData(): String
    suspend fun seedPendingBet()
    suspend fun wipe()
    suspend fun resetAdminPin()
    suspend fun info(): DevDatabaseInfo
}

/** [DevToolsActions] backed by [DevDataSeeder] and [DevDatabaseTools]. */
class DefaultDevToolsActions @Inject constructor(
    private val seeder: DevDataSeeder,
    private val tools: DevDatabaseTools,
) : DevToolsActions {
    override suspend fun seedSampleData(): String = seeder.seedSampleData()
    override suspend fun seedPendingBet() = seeder.seedPendingBet()
    override suspend fun wipe() = tools.wipe()
    override suspend fun resetAdminPin() = tools.resetAdminPin()
    override suspend fun info(): DevDatabaseInfo = tools.info()
}

/** The destructive or seeding actions that need a confirmation dialog. */
enum class DevToolsAction { SEED_SAMPLE_DATA, SEED_PENDING_BET, WIPE, RESET_ADMIN_PIN }

/**
 * UI state of the Dev Tools screen.
 *
 * @property info database info panel content, or `null` until loaded
 * @property pendingConfirmation the action awaiting confirmation, or `null`
 * @property busy whether an action is running (actions are disabled meanwhile)
 * @property recoveryCode recovery code to show once after seeding, or `null`
 * @property message snackbar text, consumed via [DevToolsViewModel.onMessageShown]
 */
data class DevToolsUiState(
    val info: DevDatabaseInfo? = null,
    val pendingConfirmation: DevToolsAction? = null,
    val busy: Boolean = false,
    val recoveryCode: String? = null,
    val message: String? = null,
)

/** One-off events the screen must act on (delivered once). */
sealed interface DevToolsEvent {
    /** Restart the app at Onboarding. */
    data object RestartApp : DevToolsEvent

    /** Close the app. */
    data object CloseApp : DevToolsEvent
}

/** ViewModel for the Dev Tools screen. */
@HiltViewModel
class DevToolsViewModel @Inject constructor(
    private val actions: DevToolsActions,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevToolsUiState())
    val uiState: StateFlow<DevToolsUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<DevToolsEvent>(Channel.BUFFERED)
    val events: Flow<DevToolsEvent> = eventChannel.receiveAsFlow()

    init {
        refreshInfo()
    }

    /** Asks for confirmation of [action]; ignored while another action is running. */
    fun request(action: DevToolsAction) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(pendingConfirmation = action) }
    }

    fun dismissConfirmation() {
        _uiState.update { it.copy(pendingConfirmation = null) }
    }

    /** Runs the action awaiting confirmation. */
    fun confirm() {
        val state = _uiState.value
        val action = state.pendingConfirmation ?: return
        if (state.busy) return
        _uiState.update { it.copy(pendingConfirmation = null, busy = true) }
        viewModelScope.launch { run(action) }
    }

    fun onRecoveryCodeShown() {
        _uiState.update { it.copy(recoveryCode = null) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    /** Reloads the database info panel. */
    fun refreshInfo() {
        viewModelScope.launch { loadInfo() }
    }

    private suspend fun run(action: DevToolsAction) {
        var event: DevToolsEvent? = null
        try {
            when (action) {
                DevToolsAction.SEED_SAMPLE_DATA -> {
                    val code = actions.seedSampleData()
                    _uiState.update { it.copy(recoveryCode = code, message = "Sample data seeded") }
                }
                DevToolsAction.SEED_PENDING_BET -> {
                    actions.seedPendingBet()
                    _uiState.update { it.copy(message = "Pending bet added. Closing app…") }
                    event = DevToolsEvent.CloseApp
                }
                DevToolsAction.WIPE -> {
                    actions.wipe()
                    _uiState.update { it.copy(message = "Dev database wiped. Restarting…") }
                    event = DevToolsEvent.RestartApp
                }
                DevToolsAction.RESET_ADMIN_PIN -> {
                    actions.resetAdminPin()
                    _uiState.update {
                        it.copy(message = "Admin PIN cleared. Next launch opens Onboarding")
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            event = null
            _uiState.update { it.copy(message = "Couldn't ${action.failureLabel()}: ${e.message}") }
        }
        try {
            loadInfo()
            if (event != null) {
                delay(RESTART_DELAY_MS)
                eventChannel.send(event)
            }
        } finally {
            _uiState.update { it.copy(busy = false) }
        }
    }

    private suspend fun loadInfo() {
        try {
            val info = actions.info()
            _uiState.update { it.copy(info = info) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep the previous info; the action's own message is more useful to show.
        }
    }

    private fun DevToolsAction.failureLabel(): String = when (this) {
        DevToolsAction.SEED_SAMPLE_DATA -> "seed sample data"
        DevToolsAction.SEED_PENDING_BET -> "seed pending bet"
        DevToolsAction.WIPE -> "wipe the dev database"
        DevToolsAction.RESET_ADMIN_PIN -> "reset the admin PIN"
    }

    private companion object {
        /** Delay so the user sees the snackbar before the app restarts or closes. */
        const val RESTART_DELAY_MS = 1_500L
    }
}
