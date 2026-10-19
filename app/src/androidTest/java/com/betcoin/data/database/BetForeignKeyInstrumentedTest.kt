package com.betcoin.data.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.betcoin.data.database.dao.BetDao
import com.betcoin.data.database.dao.BetOutcomeDao
import com.betcoin.data.database.dao.BetParticipantDao
import com.betcoin.data.database.dao.UserDao
import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.BetParticipant
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.BetStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the foreign keys on the bet tables (`bets`, `bet_outcomes`,
 * `bet_participants`): cascade, set-null and constraint-violation behavior.
 *
 * Uses an in-memory Room database so tests are hermetic and fast.
 */
@RunWith(AndroidJUnit4::class)
class BetForeignKeyInstrumentedTest {

  private lateinit var database: BetCoinDatabase
  private lateinit var betDao: BetDao
  private lateinit var betOutcomeDao: BetOutcomeDao
  private lateinit var betParticipantDao: BetParticipantDao
  private lateinit var userDao: UserDao

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    database = Room.inMemoryDatabaseBuilder(context, BetCoinDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    betDao = database.betDao()
    betOutcomeDao = database.betOutcomeDao()
    betParticipantDao = database.betParticipantDao()
    userDao = database.userDao()
  }

  @After
  fun tearDown() {
    database.close()
  }

  // --- Helpers ---

  private suspend fun insertBet(status: BetStatus = BetStatus.ACTIVE): Bet {
    val bet = Bet(prompt = "Will it rain?", status = status, createdAt = 1_000L)
    return bet.copy(id = betDao.insert(bet))
  }

  private suspend fun insertOutcome(betId: Long, text: String = "Yes"): Long =
    betOutcomeDao.insert(BetOutcome(betId = betId, outcomeText = text))

  private suspend fun insertUser(username: String = "alice"): User {
    val user = User(username = username, pinHash = "hashed_pin", createdAt = 1_000L)
    return user.copy(id = userDao.insert(user))
  }

  private fun createParticipant(
    betId: Long,
    userId: Long?,
    outcomeId: Long,
  ): BetParticipant = BetParticipant(
    betId = betId,
    userId = userId,
    outcomeId = outcomeId,
    wagerAmount = 100L,
  )

  private fun deleteOutcomeRaw(outcomeId: Long) {
    database.openHelper.writableDatabase
      .execSQL("DELETE FROM bet_outcomes WHERE id = ?", arrayOf<Any>(outcomeId))
  }

