package com.betcoin.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.betcoin.data.database.entity.BetParticipant
import com.betcoin.data.model.ParticipantWithUser

/** Data access object for [BetParticipant] operations. */
@Dao
interface BetParticipantDao {

  /** Inserts a new participant and returns the auto-generated row ID. */
  @Insert
  suspend fun insert(participant: BetParticipant): Long

  /** Returns all participants for the given [betId]. */
  @Query("SELECT * FROM bet_participants WHERE bet_id = :betId")
  suspend fun getByBetId(betId: Long): List<BetParticipant>

  /**
   * Returns all participants for the given [betId] joined with their [User] and
   * chosen [BetOutcome].
   *
   * This is a @Transaction query because it loads participants together with their
   * related user and outcome entities.
   */
  @Transaction
  @Query("SELECT * FROM bet_participants WHERE bet_id = :betId")
  suspend fun getByBetIdWithUser(betId: Long): List<ParticipantWithUser>

  /**
   * Returns the participant for the given [userId] and [betId], or null if the user has
   * not joined this bet. Useful for duplicate-participation checks.
   */
  @Query("SELECT * FROM bet_participants WHERE user_id = :userId AND bet_id = :betId")
  suspend fun getByUserIdAndBetId(userId: Long, betId: Long): BetParticipant?

  /** Returns all participation records for the given [userId]. */
  @Query("SELECT * FROM bet_participants WHERE user_id = :userId")
  suspend fun getByUserId(userId: Long): List<BetParticipant>

  /** Updates an existing participant (matched by primary key). */
  @Update
  suspend fun update(participant: BetParticipant)

  /** Deletes the given participant (matched by primary key). */
  @Delete
  suspend fun delete(participant: BetParticipant)

  /** Deletes all participants for the given [betId]. */
  @Query("DELETE FROM bet_participants WHERE bet_id = :betId")
  suspend fun deleteByBetId(betId: Long)
}
