package com.example.betcoin.data.database.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.betcoin.data.database.BetCoinDatabase
import com.example.betcoin.data.database.entity.AppSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [AppSettingsDao].
 *
 * Uses an in-memory Room database so tests are hermetic and fast.
 */
@RunWith(AndroidJUnit4::class)
class AppSettingsDaoTest {

  private lateinit var database: BetCoinDatabase
  private lateinit var appSettingsDao: AppSettingsDao

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    database = Room.inMemoryDatabaseBuilder(context, BetCoinDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    appSettingsDao = database.appSettingsDao()
  }

  @After
  fun tearDown() {
    database.close()
  }

  // --- insert ---

  @Test
  fun insert_settingsSuccessfully() = runTest {
    val settings = AppSettings(adminPinHash = "admin_hash_123")
    appSettingsDao.insert(settings)

    val retrieved = appSettingsDao.getSettings()
    assertThat(retrieved).isNotNull()
    assertThat(retrieved!!.adminPinHash).isEqualTo("admin_hash_123")
  }

  @Test
  fun insert_defaultsIdToOne() = runTest {
    val settings = AppSettings(adminPinHash = "some_hash")
    appSettingsDao.insert(settings)

    val retrieved = appSettingsDao.getSettings()
    assertThat(retrieved).isNotNull()
    assertThat(retrieved!!.id).isEqualTo(1)
  }

  @Test
  fun insert_calledTwice_replacesExistingRow() = runTest {
    appSettingsDao.insert(AppSettings(adminPinHash = "first_hash"))
    appSettingsDao.insert(AppSettings(adminPinHash = "second_hash"))

    val retrieved = appSettingsDao.getSettings()
    assertThat(retrieved).isNotNull()
    assertThat(retrieved!!.adminPinHash).isEqualTo("second_hash")
  }

  // --- getSettings ---

  @Test
  fun getSettings_noRow_returnsNull() = runTest {
    val settings = appSettingsDao.getSettings()
    assertThat(settings).isNull()
  }

  @Test
  fun getSettings_afterInsert_returnsSettings() = runTest {
    appSettingsDao.insert(AppSettings(adminPinHash = "hash_value"))

    val settings = appSettingsDao.getSettings()
    assertThat(settings).isNotNull()
    assertThat(settings!!.adminPinHash).isEqualTo("hash_value")
  }

  // --- update ---

  @Test
  fun update_changesAdminPinHash() = runTest {
    appSettingsDao.insert(AppSettings(adminPinHash = "old_hash"))

    val settings = appSettingsDao.getSettings()!!
    appSettingsDao.update(settings.copy(adminPinHash = "new_hash"))

    val updated = appSettingsDao.getSettings()!!
    assertThat(updated.adminPinHash).isEqualTo("new_hash")
  }

  @Test
  fun update_preservesId() = runTest {
    appSettingsDao.insert(AppSettings(adminPinHash = "old_hash"))
    appSettingsDao.update(AppSettings(adminPinHash = "new_hash"))

    val updated = appSettingsDao.getSettings()!!
    assertThat(updated.id).isEqualTo(1)
    assertThat(updated.adminPinHash).isEqualTo("new_hash")
  }
}
