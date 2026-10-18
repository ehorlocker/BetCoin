package com.betcoin.data.repository.local

import com.betcoin.data.database.dao.AppSettingsDao
import com.betcoin.data.database.entity.AppSettings
import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.model.ThemePreference
import com.betcoin.data.model.WrongRecoveryCodeException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [LocalSettingsRepository].
 *
 * Uses an in-memory fake of [AppSettingsDao] so tests are fast and hermetic.
 */
class LocalSettingsRepositoryTest {

  /** Fake DAO that stores settings in memory. */
  private class FakeAppSettingsDao : AppSettingsDao {
    private val settings = MutableStateFlow<AppSettings?>(null)

    override suspend fun insert(settings: AppSettings) {
      this.settings.value = settings
    }

    override suspend fun getSettings(): AppSettings? = settings.value

    override fun observeSettings(): Flow<AppSettings?> = settings

    override suspend fun update(settings: AppSettings) {
      if (this.settings.value != null) this.settings.value = settings
    }
  }

  private val fakeDao = FakeAppSettingsDao()
  private val repository = LocalSettingsRepository(fakeDao)

  // --- isFirstLaunch ---

  @Test
  fun isFirstLaunch_noSettings_returnsTrue() = runTest {
    val result = repository.isFirstLaunch()

    assertThat(result).isTrue()
  }

  @Test
  fun isFirstLaunch_afterSetAdminPin_returnsFalse() = runTest {
    repository.setAdminPin("1234")

    val result = repository.isFirstLaunch()

    assertThat(result).isFalse()
  }

  @Test
  fun isFirstLaunch_onlyThemeSet_returnsTrue() = runTest {
    repository.setThemePreference(ThemePreference.DARK)

    assertThat(repository.isFirstLaunch()).isTrue()
    assertThat(repository.getAdminPinHash()).isNull()
    assertThat(repository.verifyAdminPin("1234")).isFalse()
  }

  // --- setAdminPin ---

  @Test
  fun setAdminPin_storesHashedPinNotPlaintext() = runTest {
    val pin = "1234"

    repository.setAdminPin(pin)

    val stored = fakeDao.getSettings()
    assertThat(stored).isNotNull()
    assertThat(stored!!.adminPinHash).isNotEqualTo(pin)
    assertThat(stored.adminPinHash).isNotEmpty()
  }

  @Test
  fun setAdminPin_calledTwice_overwritesExistingHash() = runTest {
    repository.setAdminPin("1234")
    val firstHash = fakeDao.getSettings()!!.adminPinHash

    repository.setAdminPin("5678")
    val secondHash = fakeDao.getSettings()!!.adminPinHash

    assertThat(secondHash).isNotEqualTo(firstHash)
  }

  @Test
  fun setAdminPin_existingSettings_preservesIdRecoveryCodeAndTheme() = runTest {
    fakeDao.insert(
      AppSettings(
        id = 1,
        adminPinHash = "old_hash",
        recoveryCodeHash = "recovery_hash",
        themePreference = ThemePreference.LIGHT,
      ),
    )

    repository.setAdminPin("5678")

    val stored = fakeDao.getSettings()!!
    assertThat(stored.id).isEqualTo(AppSettings.SINGLETON_ID)
    assertThat(stored.recoveryCodeHash).isEqualTo("recovery_hash")
    assertThat(stored.themePreference).isEqualTo(ThemePreference.LIGHT)
  }

  @Test
  fun setAdminPin_invalidPins_throwAndStoreNothing() = runTest {
    for (pin in listOf("", "123", "12345", "abcd", "12a4", " 123", "١٢٣٤")) {
      assertFailsWith<InvalidInputException> {
        repository.setAdminPin(pin)
      }
    }
    assertThat(fakeDao.getSettings()).isNull()
  }

  // --- getAdminPinHash ---

  @Test
  fun getAdminPinHash_noSettings_returnsNull() = runTest {
    val result = repository.getAdminPinHash()

    assertThat(result).isNull()
  }

