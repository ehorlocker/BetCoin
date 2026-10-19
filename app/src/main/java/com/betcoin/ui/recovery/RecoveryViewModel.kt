package com.betcoin.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.model.BetCoinException
import com.betcoin.data.repository.BetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the Recovery screen shown when a bet was interrupted mid-creation. */
sealed interface RecoveryUiState {
    data object Loading : RecoveryUiState

    data class Pending(
        val betId: Long,
        val prompt: String,
        val participantCount: Int,
        val isDiscarding: Boolean = false,
    ) : RecoveryUiState

    /** Discarded, or there is no pending bet any more. Terminal. */
    data object GoHome : RecoveryUiState
}

/**
 * ViewModel for the Recovery screen.
 *
 * Tracks the newest pending bet and lets the user discard it. If the pending bet disappears
 * (or discarding fails because it is no longer pending) the state becomes [RecoveryUiState.GoHome].
 */
@HiltViewModel
class RecoveryViewModel @Inject constructor(
    private val betRepository: BetRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecoveryUiState>(RecoveryUiState.Loading)
    val uiState: StateFlow<RecoveryUiState> = _uiState

    init {
        viewModelScope.launch {
            betRepository.getPendingBet().collectLatest { details ->
                _uiState.update { current ->
                    when {
                        current is RecoveryUiState.GoHome -> current
                        details == null -> RecoveryUiState.GoHome
                        else -> RecoveryUiState.Pending(
                            betId = details.bet.id,
                            prompt = details.bet.prompt,
                            participantCount = details.participants.size,
                            isDiscarding = (current as? RecoveryUiState.Pending)?.isDiscarding == true,
                        )
                    }
                }
            }
        }
    }

    /** Discards the pending bet and goes Home. Ignored while a discard is already running. */
    fun discard() {
        val current = _uiState.value as? RecoveryUiState.Pending ?: return
        if (current.isDiscarding) return
        _uiState.value = current.copy(isDiscarding = true)
        viewModelScope.launch {
            try {
                betRepository.discardPendingBet(current.betId)
            } catch (_: BetCoinException) {
                // No longer pending: the goal state (no pending bet) is reached either way.
            }
            _uiState.value = RecoveryUiState.GoHome
        }
    }
}
