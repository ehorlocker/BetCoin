package com.betcoin.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.betcoin.data.model.ThemePreference

/**
 * Stores application-wide settings. This is a single-row table (id is always [SINGLETON_ID]).
 *
 * Holds the master admin PIN hash, the admin recovery code hash and the theme preference.
 */
@Entity(tableName = "app_settings")
data class AppSettings(
  @PrimaryKey
  val id: Int = SINGLETON_ID,

  @ColumnInfo(name = "admin_pin_hash")
  val adminPinHash: String,

  @ColumnInfo(name = "recovery_code_hash")
  val recoveryCodeHash: String,

  @ColumnInfo(name = "theme_preference", defaultValue = "SYSTEM")
  val themePreference: ThemePreference = ThemePreference.SYSTEM,
) {
  companion object {
    /** The fixed primary key for the single settings row. */
    const val SINGLETON_ID: Int = 1
  }
}
