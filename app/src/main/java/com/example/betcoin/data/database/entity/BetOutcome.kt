package com.example.betcoin.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Represents a possible outcome for a [Bet].
 *
 * Each bet has two or more outcomes that participants can pick from.
 */
@Entity(
  tableName = "bet_outcomes",
  foreignKeys = [
    ForeignKey(
      entity = Bet::class,
      parentColumns = ["id"],
      childColumns = ["bet_id"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
data class BetOutcome(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0L,

  @ColumnInfo(name = "bet_id", index = true)
  val betId: Long,

  @ColumnInfo(name = "outcome_text")
  val outcomeText: String,
)
