package com.betcoin.data.repository.local

import com.betcoin.data.database.dao.AppSettingsDao
import com.betcoin.data.database.entity.AppSettings
import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.model.ThemePreference
import com.betcoin.data.model.WrongRecoveryCodeException
import com.betcoin.data.repository.RecoveryCode
import com.betcoin.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.mindrot.jbcrypt.BCrypt
import javax.inject.Inject

/**
 * Room-backed implementation of [SettingsRepository].
 *
 * Uses BCrypt (cost [BCRYPT_COST]) to hash the admin PIN and the recovery code before
 * storage, on [Dispatchers.Default]. Neither is ever stored or logged in plain text.
 *
 * An empty hash means "not set": the settings row can exist before onboarding when the
 * theme is chosen first.
 */
class LocalSettingsRepository @Inject constructor(
    private val appSettingsDao: AppSettingsDao,
) : SettingsRepository {

    override suspend fun getAdminPinHash(): String? {
        return appSettingsDao.getSettings()?.adminPinHash?.takeIf { it.isNotEmpty() }
    }

    override suspend fun setAdminPin(pin: String) {
        requireValidPin(pin)
        val hashed = hash(pin)
        val existing = appSettingsDao.getSettings()
        if (existing == null) {
            appSettingsDao.insert(AppSettings(adminPinHash = hashed, recoveryCodeHash = ""))
        } else {
            appSettingsDao.update(existing.copy(adminPinHash = hashed))
        }
    }

    override suspend fun verifyAdminPin(pin: String): Boolean {
        val storedHash = getAdminPinHash() ?: return false
        return withContext(Dispatchers.Default) { BCrypt.checkpw(pin, storedHash) }
    }

    override suspend fun isFirstLaunch(): Boolean {
        return getAdminPinHash() == null
    }

    override suspend fun regenerateRecoveryCode(): String {
        val existing = appSettingsDao.getSettings()
        check(existing != null && existing.adminPinHash.isNotEmpty()) {
            "Set the admin PIN before generating a recovery code"
        }
        val code = RecoveryCode.generate()
        val codeHash = hash(RecoveryCode.normalize(code)!!)
        appSettingsDao.update(existing.copy(recoveryCodeHash = codeHash))
        return code
    }

    override suspend fun resetAdminPinWithRecoveryCode(code: String, newPin: String): String {
        requireValidPin(newPin)
        val existing = appSettingsDao.getSettings()
        val storedHash = existing?.recoveryCodeHash?.takeIf { it.isNotEmpty() }
        val normalized = RecoveryCode.normalize(code)
        if (storedHash == null || normalized == null) throw WrongRecoveryCodeException()
        val matches = withContext(Dispatchers.Default) { BCrypt.checkpw(normalized, storedHash) }
        if (!matches) throw WrongRecoveryCodeException()

        val newCode = RecoveryCode.generate()
        val pinHash = hash(newPin)
        val codeHash = hash(RecoveryCode.normalize(newCode)!!)
        // One write, so the new PIN and the new code always land together.
        appSettingsDao.update(existing.copy(adminPinHash = pinHash, recoveryCodeHash = codeHash))
        return newCode
    }

    override fun getThemePreference(): Flow<ThemePreference> {
        return appSettingsDao.observeSettings()
            .map { it?.themePreference ?: ThemePreference.SYSTEM }
            .distinctUntilChanged()
    }

    override suspend fun setThemePreference(pref: ThemePreference) {
        val existing = appSettingsDao.getSettings()
        if (existing == null) {
            appSettingsDao.insert(
                AppSettings(adminPinHash = "", recoveryCodeHash = "", themePreference = pref),
            )
        } else {
            appSettingsDao.update(existing.copy(themePreference = pref))
        }
    }

    private suspend fun hash(secret: String): String =
        withContext(Dispatchers.Default) { BCrypt.hashpw(secret, BCrypt.gensalt(BCRYPT_COST)) }

    private fun requireValidPin(pin: String) {
        if (!PIN_REGEX.matches(pin)) throw InvalidInputException("PIN must be exactly 4 digits")
    }

    private companion object {
        const val BCRYPT_COST = 10
        val PIN_REGEX = Regex("[0-9]{4}")
    }
}
