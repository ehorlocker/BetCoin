package com.betcoin.data.repository.local

import com.betcoin.data.repository.SettingsRepository
import javax.inject.Inject

/**
 * Room-backed implementation of [SettingsRepository].
 *
 * Methods will be implemented in a later ticket (KAN-11).
 */
class LocalSettingsRepository @Inject constructor() : SettingsRepository {
    override suspend fun getAdminPinHash(): String? = TODO()
    override suspend fun setAdminPin(pin: String): Unit = TODO()
    override suspend fun verifyAdminPin(pin: String): Boolean = TODO()
    override suspend fun isFirstLaunch(): Boolean = TODO()
}
