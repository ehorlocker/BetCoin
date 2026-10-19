package com.betcoin.ui.players

import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betcoin.data.avatar.AvatarStore
import com.betcoin.data.avatar.CameraCaptureStore
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.BetCoinException
import com.betcoin.data.repository.UserRepository
import com.betcoin.navigation.Routes
import com.betcoin.ui.common.GENERIC_ERROR_MESSAGE
import com.betcoin.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for the Player Detail screen (§4.2): the player's stats and a PIN-gated bailout (§5.4).
 *
 * Also drives changing or removing the profile picture (BET-20).
 */
@HiltViewModel
class PlayerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val avatarStore: AvatarStore,
    private val cameraCaptureStore: CameraCaptureStore,
) : ViewModel() {

    private val savedState = savedStateHandle
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
            val avatarModel: Any? = null,
            val hasProfilePicture: Boolean = false,
            val showPicturePin: Boolean = false,
            val showPictureOptions: Boolean = false,
            val isUpdatingPicture: Boolean = false,
            /** One-shot: the screen launches TakePicture(uri), then calls [onCameraLaunched]. */
            val launchCamera: Uri? = null,
        ) : UiState
    }

    private data class LocalState(
        val showBailoutPin: Boolean = false,
        val isBailingOut: Boolean = false,
        val message: String? = null,
        val showPicturePin: Boolean = false,
        val showPictureOptions: Boolean = false,
        val isUpdatingPicture: Boolean = false,
        val launchCamera: Uri? = null,
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
                    avatarModel = user.avatarRef?.let { avatarStore.load(it) },
                    hasProfilePicture = user.avatarRef != null,
                    showPicturePin = local.showPicturePin,
                    showPictureOptions = local.showPictureOptions,
                    isUpdatingPicture = local.isUpdatingPicture,
                    launchCamera = local.launchCamera,
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

    fun onChangePictureClicked() {
        if (localState.value.isUpdatingPicture) return
        localState.update { it.copy(showPicturePin = true) }
    }

    fun onPicturePinDismissed() {
        localState.update { it.copy(showPicturePin = false) }
    }

    fun onPicturePinVerified() {
        localState.update { it.copy(showPicturePin = false, showPictureOptions = true) }
    }

    fun onPictureOptionsDismissed() {
        localState.update { it.copy(showPictureOptions = false) }
    }

    fun onGallerySelected() {
        localState.update { it.copy(showPictureOptions = false) }
    }

    /** The gallery picker's result; null means the user backed out. */
    fun onImagePicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch { updatePicture(uri) }
    }

    fun onTakePhotoSelected() {
        localState.update { it.copy(showPictureOptions = false) }
        viewModelScope.launch {
            try {
                val uri = cameraCaptureStore.createCaptureUri()
                savedState[CAPTURE_URI_KEY] = uri
                localState.update { it.copy(launchCamera = uri) }
            } catch (e: IOException) {
                localState.update { it.copy(message = GENERIC_ERROR_MESSAGE) }
            }
        }
    }

    fun onCameraLaunched() {
        localState.update { it.copy(launchCamera = null) }
    }

    /** The camera's result. The capture file is always discarded once the update finishes. */
    fun onPhotoCaptured(success: Boolean) {
        val uri = savedState.get<Uri>(CAPTURE_URI_KEY) ?: return
        savedState[CAPTURE_URI_KEY] = null
        viewModelScope.launch {
            try {
                if (success) updatePicture(uri)
            } finally {
                withContext(NonCancellable) { cameraCaptureStore.discard(uri) }
            }
        }
    }

    fun onRemovePhotoSelected() {
        localState.update { it.copy(showPictureOptions = false) }
        viewModelScope.launch { runPictureChange("Profile picture removed") { userRepository.clearProfilePicture(userId) } }
    }

    private suspend fun updatePicture(uri: Uri) =
        runPictureChange("Profile picture updated") { userRepository.updateProfilePicture(userId, uri) }

    /** Runs [change] with the busy flag set, ignoring the call if another change is running. */
    private suspend fun runPictureChange(successMessage: String, change: suspend () -> Unit) {
        var claimed = false
        localState.update {
            claimed = !it.isUpdatingPicture
            if (claimed) it.copy(isUpdatingPicture = true) else it
        }
        if (!claimed) return
        try {
            val message = try {
                change()
                successMessage
            } catch (e: BetCoinException) {
                e.toUserMessage()
            } catch (e: IOException) {
                GENERIC_ERROR_MESSAGE
            } catch (e: SQLiteException) {
                GENERIC_ERROR_MESSAGE
            }
            localState.update { it.copy(message = message) }
        } finally {
            localState.update { it.copy(isUpdatingPicture = false) }
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
        const val CAPTURE_URI_KEY = "captureUri"
    }
}
