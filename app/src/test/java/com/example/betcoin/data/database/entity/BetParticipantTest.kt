package com.example.betcoin.data.database.entity

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [BetParticipant] entity data class construction and default values.
 */
class BetParticipantTest {

  @Test
  fun defaultValues_areCorrect() {
    val participant = BetParticipant(
      betId = 1L,
      outcomeId = 2L,
      wagerAmount = 500L,
    )

    assertThat(participant.id).isEqualTo(0L)
    assertThat(participant.userId).isNull()
    assertThat(participant.payout).isNull()
    assertThat(participant.deletedUsername).isNull()
  }

  @Test
  fun requiredFields_areSetCorrectly() {
    val participant = BetParticipant(
      betId = 3L,
      userId = 7L,
      outcomeId = 4L,
      wagerAmount = 250L,
    )

    assertThat(participant.betId).isEqualTo(3L)
    assertThat(participant.userId).isEqualTo(7L)
    assertThat(participant.outcomeId).isEqualTo(4L)
    assertThat(participant.wagerAmount).isEqualTo(250L)
  }

  @Test
  fun allFields_areSetCorrectly() {
    val participant = BetParticipant(
      id = 100L,
      betId = 5L,
      userId = 10L,
      outcomeId = 20L,
      wagerAmount = 1000L,
      payout = 2000L,
      deletedUsername = "old_user",
    )

    assertThat(participant.id).isEqualTo(100L)
    assertThat(participant.betId).isEqualTo(5L)
    assertThat(participant.userId).isEqualTo(10L)
    assertThat(participant.outcomeId).isEqualTo(20L)
    assertThat(participant.wagerAmount).isEqualTo(1000L)
    assertThat(participant.payout).isEqualTo(2000L)
    assertThat(participant.deletedUsername).isEqualTo("old_user")
  }

  @Test
  fun userId_isNullable() {
    val participant = BetParticipant(
      betId = 1L,
      userId = null,
      outcomeId = 2L,
      wagerAmount = 100L,
      deletedUsername = "deleted_player",
    )

    assertThat(participant.userId).isNull()
    assertThat(participant.deletedUsername).isEqualTo("deleted_player")
  }

  @Test
  fun currencyFields_useLongType() {
    val participant = BetParticipant(
      betId = 1L,
      outcomeId = 2L,
      wagerAmount = Long.MAX_VALUE,
      payout = Long.MAX_VALUE,
    )

    assertThat(participant.wagerAmount).isEqualTo(Long.MAX_VALUE)
    assertThat(participant.payout).isEqualTo(Long.MAX_VALUE)
  }
}
