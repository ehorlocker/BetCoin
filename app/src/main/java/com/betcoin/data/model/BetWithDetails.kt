package com.betcoin.data.model

import androidx.room.Embedded
import androidx.room.Relation
import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.BetParticipant

/**
 * Composite query result that combines a [Bet] with all its [BetOutcome]s
 * and [ParticipantWithUser] entries.
 *
 * Used by DAO queries to load a complete bet view in a single operation.
 */
data class BetWithDetails(
  @Embedded val bet: Bet,

  @Relation(parentColumn = "id", entityColumn = "bet_id")
  val outcomes: List<BetOutcome>,

  @Relation(
    entity = BetParticipant::class,
    parentColumn = "id",
    entityColumn = "bet_id",
  )
  val participants: List<ParticipantWithUser>,
)
