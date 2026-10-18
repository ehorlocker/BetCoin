package com.betcoin.data.repository

import com.betcoin.data.model.ThemePreference
import kotlinx.coroutines.flow.Flow

/**
 * Contract for application settings operations.
 *
 * Implementations handle admin PIN management, the admin recovery code, first-launch
 * detection and the theme preference.
 */
interface SettingsRepository {

    /**
     * Returns the stored admin PIN hash, or `null` if no PIN has been set.
     */
    suspend fun getAdminPinHash(): String?

    /**
     * Hashes and stores the admin PIN.
     *
     * @throws com.betcoin.data.model.InvalidInputException if [pin] isn't exactly 4 digits.
     */
    suspend fun setAdminPin(pin: String)

    /**
     * Verifies [pin] against the stored admin PIN hash.
     *
     * @return `true` if the PIN matches.
     */
    suspend fun verifyAdminPin(pin: String): Boolean

    /**
     * Returns `true` if no admin PIN has been set (first launch).
     */
    suspend fun isFirstLaunch(): Boolean

    /**
     * Generates a new recovery code, stores only its hash, and returns the plain code
     * (formatted `XXXX-XXXX-XXXX`) once for display. Any previous code stops working.
     *
     * @throws IllegalStateException if the admin PIN hasn't been set yet.
     */
    suspend fun regenerateRecoveryCode(): String

    /**
     * Verifies [code] (case-insensitive, dashes optional) against the stored recovery code
     * hash. If it matches, sets [newPin] as the admin PIN and returns a freshly generated
     * recovery code; the old code stops working.
     *
     * @throws com.betcoin.data.model.InvalidInputException if [newPin] isn't exactly 4 digits.
     * @throws com.betcoin.data.model.WrongRecoveryCodeException if [code] doesn't match.
     */
    suspend fun resetAdminPinWithRecoveryCode(code: String, newPin: String): String

    /**
     * Emits the stored theme preference ([ThemePreference.SYSTEM] until one is set) and
     * again whenever it changes.
     */
    fun getThemePreference(): Flow<ThemePreference>

    /**
     * Persists the theme choice.
     */
    suspend fun setThemePreference(pref: ThemePreference)
}
