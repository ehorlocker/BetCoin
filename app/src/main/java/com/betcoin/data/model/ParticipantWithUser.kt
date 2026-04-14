package com.betcoin.data.model

import androidx.room.Embedded
import androidx.room.Relation
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.BetParticipant
import com.betcoin.data.database.entity.User

/**
 * Composite query result that enriches a [BetParticipant] with the associated [User]
 * and chosen [BetOutcome].
 *
 * When a user has been deleted, [user] will be null and the original username is
 * preserved in [participant].[BetParticipant.deletedUsername].
 */
data class ParticipantWithUser(
  @Embedded val participant: BetParticipant,

  @Relation(parentColumn = "user_id", entityColumn = "id", entity = User::class)
  val user: User?,

  @Relation(parentColumn = "outcome_id", entityColumn = "id", entity = BetOutcome::class)
  val outcome: BetOutcome,
)
