package com.betcoin.data.repository.local

import com.betcoin.data.database.dao.AppSettingsDao
import com.betcoin.data.database.entity.AppSettings
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
    private var settings: AppSettings? = null

    override suspend fun insert(settings: AppSettings) {
      this.settings = settings
    }

    override suspend fun getSettings(): AppSettings? = settings

    override suspend fun update(settings: AppSettings) {
      this.settings = settings
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
  fun setAdminPin_existingSettings_preservesId() = runTest {
    fakeDao.insert(AppSettings(id = 1, adminPinHash = "old_hash"))

    repository.setAdminPin("5678")

    val stored = fakeDao.getSettings()!!
    assertThat(stored.id).isEqualTo(AppSettings.SINGLETON_ID)
  }

  @Test(expected = IllegalArgumentException::class)
  fun setAdminPin_emptyPin_throws() = runTest {
    repository.setAdminPin("")
  }

  @Test(expected = IllegalArgumentException::class)
  fun setAdminPin_shortPin_throws() = runTest {
    repository.setAdminPin("123")
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
}
