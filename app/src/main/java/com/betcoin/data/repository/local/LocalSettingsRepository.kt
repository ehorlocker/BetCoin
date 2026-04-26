package com.betcoin.data.repository.local

import com.betcoin.data.database.dao.AppSettingsDao
import com.betcoin.data.database.entity.AppSettings
import com.betcoin.data.repository.SettingsRepository
import org.mindrot.jbcrypt.BCrypt
import javax.inject.Inject

/**
 * Room-backed implementation of [SettingsRepository].
 *
 * Uses BCrypt to hash the admin PIN before storage. The plaintext PIN is never
 * stored or logged.
 */
class LocalSettingsRepository @Inject constructor(
    private val appSettingsDao: AppSettingsDao,
) : SettingsRepository {

    override suspend fun getAdminPinHash(): String? {
        return appSettingsDao.getSettings()?.adminPinHash
    }

    override suspend fun setAdminPin(pin: String) {
        val hashed = BCrypt.hashpw(pin, BCrypt.gensalt())
        appSettingsDao.insert(AppSettings(adminPinHash = hashed))
    }

    override suspend fun verifyAdminPin(pin: String): Boolean {
        val storedHash = appSettingsDao.getSettings()?.adminPinHash ?: return false
        return BCrypt.checkpw(pin, storedHash)
    }

    override suspend fun isFirstLaunch(): Boolean {
        return appSettingsDao.getSettings() == null
    }
}
