package com.betcoin.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.model.BetCoinException
import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.repository.BetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
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

    private data class LocalState(val isDiscarding: Boolean, val discarded: Boolean)

    private val localState = MutableStateFlow(LocalState(isDiscarding = false, discarded = false))

    /** Current Recovery state. [RecoveryUiState.GoHome] is terminal for as long as this flow stays active. */
    val uiState: StateFlow<RecoveryUiState> =
        combine(betRepository.getPendingBet(), localState) { details, local -> details to local }
            .runningFold<Pair<BetWithDetails?, LocalState>, RecoveryUiState>(RecoveryUiState.Loading) { previous, (details, local) ->
                when {
                    previous is RecoveryUiState.GoHome -> previous
                    local.discarded || details == null -> RecoveryUiState.GoHome
                    else -> RecoveryUiState.Pending(
                        betId = details.bet.id,
                        prompt = details.bet.prompt,
                        participantCount = details.participants.size,
                        isDiscarding = local.isDiscarding,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecoveryUiState.Loading)

    /** Discards the pending bet and goes Home. Ignored while a discard is already running. */
    fun discard() {
        val pending = uiState.value as? RecoveryUiState.Pending ?: return
        if (localState.getAndUpdate { it.copy(isDiscarding = true) }.isDiscarding) return
        viewModelScope.launch {
            try {
                betRepository.discardPendingBet(pending.betId)
            } catch (_: BetCoinException) {
                // No longer pending: the goal state (no pending bet) is reached either way.
            }
            localState.update { it.copy(discarded = true) }
        }
    }
}
