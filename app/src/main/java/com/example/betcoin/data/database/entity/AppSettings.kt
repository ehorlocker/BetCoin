package com.example.betcoin.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores application-wide settings. This is a single-row table (id is always [SINGLETON_ID]).
 *
 * Currently holds only the master admin PIN hash.
 */
@Entity(tableName = "app_settings")
data class AppSettings(
  @PrimaryKey
  val id: Int = SINGLETON_ID,

  @ColumnInfo(name = "admin_pin_hash")
  val adminPinHash: String,
) {
  companion object {
    /** The fixed primary key for the single settings row. */
    const val SINGLETON_ID: Int = 1
  }
}
