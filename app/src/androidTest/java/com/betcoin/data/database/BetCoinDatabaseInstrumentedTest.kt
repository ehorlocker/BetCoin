package com.betcoin.data.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.betcoin.data.database.entity.AppSettings
import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.BetParticipant
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.ThemePreference
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [BetCoinDatabase] schema validation.
 *
 * Verifies that Room creates all expected tables with the correct columns and that basic
 * insert/read operations work for each entity.
 */
@RunWith(AndroidJUnit4::class)
class BetCoinDatabaseInstrumentedTest {

  private lateinit var database: BetCoinDatabase

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    database = Room.inMemoryDatabaseBuilder(context, BetCoinDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun database_createsSuccessfully() {
    assertThat(database).isNotNull()
    // Trigger lazy initialization so the database actually opens.
    database.openHelper.readableDatabase
    assertThat(database.isOpen).isTrue()
  }

  @Test
  fun usersTable_existsWithCorrectColumns() {
    val cursor = database.openHelper.readableDatabase.query(
      "SELECT * FROM users LIMIT 0"
    )
    val columnNames = cursor.columnNames.toList()
    cursor.close()

    assertThat(columnNames).containsExactly(
      "id", "username", "pin_hash", "balance", "total_wins", "total_losses",
      "total_earnings", "total_lost", "bailout_count", "total_debt", "avatar_ref", "created_at",
    )
  }

  @Test
  fun betsTable_existsWithCorrectColumns() {
    val cursor = database.openHelper.readableDatabase.query(
      "SELECT * FROM bets LIMIT 0"
    )
    val columnNames = cursor.columnNames.toList()
    cursor.close()

    assertThat(columnNames).containsExactly(
      "id", "prompt", "status", "winning_outcome_id", "created_at", "resolved_at",
    )
  }

  @Test
  fun betOutcomesTable_existsWithCorrectColumns() {
    val cursor = database.openHelper.readableDatabase.query(
      "SELECT * FROM bet_outcomes LIMIT 0"
    )
    val columnNames = cursor.columnNames.toList()
    cursor.close()

    assertThat(columnNames).containsExactly(
      "id", "bet_id", "outcome_text",
    )
  }

  @Test
  fun betParticipantsTable_existsWithCorrectColumns() {
    val cursor = database.openHelper.readableDatabase.query(
      "SELECT * FROM bet_participants LIMIT 0"
    )
    val columnNames = cursor.columnNames.toList()
    cursor.close()

    assertThat(columnNames).containsExactly(
      "id", "bet_id", "user_id", "outcome_id", "wager_amount", "payout",
      "deleted_username",
    )
  }

  @Test
  fun appSettingsTable_existsWithCorrectColumns() {
    val cursor = database.openHelper.readableDatabase.query(
      "SELECT * FROM app_settings LIMIT 0"
    )
    val columnNames = cursor.columnNames.toList()
    cursor.close()

    assertThat(columnNames).containsExactly(
      "id", "admin_pin_hash", "recovery_code_hash", "theme_preference",
    )
  }

  @Test
  fun usersTable_insertAndReadBack() {
    val db = database.openHelper.writableDatabase
    val values = ContentValues().apply {
      put("username", "alice")
      put("pin_hash", "hashed_pin")
      put("balance", 1000L)
      put("total_wins", 0)
      put("total_losses", 0)
      put("total_earnings", 0L)
      put("total_lost", 0L)
      put("bailout_count", 0)
      put("total_debt", 0L)
      put("created_at", System.currentTimeMillis())
    }
    val rowId = db.insert("users", SQLiteDatabase.CONFLICT_ABORT, values)
    assertThat(rowId).isGreaterThan(0L)

    val cursor = db.query("SELECT username, balance FROM users WHERE id = ?", arrayOf(rowId))
    assertThat(cursor.moveToFirst()).isTrue()
    assertThat(cursor.getString(0)).isEqualTo("alice")
    assertThat(cursor.getLong(1)).isEqualTo(1000L)
    cursor.close()
  }

  @Test
  fun betsTable_insertAndReadBack() {
    val db = database.openHelper.writableDatabase
    val values = ContentValues().apply {
      put("prompt", "Who wins?")
      put("status", "PENDING_CONFIRMATION")
      put("created_at", System.currentTimeMillis())
    }
    val rowId = db.insert("bets", SQLiteDatabase.CONFLICT_ABORT, values)
    assertThat(rowId).isGreaterThan(0L)

    val cursor = db.query("SELECT prompt, status FROM bets WHERE id = ?", arrayOf(rowId))
    assertThat(cursor.moveToFirst()).isTrue()
    assertThat(cursor.getString(0)).isEqualTo("Who wins?")
    assertThat(cursor.getString(1)).isEqualTo("PENDING_CONFIRMATION")
    cursor.close()
  }

  @Test
  fun appSettingsTable_insertAndReadBack() {
    val db = database.openHelper.writableDatabase
    val values = ContentValues().apply {
      put("id", 1)
      put("admin_pin_hash", "admin_hash")
      put("recovery_code_hash", "recovery_hash")
    }
    val rowId = db.insert("app_settings", SQLiteDatabase.CONFLICT_ABORT, values)
    assertThat(rowId).isEqualTo(1L)

    val cursor = db.query(
      "SELECT admin_pin_hash FROM app_settings WHERE id = 1"
    )
    assertThat(cursor.moveToFirst()).isTrue()
    assertThat(cursor.getString(0)).isEqualTo("admin_hash")
    cursor.close()
  }

  @Test
  fun usersTable_usernameIsUnique() {
    val db = database.openHelper.writableDatabase
    val values = ContentValues().apply {
      put("username", "alice")
      put("pin_hash", "hash1")
      put("created_at", System.currentTimeMillis())
    }
    db.insert("users", SQLiteDatabase.CONFLICT_ABORT, values)

    val duplicateValues = ContentValues().apply {
      put("username", "alice")
      put("pin_hash", "hash2")
      put("created_at", System.currentTimeMillis())
    }
    try {
      db.insert("users", SQLiteDatabase.CONFLICT_ABORT, duplicateValues)
      // If we get here, the constraint was not enforced.
      throw AssertionError("Expected UNIQUE constraint violation for duplicate username")
    } catch (e: SQLiteConstraintException) {
      // Expected: unique constraint violation.
    }
  }

  @Test
  fun usersTable_usernameIsUniqueIgnoringCase() = runTest {
    val userDao = database.userDao()
    userDao.insert(User(username = "Jay", pinHash = "hash1", createdAt = 0L))

    val duplicate = runCatching {
      userDao.insert(User(username = "jay", pinHash = "hash2", createdAt = 0L))
    }

    assertThat(duplicate.exceptionOrNull()).isInstanceOf(SQLiteConstraintException::class.java)
    assertThat(userDao.getByUsername("JAY")?.username).isEqualTo("Jay")
  }

  @Test
  fun usersTable_avatarRefDefaultsToNull() {
    val db = database.openHelper.writableDatabase
    val values = ContentValues().apply {
      put("username", "alice")
      put("pin_hash", "hash")
      put("created_at", 0L)
    }
    val rowId = db.insert("users", SQLiteDatabase.CONFLICT_ABORT, values)

    val cursor = db.query("SELECT avatar_ref FROM users WHERE id = ?", arrayOf(rowId))
    assertThat(cursor.moveToFirst()).isTrue()
    assertThat(cursor.isNull(0)).isTrue()
    cursor.close()
  }

  @Test
  fun appSettingsTable_themePreferenceDefaultsToSystem() = runTest {
    val db = database.openHelper.writableDatabase
    val values = ContentValues().apply {
      put("id", 1)
      put("admin_pin_hash", "admin_hash")
      put("recovery_code_hash", "recovery_hash")
    }
    db.insert("app_settings", SQLiteDatabase.CONFLICT_ABORT, values)

    val settings = database.appSettingsDao().getSettings()
    assertThat(settings?.themePreference).isEqualTo(ThemePreference.SYSTEM)
  }

  @Test
  fun appSettingsTable_themePreferenceRoundTrips() = runTest {
    val dao = database.appSettingsDao()
    dao.insert(
      AppSettings(
        adminPinHash = "admin_hash",
        recoveryCodeHash = "recovery_hash",
        themePreference = ThemePreference.DARK,
      ),
    )

    val settings = dao.getSettings()
    assertThat(settings?.recoveryCodeHash).isEqualTo("recovery_hash")
    assertThat(settings?.themePreference).isEqualTo(ThemePreference.DARK)
  }

  @Test
  fun betParticipantsTable_userDeleteSetsUserIdToNull() = runTest {
    val userId = database.userDao()
      .insert(User(username = "alice", pinHash = "hash", createdAt = 0L))
    val betId = database.betDao()
      .insert(Bet(prompt = "Who wins?", status = BetStatus.RESOLVED, createdAt = 0L))
    val outcomeId = database.betOutcomeDao().insert(BetOutcome(betId = betId, outcomeText = "Us"))
    val participantId = database.betParticipantDao().insert(
      BetParticipant(betId = betId, userId = userId, outcomeId = outcomeId, wagerAmount = 100L),
    )

    database.userDao().delete(database.userDao().getById(userId)!!)

    val participants = database.betParticipantDao().getByBetId(betId)
    assertThat(participants).hasSize(1)
    assertThat(participants.single().id).isEqualTo(participantId)
    assertThat(participants.single().userId).isNull()
  }

  @Test
  fun daoAccessors_returnNonNull() {
    assertThat(database.userDao()).isNotNull()
    assertThat(database.betDao()).isNotNull()
    assertThat(database.betOutcomeDao()).isNotNull()
    assertThat(database.betParticipantDao()).isNotNull()
    assertThat(database.appSettingsDao()).isNotNull()
  }
}
