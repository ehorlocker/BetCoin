package com.example.betcoin.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.betcoin.data.database.entity.AppSettings

/** Data access object for [AppSettings] operations. */
@Dao
interface AppSettingsDao {

  /** Inserts a new app settings row. */
  @Insert
  suspend fun insert(settings: AppSettings)

  /** Returns the app settings row (id=1), or null if no settings exist. */
  @Query("SELECT * FROM app_settings WHERE id = 1")
  suspend fun getSettings(): AppSettings?

  /** Updates the existing app settings row (matched by primary key). */
  @Update
  suspend fun update(settings: AppSettings)
}
