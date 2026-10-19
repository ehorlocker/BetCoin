package com.betcoin.ui.players

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.BetCoinException
import com.betcoin.data.repository.UserRepository
import com.betcoin.navigation.Routes
import com.betcoin.ui.common.GENERIC_ERROR_MESSAGE
import com.betcoin.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Player Detail screen (§4.2): the player's stats and a PIN-gated bailout (§5.4).
 *
 * Profile picture changes are BET-20.
 */
@HiltViewModel
class PlayerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val userId: Long = checkNotNull(savedStateHandle[Routes.PLAYER_ID_ARG])

    /** Everything Player Detail shows about one player. */
    data class PlayerStats(
        val username: String,
        val balance: Long,
        val totalWins: Int,
        val totalLosses: Int,
        val totalEarnings: Long,
        val totalLost: Long,
        val bailoutCount: Int,
        val totalDebt: Long,
        val lockedBalance: Long,
    ) {
        /** `balance − totalDebt`, the leaderboard ranking (§4.2 Leaderboard). */
        val netWorth: Long get() = balance - totalDebt

        /** §5.4: eligible while balance plus coins locked in ACTIVE bets is below 100. */
        val isEligibleForBailout: Boolean get() = balance + lockedBalance < BAILOUT_THRESHOLD
    }

    sealed interface UiState {
        data object Loading : UiState

        /** The player doesn't exist, e.g. deleted while this screen was in the back stack. */
        data object NotFound : UiState

        data class Loaded(
            val stats: PlayerStats,
            val showBailoutPin: Boolean = false,
            val isBailingOut: Boolean = false,
            val message: String? = null,
        ) : UiState
    }

    private data class LocalState(
        val showBailoutPin: Boolean = false,
        val isBailingOut: Boolean = false,
        val message: String? = null,
    )

    private val localState = MutableStateFlow(LocalState())

    private val user = userRepository.getAllUsers().map { users -> users.find { it.id == userId } }

    val uiState: StateFlow<UiState> =
        combine(user, userRepository.getLockedBalance(userId), localState) { user, locked, local ->
            if (user == null) {
                UiState.NotFound
            } else {
                UiState.Loaded(
                    stats = user.toStats(locked),
                    showBailoutPin = local.showBailoutPin,
                    isBailingOut = local.isBailingOut,
                    message = local.message,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun onBailoutClicked() {
        val state = uiState.value as? UiState.Loaded ?: return
        if (!state.stats.isEligibleForBailout || state.isBailingOut) return
        localState.update { it.copy(showBailoutPin = true) }
    }

    fun onBailoutPinDismissed() {
        localState.update { it.copy(showBailoutPin = false) }
    }

    /** PIN verifier for the bailout dialog. Never throws, per the PinEntryDialog contract. */
    suspend fun verifyPin(pin: String): Boolean =
        try {
            userRepository.verifyPin(userId, pin)
        } catch (e: BetCoinException) {
            false
        } catch (e: IOException) {
            false
        } catch (e: SQLiteException) {
            false
        }

    /** Called once the player's PIN is accepted. */
    fun onBailoutPinVerified() {
        localState.update { it.copy(showBailoutPin = false, isBailingOut = true) }
        viewModelScope.launch {
            val message = try {
                userRepository.bailout(userId)
                "Bailout granted: +1,000 BetCoin"
            } catch (e: BetCoinException) {
                e.toUserMessage()
            } catch (e: IOException) {
                GENERIC_ERROR_MESSAGE
            } catch (e: SQLiteException) {
                GENERIC_ERROR_MESSAGE
            }
            localState.update { it.copy(isBailingOut = false, message = message) }
        }
    }

    fun onMessageShown() {
        localState.update { it.copy(message = null) }
    }

    private fun User.toStats(lockedBalance: Long) = PlayerStats(
        username = username,
        balance = balance,
        totalWins = totalWins,
        totalLosses = totalLosses,
        totalEarnings = totalEarnings,
        totalLost = totalLost,
        bailoutCount = bailoutCount,
        totalDebt = totalDebt,
        lockedBalance = lockedBalance,
    )

    private companion object {
        /** §5.4 bailout threshold. The repository enforces it; this only decides button visibility. */
        const val BAILOUT_THRESHOLD = 100L
    }
}
