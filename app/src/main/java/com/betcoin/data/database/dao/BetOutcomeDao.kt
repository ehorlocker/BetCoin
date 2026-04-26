package com.betcoin.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.betcoin.data.database.entity.BetOutcome

/** Data access object for [BetOutcome] operations. */
@Dao
interface BetOutcomeDao {

  /** Inserts a new outcome and returns the auto-generated row ID. */
  @Insert
  suspend fun insert(outcome: BetOutcome): Long

  /** Inserts multiple outcomes and returns the list of auto-generated row IDs. */
  @Insert
  suspend fun insertAll(outcomes: List<BetOutcome>): List<Long>

  /** Returns all outcomes for the given [betId]. */
  @Query("SELECT * FROM bet_outcomes WHERE bet_id = :betId")
  suspend fun getByBetId(betId: Long): List<BetOutcome>

  /** Deletes all outcomes for the given [betId]. */
  @Query("DELETE FROM bet_outcomes WHERE bet_id = :betId")
  suspend fun deleteByBetId(betId: Long)
}
