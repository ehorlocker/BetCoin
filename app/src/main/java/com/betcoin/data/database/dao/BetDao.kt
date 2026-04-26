package com.betcoin.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.betcoin.data.database.entity.Bet
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.BetWithDetails
import kotlinx.coroutines.flow.Flow

/** Data access object for [Bet] operations. */
@Dao
interface BetDao {

  /** Inserts a new bet and returns the auto-generated row ID. */
  @Insert
  suspend fun insert(bet: Bet): Long

  /** Returns the bet with the given [betId], or null if not found. */
  @Query("SELECT * FROM bets WHERE id = :betId")
  suspend fun getById(betId: Long): Bet?

  /** Returns a [Flow] of bets matching the given [status]. */
  @Query("SELECT * FROM bets WHERE status = :status ORDER BY created_at DESC")
  fun getByStatus(status: BetStatus): Flow<List<Bet>>

  /** Returns a [Flow] of all bets ordered by creation date descending. */
  @Query("SELECT * FROM bets ORDER BY created_at DESC")
  fun getAll(): Flow<List<Bet>>

  /** Returns all pending-confirmation bets (non-Flow, for interrupted-bet recovery). */
  @Query("SELECT * FROM bets WHERE status = :status")
  suspend fun getPendingBets(status: BetStatus = BetStatus.PENDING_CONFIRMATION): List<Bet>

  /** Updates an existing bet (matched by primary key). */
  @Update
  suspend fun update(bet: Bet)

  /** Deletes the given bet (matched by primary key). */
  @Delete
  suspend fun delete(bet: Bet)

  /**
   * Returns a [Flow] that emits a [BetWithDetails] for the given [betId], or null if
   * not found.
   *
   * This is a @Transaction query because it loads the bet together with its related
   * outcomes and participants.
   */
  @Transaction
  @Query("SELECT * FROM bets WHERE id = :betId")
  fun getByIdWithDetails(betId: Long): Flow<BetWithDetails?>

  /**
   * Returns a [Flow] of all bets with their full details (outcomes and participants),
   * ordered by creation date descending.
   */
  @Transaction
  @Query("SELECT * FROM bets ORDER BY created_at DESC")
  fun getAllWithDetails(): Flow<List<BetWithDetails>>

  /**
   * Returns a [Flow] of bets with full details filtered by [status],
   * ordered by creation date descending.
   */
  @Transaction
  @Query("SELECT * FROM bets WHERE status = :status ORDER BY created_at DESC")
  fun getByStatusWithDetails(status: BetStatus): Flow<List<BetWithDetails>>
}
