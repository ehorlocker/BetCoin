package com.betcoin.ui.onboarding

import com.betcoin.data.repository.SettingsRepository

/**
 * Fake implementation of [SettingsRepository] for testing.
 */
class FakeSettingsRepository : SettingsRepository {
    var storedHash: String? = null
    var storedPin: String? = null
    var isFirstLaunchResult: Boolean = true

    override suspend fun getAdminPinHash(): String? = storedHash

    override suspend fun setAdminPin(pin: String) {
        storedPin = pin
        storedHash = "hashed_$pin"
        isFirstLaunchResult = false
    }

    override suspend fun verifyAdminPin(pin: String): Boolean {
        return storedHash == "hashed_$pin"
    }

    override suspend fun isFirstLaunch(): Boolean = isFirstLaunchResult
}
