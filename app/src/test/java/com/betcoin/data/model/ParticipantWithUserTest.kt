package com.betcoin.data.model

import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.BetParticipant
import com.betcoin.data.database.entity.User
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [ParticipantWithUser] composite data class.
 *
 * Room annotations have CLASS retention and are not available at runtime, so annotation
 * correctness is verified at compile time by the Room annotation processor. These tests
 * verify the data class structure and field types.
 */
class ParticipantWithUserTest {

  private val participant = BetParticipant(
    id = 1L,
    betId = 10L,
    userId = 100L,
    outcomeId = 50L,
    wagerAmount = 200L,
  )

  private val user = User(
    id = 100L,
    username = "alice",
    pinHash = "hash123",
    createdAt = 1000L,
  )

  private val outcome = BetOutcome(
    id = 50L,
    betId = 10L,
    outcomeText = "Team A wins",
  )

  @Test
  fun construction_setsAllFields() {
    val pwu = ParticipantWithUser(
      participant = participant,
      user = user,
      outcome = outcome,
    )

    assertThat(pwu.participant).isEqualTo(participant)
    assertThat(pwu.user).isEqualTo(user)
    assertThat(pwu.outcome).isEqualTo(outcome)
  }

  @Test
  fun user_isNullable_forDeletedUser() {
    val deletedParticipant = participant.copy(userId = null, deletedUsername = "bob")
    val pwu = ParticipantWithUser(
      participant = deletedParticipant,
      user = null,
      outcome = outcome,
    )

    assertThat(pwu.user).isNull()
    assertThat(pwu.participant.deletedUsername).isEqualTo("bob")
  }

  @Test
  fun deletedUsername_accessedViaParticipant() {
    val deletedParticipant = participant.copy(userId = null, deletedUsername = "charlie")
    val pwu = ParticipantWithUser(
      participant = deletedParticipant,
      user = null,
      outcome = outcome,
    )

    assertThat(pwu.participant.deletedUsername).isEqualTo("charlie")
  }

  @Test
  fun equality_worksCorrectly() {
    val pwu1 = ParticipantWithUser(participant = participant, user = user, outcome = outcome)
    val pwu2 = ParticipantWithUser(participant = participant, user = user, outcome = outcome)

    assertThat(pwu1).isEqualTo(pwu2)
    assertThat(pwu1.hashCode()).isEqualTo(pwu2.hashCode())
  }

  @Test
  fun copy_worksCorrectly() {
    val pwu = ParticipantWithUser(participant = participant, user = user, outcome = outcome)
    val copied = pwu.copy(user = null)

    assertThat(copied.user).isNull()
    assertThat(copied.participant).isEqualTo(participant)
    assertThat(copied.outcome).isEqualTo(outcome)
  }

  @Test
  fun structure_hasParticipantField() {
    val field = ParticipantWithUser::class.java.getDeclaredField("participant")
    assertThat(field.type).isEqualTo(BetParticipant::class.java)
  }

  @Test
  fun structure_hasUserField() {
    val field = ParticipantWithUser::class.java.getDeclaredField("user")
    assertThat(field.type).isEqualTo(User::class.java)
  }

  @Test
  fun structure_hasOutcomeField() {
    val field = ParticipantWithUser::class.java.getDeclaredField("outcome")
    assertThat(field.type).isEqualTo(BetOutcome::class.java)
  }

  @Test
  fun structure_hasThreeFields() {
    val dataFields = ParticipantWithUser::class.java.declaredFields
      .filter { !it.isSynthetic && !it.name.startsWith("$") && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
    assertThat(dataFields).hasSize(3)
  }

  @Test
  fun structure_isDataClass() {
    val copyMethod = ParticipantWithUser::class.java.declaredMethods
      .any { it.name == "copy" }
    assertThat(copyMethod).isTrue()
  }
}
