package com.betcoin.data.model

import com.betcoin.data.database.entity.Bet
import com.betcoin.data.database.entity.BetOutcome
import com.betcoin.data.database.entity.BetParticipant
import com.betcoin.data.database.entity.User
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [BetWithDetails] composite data class.
 *
 * Room annotations have CLASS retention and are not available at runtime, so annotation
 * correctness is verified at compile time by the Room annotation processor. These tests
 * verify the data class structure and field types.
 */
class BetWithDetailsTest {

  private val bet = Bet(
    id = 1L,
    prompt = "Who wins the game?",
    status = BetStatus.ACTIVE,
    createdAt = 1000L,
  )

  private val outcome1 = BetOutcome(id = 10L, betId = 1L, outcomeText = "Team A")
  private val outcome2 = BetOutcome(id = 11L, betId = 1L, outcomeText = "Team B")

  private val user1 = User(id = 100L, username = "alice", pinHash = "h1", createdAt = 1000L)
  private val user2 = User(id = 101L, username = "bob", pinHash = "h2", createdAt = 1000L)

  private val participant1 = BetParticipant(
    id = 1L, betId = 1L, userId = 100L, outcomeId = 10L, wagerAmount = 100L,
  )
  private val participant2 = BetParticipant(
    id = 2L, betId = 1L, userId = 101L, outcomeId = 11L, wagerAmount = 200L,
  )

  private val pwu1 = ParticipantWithUser(
    participant = participant1, user = user1, outcome = outcome1,
  )
  private val pwu2 = ParticipantWithUser(
    participant = participant2, user = user2, outcome = outcome2,
  )

  @Test
  fun construction_setsAllFields() {
    val bwd = BetWithDetails(
      bet = bet,
      outcomes = listOf(outcome1, outcome2),
      participants = listOf(pwu1, pwu2),
    )

    assertThat(bwd.bet).isEqualTo(bet)
    assertThat(bwd.outcomes).containsExactly(outcome1, outcome2)
    assertThat(bwd.participants).containsExactly(pwu1, pwu2)
  }

  @Test
  fun emptyLists_areValid() {
    val bwd = BetWithDetails(
      bet = bet,
      outcomes = emptyList(),
      participants = emptyList(),
    )

    assertThat(bwd.outcomes).isEmpty()
    assertThat(bwd.participants).isEmpty()
  }

  @Test
  fun equality_worksCorrectly() {
    val bwd1 = BetWithDetails(
      bet = bet, outcomes = listOf(outcome1), participants = listOf(pwu1),
    )
    val bwd2 = BetWithDetails(
      bet = bet, outcomes = listOf(outcome1), participants = listOf(pwu1),
    )

    assertThat(bwd1).isEqualTo(bwd2)
    assertThat(bwd1.hashCode()).isEqualTo(bwd2.hashCode())
  }

  @Test
  fun copy_worksCorrectly() {
    val bwd = BetWithDetails(
      bet = bet,
      outcomes = listOf(outcome1, outcome2),
      participants = listOf(pwu1, pwu2),
    )
    val copied = bwd.copy(participants = emptyList())

    assertThat(copied.bet).isEqualTo(bet)
    assertThat(copied.outcomes).containsExactly(outcome1, outcome2)
    assertThat(copied.participants).isEmpty()
  }

  @Test
  fun structure_hasBetField() {
    val field = BetWithDetails::class.java.getDeclaredField("bet")
    assertThat(field.type).isEqualTo(Bet::class.java)
  }

  @Test
  fun structure_hasOutcomesField() {
    val field = BetWithDetails::class.java.getDeclaredField("outcomes")
    assertThat(field.type).isEqualTo(List::class.java)
  }

  @Test
  fun structure_hasParticipantsField() {
    val field = BetWithDetails::class.java.getDeclaredField("participants")
    assertThat(field.type).isEqualTo(List::class.java)
  }

  @Test
  fun structure_hasThreeFields() {
    val dataFields = BetWithDetails::class.java.declaredFields
      .filter { !it.isSynthetic && !it.name.startsWith("$") && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
    assertThat(dataFields).hasSize(3)
  }

  @Test
  fun structure_isDataClass() {
    val copyMethod = BetWithDetails::class.java.declaredMethods
      .any { it.name == "copy" }
    assertThat(copyMethod).isTrue()
  }
}
