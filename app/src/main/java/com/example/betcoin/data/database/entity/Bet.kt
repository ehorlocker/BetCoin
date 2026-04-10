package com.example.betcoin.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.example.betcoin.data.model.BetStatus

/**
 * Represents a bet that players can wager BetCoin on.
 *
 * The [winningOutcomeId] foreign key references [BetOutcome.id] and is null until the bet is
 * resolved.
 */
@Entity(
  tableName = "bets",
  foreignKeys = [
    ForeignKey(
      entity = BetOutcome::class,
      parentColumns = ["id"],
      childColumns = ["winning_outcome_id"],
      onDelete = ForeignKey.SET_NULL,
    ),
  ],
)
data class Bet(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0L,

  @ColumnInfo(name = "prompt")
  val prompt: String,

  @ColumnInfo(name = "status")
  val status: BetStatus,

  @ColumnInfo(name = "winning_outcome_id", index = true)
  val winningOutcomeId: Long? = null,

  @ColumnInfo(name = "created_at")
  val createdAt: Long,

  @ColumnInfo(name = "resolved_at")
  val resolvedAt: Long? = null,
)
