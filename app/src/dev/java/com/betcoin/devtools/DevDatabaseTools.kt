package com.betcoin.devtools

import android.content.Context
import com.betcoin.BuildConfig
import com.betcoin.data.database.BetCoinDatabase
import com.betcoin.data.database.dao.AppSettingsDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** A snapshot of the database for the Dev Tools Info panel. */
data class DevDatabaseInfo(
    val databaseName: String,
    val schemaVersion: Int,
    /** Table name to row count, in the order users, bets, bet_outcomes, bet_participants, app_settings. */
    val rowCounts: List<Pair<String, Long>>,
    val appVersion: String,
)

/**
 * Dev-only database helpers: wipe everything, reset the admin PIN, and report database info.
 */
class DevDatabaseTools @Inject constructor(
    private val database: BetCoinDatabase,
    private val appSettingsDao: AppSettingsDao,
    @ApplicationContext private val context: Context,
) {

    /** Clears every table and deletes all stored avatar files. */
    suspend fun wipe() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        File(context.filesDir, AVATARS_DIR).deleteRecursively()
        Unit
    }

    /**
     * Clears the admin PIN and recovery code hashes (keeping the theme), so the next launch
     * opens Onboarding. Does nothing if no settings row exists.
     */
    suspend fun resetAdminPin() {
        val settings = appSettingsDao.getSettings() ?: return
        appSettingsDao.update(settings.copy(adminPinHash = "", recoveryCodeHash = ""))
    }

    /** Returns the database name, schema version, row counts and app version. */
    suspend fun info(): DevDatabaseInfo = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        val counts = TABLES.map { table ->
            table to db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
        }
        val buildType = BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercase() }
        DevDatabaseInfo(
            databaseName = BuildConfig.DB_NAME,
            schemaVersion = db.version,
            rowCounts = counts,
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
                "${BuildConfig.FLAVOR}$buildType",
        )
    }

    private companion object {
        /** Mirrors the private avatars directory name in LocalAvatarStore. */
        const val AVATARS_DIR = "avatars"
        val TABLES = listOf("users", "bets", "bet_outcomes", "bet_participants", "app_settings")
    }
}
