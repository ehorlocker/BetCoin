package com.betcoin.ui.onboarding

import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.model.ThemePreference
import com.betcoin.data.model.WrongRecoveryCodeException
import com.betcoin.data.repository.RecoveryCode
import com.betcoin.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.mindrot.jbcrypt.BCrypt

/**
 * Fake implementation of [SettingsRepository] for testing.
 *
 * Hashes the admin PIN and recovery code with BCrypt like the real implementation, so no
 * plain-text secret is kept. Hashing runs on the caller's dispatcher to keep tests that use
 * a test dispatcher deterministic.
 */
class FakeSettingsRepository : SettingsRepository {
    var storedHash: String? = null
    var storedRecoveryCodeHash: String? = null
    var isFirstLaunchResult: Boolean = true

    /** When set, [isFirstLaunch] throws it. */
    var isFirstLaunchError: Throwable? = null

    /** When set, [isFirstLaunch] suspends until it completes. */
    var isFirstLaunchGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    /** Number of [isFirstLaunch] calls. */
    var isFirstLaunchCalls = 0
        private set
    val themePreference = MutableStateFlow(ThemePreference.SYSTEM)

    /** When set, the next [setThemePreference] throws this once and does not store the value. */
    var failNextSetTheme: Throwable? = null

    override suspend fun getAdminPinHash(): String? = storedHash

    override suspend fun setAdminPin(pin: String) {
        requireValidPin(pin)
        storedHash = BCrypt.hashpw(pin, BCrypt.gensalt())
        isFirstLaunchResult = false
    }

    override suspend fun verifyAdminPin(pin: String): Boolean {
        val hash = storedHash ?: return false
        return BCrypt.checkpw(pin, hash)
    }

    override suspend fun isFirstLaunch(): Boolean {
        isFirstLaunchCalls++
        isFirstLaunchGate?.await()
        isFirstLaunchError?.let { throw it }
        return isFirstLaunchResult
    }

    override suspend fun regenerateRecoveryCode(): String {
        check(storedHash != null) { "Set the admin PIN before generating a recovery code" }
        val code = RecoveryCode.generate()
        storedRecoveryCodeHash = BCrypt.hashpw(RecoveryCode.normalize(code)!!, BCrypt.gensalt())
        return code
    }

    override suspend fun resetAdminPinWithRecoveryCode(code: String, newPin: String): String {
        requireValidPin(newPin)
        val hash = storedRecoveryCodeHash
        val normalized = RecoveryCode.normalize(code)
        if (hash == null || normalized == null || !BCrypt.checkpw(normalized, hash)) {
            throw WrongRecoveryCodeException()
        }
        setAdminPin(newPin)
        return regenerateRecoveryCode()
    }

    override fun getThemePreference(): Flow<ThemePreference> = themePreference

    override suspend fun setThemePreference(pref: ThemePreference) {
        failNextSetTheme?.let {
            failNextSetTheme = null
            throw it
        }
        themePreference.value = pref
    }

    private fun requireValidPin(pin: String) {
        if (!Regex("[0-9]{4}").matches(pin)) {
            throw InvalidInputException("PIN must be exactly 4 digits")
        }
    }
}