  @Test
  fun getAdminPinHash_afterSetAdminPin_returnsStoredHash() = runTest {
    repository.setAdminPin("1234")

    val result = repository.getAdminPinHash()

    assertThat(result).isNotNull()
    assertThat(result).isEqualTo(fakeDao.getSettings()!!.adminPinHash)
  }

  // --- verifyAdminPin ---

  @Test
  fun verifyAdminPin_correctPin_returnsTrue() = runTest {
    repository.setAdminPin("1234")

    val result = repository.verifyAdminPin("1234")

    assertThat(result).isTrue()
  }

  @Test
  fun verifyAdminPin_incorrectPin_returnsFalse() = runTest {
    repository.setAdminPin("1234")

    val result = repository.verifyAdminPin("wrong")

    assertThat(result).isFalse()
  }

  @Test
  fun verifyAdminPin_noSettings_returnsFalse() = runTest {
    val result = repository.verifyAdminPin("1234")

    assertThat(result).isFalse()
  }

  // --- regenerateRecoveryCode ---

  @Test
  fun regenerateRecoveryCode_returnsFormattedCodeAndStoresOnlyItsHash() = runTest {
    repository.setAdminPin("1234")

    val code = repository.regenerateRecoveryCode()

    assertThat(code).matches("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}")
    val storedHash = fakeDao.getSettings()!!.recoveryCodeHash
    assertThat(storedHash).isNotEmpty()
    assertThat(storedHash).doesNotContain(code.replace("-", ""))
    assertThat(storedHash).startsWith("\$2a\$10\$")
  }

  @Test
  fun regenerateRecoveryCode_keepsAdminPin() = runTest {
    repository.setAdminPin("1234")

    repository.regenerateRecoveryCode()

    assertThat(repository.verifyAdminPin("1234")).isTrue()
  }

  @Test
  fun regenerateRecoveryCode_invalidatesPreviousCode() = runTest {
    repository.setAdminPin("1234")
    val oldCode = repository.regenerateRecoveryCode()

    val newCode = repository.regenerateRecoveryCode()

    assertThat(newCode).isNotEqualTo(oldCode)
    assertFailsWith<WrongRecoveryCodeException> {
      repository.resetAdminPinWithRecoveryCode(oldCode, "5678")
    }
    repository.resetAdminPinWithRecoveryCode(newCode, "5678")
    assertThat(repository.verifyAdminPin("5678")).isTrue()
  }

  @Test
  fun regenerateRecoveryCode_noAdminPin_throws() = runTest {
    assertFailsWith<IllegalStateException> {
      repository.regenerateRecoveryCode()
    }
  }

  // --- resetAdminPinWithRecoveryCode ---

  @Test
  fun resetAdminPinWithRecoveryCode_correctCode_setsNewPin() = runTest {
    repository.setAdminPin("1234")
    val code = repository.regenerateRecoveryCode()

    repository.resetAdminPinWithRecoveryCode(code, "5678")

    assertThat(repository.verifyAdminPin("5678")).isTrue()
    assertThat(repository.verifyAdminPin("1234")).isFalse()
  }

  @Test
  fun resetAdminPinWithRecoveryCode_lowercaseWithoutDashes_isAccepted() = runTest {
    repository.setAdminPin("1234")
    val code = repository.regenerateRecoveryCode()

    repository.resetAdminPinWithRecoveryCode(code.replace("-", "").lowercase(), "5678")

    assertThat(repository.verifyAdminPin("5678")).isTrue()
  }

  @Test
  fun resetAdminPinWithRecoveryCode_rotatesCode() = runTest {
    repository.setAdminPin("1234")
    val oldCode = repository.regenerateRecoveryCode()

    val newCode = repository.resetAdminPinWithRecoveryCode(oldCode, "5678")

    assertThat(newCode).isNotEqualTo(oldCode)
    assertThat(newCode).matches("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}")
    assertFailsWith<WrongRecoveryCodeException> {
      repository.resetAdminPinWithRecoveryCode(oldCode, "9999")
    }
    repository.resetAdminPinWithRecoveryCode(newCode, "9999")
    assertThat(repository.verifyAdminPin("9999")).isTrue()
  }