  private fun participantCount(): Int =
    database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM bet_participants").use {
      it.moveToFirst()
      it.getInt(0)
    }

  private fun outcomeCount(): Int =
    database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM bet_outcomes").use {
      it.moveToFirst()
      it.getInt(0)
    }

  // --- bet delete ---

  @Test
  fun deleteBet_cascadesToOutcomesAndParticipants() = runTest {
    val bet = insertBet()
    val other = insertBet()
    val user = insertUser()
    val outcomeId = insertOutcome(bet.id)
    val otherOutcomeId = insertOutcome(other.id)
    betParticipantDao.insert(createParticipant(bet.id, user.id, outcomeId))
    betParticipantDao.insert(createParticipant(other.id, user.id, otherOutcomeId))

    betDao.delete(bet)

    assertThat(betDao.getById(bet.id)).isNull()
    assertThat(betOutcomeDao.getByBetId(bet.id)).isEmpty()
    assertThat(betParticipantDao.getByBetId(bet.id)).isEmpty()
    assertThat(betOutcomeDao.getByBetId(other.id)).hasSize(1)
    assertThat(betParticipantDao.getByBetId(other.id)).hasSize(1)
  }

  // --- outcome delete ---

  /**
   * Deleting an outcome cascades to every participant who picked it. That silently removes locked
   * wagers with no refund. Today outcomes are only ever deleted together with their bet, so this
   * never happens in practice; this test pins the current schema behavior. See the BET-41 PR for
   * the proposal to switch to RESTRICT if outcome editing is ever added.
   */
  @Test
  fun deleteOutcome_cascadesToParticipantsWhoPickedIt() = runTest {
    val bet = insertBet()
    val alice = insertUser("alice")
    val bob = insertUser("bob")
    val yesId = insertOutcome(bet.id, "Yes")
    val noId = insertOutcome(bet.id, "No")
    betParticipantDao.insert(createParticipant(bet.id, alice.id, yesId))
    val bobParticipantId = betParticipantDao.insert(createParticipant(bet.id, bob.id, noId))

    deleteOutcomeRaw(yesId)

    val remaining = betParticipantDao.getByBetId(bet.id)
    assertThat(remaining.map { it.id }).containsExactly(bobParticipantId)
    assertThat(betOutcomeDao.getByBetId(bet.id).map { it.id }).containsExactly(noId)
  }

  @Test
  fun deleteOutcome_nullsWinningOutcomeId() = runTest {
    val bet = insertBet()
    val outcomeId = insertOutcome(bet.id)
    betDao.update(
      bet.copy(status = BetStatus.RESOLVED, winningOutcomeId = outcomeId, resolvedAt = 2_000L),
    )

    deleteOutcomeRaw(outcomeId)

    val reloaded = betDao.getById(bet.id)
    assertThat(reloaded).isNotNull()
    assertThat(reloaded!!.winningOutcomeId).isNull()
  }

  // --- user delete ---

  @Test
  fun deleteUser_nullsParticipantUserId() = runTest {
    val bet = insertBet()
    val user = insertUser()
    val outcomeId = insertOutcome(bet.id)
    val participantId = betParticipantDao.insert(createParticipant(bet.id, user.id, outcomeId))

    userDao.delete(user)

    val participant = betParticipantDao.getById(participantId)
    assertThat(participant).isNotNull()
    assertThat(participant!!.userId).isNull()
    assertThat(participant.deletedUsername).isNull()
    val withUser = betParticipantDao.getByBetIdWithUser(bet.id).single()
    assertThat(withUser.user).isNull()
  }

  // --- insert violations ---

  @Test
  fun insertParticipant_missingBet_fails() = runTest {
    val bet = insertBet()
    val user = insertUser()
    val outcomeId = insertOutcome(bet.id)

    assertThrows(SQLiteConstraintException::class.java) {
      runBlocking {
        betParticipantDao.insert(createParticipant(999L, user.id, outcomeId))
      }
    }
    assertThat(participantCount()).isEqualTo(0)
  }

  @Test
  fun insertParticipant_missingOutcome_fails() = runTest {
    val bet = insertBet()
    val user = insertUser()

    assertThrows(SQLiteConstraintException::class.java) {
      runBlocking {
        betParticipantDao.insert(createParticipant(bet.id, user.id, 999L))
      }
    }
    assertThat(participantCount()).isEqualTo(0)
  }

  @Test
  fun insertParticipant_missingUser_fails() = runTest {
    val bet = insertBet()
    val outcomeId = insertOutcome(bet.id)

    assertThrows(SQLiteConstraintException::class.java) {
      runBlocking {
        betParticipantDao.insert(createParticipant(bet.id, 999L, outcomeId))
      }
    }
    assertThat(participantCount()).isEqualTo(0)
  }

  @Test
  fun insertOutcome_missingBet_fails() = runTest {
    assertThrows(SQLiteConstraintException::class.java) {
      runBlocking { insertOutcome(999L) }
    }
    assertThat(outcomeCount()).isEqualTo(0)
  }

  @Test
  fun updateBet_winningOutcomeIdMissing_fails() = runTest {
    val bet = insertBet()

    assertThrows(SQLiteConstraintException::class.java) {
      runBlocking { betDao.update(bet.copy(winningOutcomeId = 999L)) }
    }
    assertThat(betDao.getById(bet.id)).isEqualTo(bet)
  }
}
