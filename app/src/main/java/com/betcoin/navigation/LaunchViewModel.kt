package com.betcoin.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.util.ErrorLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Log tag for launch routing. */
const val LAUNCH_LOG_TAG = "BetCoinLaunch"

private const val KEY_LAUNCH_ROUTE = "launch_route"

/** UI state for resolving the start destination at launch. */
sealed interface LaunchUiState {
    data object Loading : LaunchUiState
    data class Ready(val startDestination: String) : LaunchUiState
    data object Error : LaunchUiState
}

/**
 * Resolves the start destination without letting a database failure crash the app.
 *
 * The resolved route is saved in [SavedStateHandle] so it is not re-resolved after process
 * recreation (which could otherwise swap the start destination under a restored back stack).
 */
@HiltViewModel
class LaunchViewModel @Inject constructor(
    private val resolver: StartDestinationResolver,
    private val savedStateHandle: SavedStateHandle,
    private val errorLogger: ErrorLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LaunchUiState>(LaunchUiState.Loading)

    /** Current launch state. */
    val uiState: StateFlow<LaunchUiState> = _uiState.asStateFlow()

    init {
        val saved = savedStateHandle.get<String>(KEY_LAUNCH_ROUTE)
        if (saved != null) {
            _uiState.value = LaunchUiState.Ready(saved)
        } else {
            resolve()
        }
    }

    /** Tries to resolve the route again after a failure. Ignored unless the state is [LaunchUiState.Error]. */
    fun retry() {
        if (!_uiState.compareAndSet(LaunchUiState.Error, LaunchUiState.Loading)) return
        resolve()
    }

    private fun resolve() {
        viewModelScope.launch {
            try {
                val route = resolver.resolve()
                savedStateHandle[KEY_LAUNCH_ROUTE] = route
                _uiState.value = LaunchUiState.Ready(route)
            } catch (e: Exception) {
                // A CancellationException from the repository while this scope is still active is a
                // failure like any other; if the scope itself was cancelled this rethrows instead.
                if (e is CancellationException) currentCoroutineContext().ensureActive()
                errorLogger.logError(LAUNCH_LOG_TAG, "Failed to resolve the start destination", e)
                _uiState.value = LaunchUiState.Error
            }
        }
    }
}
