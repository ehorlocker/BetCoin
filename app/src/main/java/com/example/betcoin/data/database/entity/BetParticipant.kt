package com.example.betcoin.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Represents a user's participation in a [Bet].
 *
 * The [userId] is nullable to support user deletion: when a user is deleted, their participation
 * records are preserved with [deletedUsername] populated and [userId] set to null.
 *
 * All currency values are stored as [Long] (integer BetCoin).
 */
@Entity(
  tableName = "bet_participants",
  foreignKeys = [
    ForeignKey(
      entity = Bet::class,
      parentColumns = ["id"],
      childColumns = ["bet_id"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = User::class,
      parentColumns = ["id"],
      childColumns = ["user_id"],
      onDelete = ForeignKey.SET_NULL,
    ),
    ForeignKey(
      entity = BetOutcome::class,
      parentColumns = ["id"],
      childColumns = ["outcome_id"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
data class BetParticipant(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0L,

  @ColumnInfo(name = "bet_id", index = true)
  val betId: Long,

  @ColumnInfo(name = "user_id", index = true)
  val userId: Long? = null,

  @ColumnInfo(name = "outcome_id", index = true)
  val outcomeId: Long,

  @ColumnInfo(name = "wager_amount")
  val wagerAmount: Long,

  @ColumnInfo(name = "payout")
  val payout: Long? = null,

  @ColumnInfo(name = "deleted_username")
  val deletedUsername: String? = null,
)
