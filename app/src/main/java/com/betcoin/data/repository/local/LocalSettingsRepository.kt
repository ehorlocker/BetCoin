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
        require(pin.length >= 4) { "PIN must be at least 4 characters" }
        val hashed = BCrypt.hashpw(pin, BCrypt.gensalt())
        val existing = appSettingsDao.getSettings()
        if (existing == null) {
            appSettingsDao.insert(AppSettings(adminPinHash = hashed))
        } else {
            appSettingsDao.update(existing.copy(adminPinHash = hashed))
        }
    }

    override suspend fun verifyAdminPin(pin: String): Boolean {
        val storedHash = appSettingsDao.getSettings()?.adminPinHash ?: return false
        return BCrypt.checkpw(pin, storedHash)
    }

    override suspend fun isFirstLaunch(): Boolean {
        return appSettingsDao.getSettings() == null
    }
}
