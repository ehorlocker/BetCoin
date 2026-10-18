package com.betcoin.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.betcoin.data.database.entity.AppSettings
import kotlinx.coroutines.flow.Flow

/** Data access object for [AppSettings] operations. */
@Dao
interface AppSettingsDao {

  /** Inserts or replaces the app settings row (single-row table, id is always 1). */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(settings: AppSettings)

  /** Returns the app settings row (id=1), or null if no settings exist. */
  @Query("SELECT * FROM app_settings WHERE id = 1")
  suspend fun getSettings(): AppSettings?

  /** Emits the app settings row (id=1), or null if no settings exist, and again on every change. */
  @Query("SELECT * FROM app_settings WHERE id = 1")
  fun observeSettings(): Flow<AppSettings?>

  /** Updates the existing app settings row (matched by primary key). */
  @Update
  suspend fun update(settings: AppSettings)
}
