package com.betcoin.data.database.entity

import com.betcoin.data.model.ThemePreference
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [AppSettings] entity data class construction and constraints.
 */
class AppSettingsTest {

  @Test
  fun defaultId_isSingletonId() {
    val settings = AppSettings(adminPinHash = "hashed_admin_pin", recoveryCodeHash = "recovery_hash")

    assertThat(settings.id).isEqualTo(AppSettings.SINGLETON_ID)
  }

  @Test
  fun adminPinHash_isSetCorrectly() {
    val settings = AppSettings(adminPinHash = "bcrypt_hash_value", recoveryCodeHash = "recovery_hash")

    assertThat(settings.adminPinHash).isEqualTo("bcrypt_hash_value")
  }

  @Test
  fun id_isAlwaysSingletonIdByDefault() {
    val settings1 = AppSettings(adminPinHash = "hash1", recoveryCodeHash = "recovery_hash")
    val settings2 = AppSettings(adminPinHash = "hash2", recoveryCodeHash = "recovery_hash")

    assertThat(settings1.id).isEqualTo(settings2.id)
    assertThat(settings1.id).isEqualTo(AppSettings.SINGLETON_ID)
  }

  @Test
  fun themePreference_defaultsToSystem() {
    val settings = AppSettings(adminPinHash = "hash", recoveryCodeHash = "recovery_hash")

    assertThat(settings.themePreference).isEqualTo(ThemePreference.SYSTEM)
  }
}
