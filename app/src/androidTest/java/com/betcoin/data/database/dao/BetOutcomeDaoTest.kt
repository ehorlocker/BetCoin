package com.betcoin.data.database.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.betcoin.data.database.BetCoinDatabase
import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.model.BetStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [BetOutcomeDao].
 *
 * Uses an in-memory Room database so tests are hermetic and fast.
 */
@RunWith(AndroidJUnit4::class)
class BetOutcomeDaoTest {

  private lateinit var database: BetCoinDatabase
  private lateinit var betDao: BetDao
  private lateinit var betOutcomeDao: BetOutcomeDao

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    database = Room.inMemoryDatabaseBuilder(context, BetCoinDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    betDao = database.betDao()
    betOutcomeDao = database.betOutcomeDao()
  }

  @After
  fun tearDown() {
    database.close()
  }

  // --- Helpers ---

  private suspend fun insertBet(): Long = betDao.insert(
    Bet(prompt = "Will it rain?", status = BetStatus.ACTIVE, createdAt = 1_000L),
  )

  private fun createOutcome(betId: Long, text: String = "Yes"): BetOutcome =
    BetOutcome(betId = betId, outcomeText = text)

  // --- insert ---

  @Test
  fun insert_returnsPositiveId() = runTest {
    val id = betOutcomeDao.insert(createOutcome(insertBet()))

    assertThat(id).isGreaterThan(0L)
  }

  // --- insertAll ---

  @Test
  fun insertAll_returnsIdsInOrder() = runTest {
    val betId = insertBet()

    val ids = betOutcomeDao.insertAll(
      listOf(
        createOutcome(betId, "A"),
        createOutcome(betId, "B"),
        createOutcome(betId, "C"),
      ),
    )

    assertThat(ids).hasSize(3)
    assertThat(ids.toSet()).hasSize(3)
    assertThat(ids).isInStrictOrder()
    val stored = betOutcomeDao.getByBetId(betId).associateBy { it.id }
    assertThat(ids.map { stored[it]!!.outcomeText }).containsExactly("A", "B", "C").inOrder()
  }

  // --- getByBetId ---

  @Test
  fun getByBetId_returnsOnlyThatBetsOutcomes() = runTest {
    val betA = insertBet()
    val betB = insertBet()
    betOutcomeDao.insert(createOutcome(betA, "A1"))
    betOutcomeDao.insert(createOutcome(betA, "A2"))
    betOutcomeDao.insert(createOutcome(betB, "B1"))

    val result = betOutcomeDao.getByBetId(betA)

    assertThat(result.map { it.outcomeText }).containsExactly("A1", "A2")
    assertThat(result.all { it.betId == betA }).isTrue()
  }

  @Test
  fun getByBetId_noOutcomes_returnsEmpty() = runTest {
    assertThat(betOutcomeDao.getByBetId(insertBet())).isEmpty()
  }

  // --- deleteByBetId ---

  @Test
  fun deleteByBetId_removesOnlyThatBetsOutcomes() = runTest {
    val betA = insertBet()
    val betB = insertBet()
    betOutcomeDao.insert(createOutcome(betA, "A1"))
    betOutcomeDao.insert(createOutcome(betB, "B1"))

    betOutcomeDao.deleteByBetId(betA)

    assertThat(betOutcomeDao.getByBetId(betA)).isEmpty()
    assertThat(betOutcomeDao.getByBetId(betB).map { it.outcomeText }).containsExactly("B1")
  }
}