  @Test
  fun resetAdminPinWithRecoveryCode_wrongCode_throwsAndChangesNothing() = runTest {
    repository.setAdminPin("1234")
    val code = repository.regenerateRecoveryCode()
    val before = fakeDao.getSettings()

    for (wrong in listOf("AAAA-AAAA-AAAA", "not a code", "", code.dropLast(1))) {
      assertFailsWith<WrongRecoveryCodeException> {
        repository.resetAdminPinWithRecoveryCode(wrong, "5678")
      }
    }

    assertThat(fakeDao.getSettings()).isEqualTo(before)
    assertThat(repository.verifyAdminPin("1234")).isTrue()
  }

  @Test
  fun resetAdminPinWithRecoveryCode_noCodeStored_throwsWrongRecoveryCode() = runTest {
    repository.setAdminPin("1234")

    assertFailsWith<WrongRecoveryCodeException> {
      repository.resetAdminPinWithRecoveryCode("ABCD-EFGH-JK23", "5678")
    }
  }

  @Test
  fun resetAdminPinWithRecoveryCode_invalidNewPin_throwsAndChangesNothing() = runTest {
    repository.setAdminPin("1234")
    val code = repository.regenerateRecoveryCode()
    val before = fakeDao.getSettings()

    assertFailsWith<InvalidInputException> {
      repository.resetAdminPinWithRecoveryCode(code, "567")
    }

    assertThat(fakeDao.getSettings()).isEqualTo(before)
    repository.resetAdminPinWithRecoveryCode(code, "5678")
    assertThat(repository.verifyAdminPin("5678")).isTrue()
  }

  // --- theme preference ---

  @Test
  fun getThemePreference_noSettings_emitsSystem() = runTest {
    assertThat(repository.getThemePreference().first()).isEqualTo(ThemePreference.SYSTEM)
  }

  @Test
  fun setThemePreference_beforeAdminPin_isPersisted() = runTest {
    repository.setThemePreference(ThemePreference.LIGHT)

    assertThat(repository.getThemePreference().first()).isEqualTo(ThemePreference.LIGHT)

    repository.setAdminPin("1234")
    assertThat(repository.getThemePreference().first()).isEqualTo(ThemePreference.LIGHT)
    assertThat(repository.verifyAdminPin("1234")).isTrue()
  }

  @Test
  fun setThemePreference_keepsAdminPinAndRecoveryCode() = runTest {
    repository.setAdminPin("1234")
    repository.regenerateRecoveryCode()
    val before = fakeDao.getSettings()!!

    repository.setThemePreference(ThemePreference.DARK)

    val after = fakeDao.getSettings()!!
    assertThat(after.themePreference).isEqualTo(ThemePreference.DARK)
    assertThat(after.adminPinHash).isEqualTo(before.adminPinHash)
    assertThat(after.recoveryCodeHash).isEqualTo(before.recoveryCodeHash)
  }

  @Test
  fun getThemePreference_emitsEachChange() = runTest {
    val emitted = mutableListOf<ThemePreference>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.getThemePreference().take(3).toList(emitted)
    }

    repository.setThemePreference(ThemePreference.DARK)
    repository.setThemePreference(ThemePreference.DARK)
    repository.setThemePreference(ThemePreference.LIGHT)
    job.join()

    assertThat(emitted)
      .containsExactly(ThemePreference.SYSTEM, ThemePreference.DARK, ThemePreference.LIGHT)
      .inOrder()
  }

  private suspend inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit) {
    try {
      block()
    } catch (e: Throwable) {
      if (e is T) return
      throw e
    }
    throw AssertionError("Expected ${T::class.simpleName} to be thrown")
  }
}
