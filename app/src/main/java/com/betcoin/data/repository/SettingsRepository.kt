package com.betcoin.data.repository

/**
 * Contract for application settings operations.
 *
 * Implementations handle admin PIN management and first-launch detection.
 */
interface SettingsRepository {

    /**
     * Returns the stored admin PIN hash, or `null` if no PIN has been set.
     */
    suspend fun getAdminPinHash(): String?

    /**
     * Hashes and stores the admin PIN.
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
}
