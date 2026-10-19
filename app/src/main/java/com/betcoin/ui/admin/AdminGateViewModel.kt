package com.betcoin.ui.admin

import androidx.lifecycle.ViewModel
import com.betcoin.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Verifies the admin PIN for the gate shown before the Admin Panel opens.
 *
 * Holds no state: the gate is asked again every time the Manage Players gear is tapped.
 */
@HiltViewModel
class AdminGateViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /**
     * Returns whether [pin] is the admin PIN. Never throws (other than cancellation), because the
     * PIN dialog's verifier must not: any failure counts as a wrong PIN.
     */
    suspend fun verifyPin(pin: String): Boolean =
        try {
            settingsRepository.verifyAdminPin(pin)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
}
