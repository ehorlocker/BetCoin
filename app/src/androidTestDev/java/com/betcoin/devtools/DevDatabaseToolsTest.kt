package com.betcoin.devtools

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.betcoin.data.database.BetCoinDatabase
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.ThemePreference
import com.betcoin.data.repository.local.LocalSettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Instrumented tests for [DevDatabaseTools] against an in-memory Room database. */
@RunWith(AndroidJUnit4::class)
class DevDatabaseToolsTest {

    private lateinit var context: Context
    private lateinit var database: BetCoinDatabase
    private lateinit var settings: LocalSettingsRepository
    private lateinit var tools: DevDatabaseTools
    private val avatarsDir get() = File(context.filesDir, "avatars")

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, BetCoinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = LocalSettingsRepository(database.appSettingsDao())
        tools = DevDatabaseTools(database, database.appSettingsDao(), context)
        avatarsDir.deleteRecursively()
    }

    @After
    fun tearDown() {
        database.close()
        avatarsDir.deleteRecursively()
    }

    private suspend fun insertUser(name: String = "Austin"): Long =
        database.userDao().insert(
            User(username = name, pinHash = "x", balance = 1000L, totalDebt = 0L, createdAt = 0L),
        )

    @Test
    fun wipe_clearsAllTablesAndAvatars() = runTest {
        insertUser()
        settings.setAdminPin("1234")
        avatarsDir.mkdirs()
        File(avatarsDir, "x.jpg").writeText("img")

        tools.wipe()

        assertThat(tools.info().rowCounts.map { it.second }).containsExactly(0L, 0L, 0L, 0L, 0L)
        assertThat(avatarsDir.exists()).isFalse()
    }

    @Test
    fun resetAdminPin_clearsPinAndRecoveryKeepsPlayersAndTheme() = runTest {
        settings.setAdminPin("1234")
        settings.regenerateRecoveryCode()
        settings.setThemePreference(ThemePreference.DARK)
        insertUser()

        tools.resetAdminPin()

        assertThat(settings.isFirstLaunch()).isTrue()
        val row = database.appSettingsDao().getSettings()!!
        assertThat(row.recoveryCodeHash).isEqualTo("")
        assertThat(row.adminPinHash).isEqualTo("")
        assertThat(row.themePreference).isEqualTo(ThemePreference.DARK)
        assertThat(database.userDao().getAll().first()).hasSize(1)
    }

    @Test
    fun resetAdminPin_noSettings_isNoOp() = runTest {
        tools.resetAdminPin()

        assertThat(database.appSettingsDao().getSettings()).isNull()
    }

    @Test
    fun info_reportsNameVersionCountsAndAppVersion() = runTest {
        insertUser("Austin")
        insertUser("Sam")
        settings.setAdminPin("1234")

        val info = tools.info()

        assertThat(info.databaseName).isEqualTo("betcoin_dev.db")
        assertThat(info.schemaVersion).isEqualTo(2)
        assertThat(info.rowCounts).containsExactly(
            "users" to 2L,
            "bets" to 0L,
            "bet_outcomes" to 0L,
            "bet_participants" to 0L,
            "app_settings" to 1L,
        ).inOrder()
        assertThat(info.appVersion).contains("devDebug")
    }
}
