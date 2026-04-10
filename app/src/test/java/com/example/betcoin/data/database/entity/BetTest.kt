package com.example.betcoin.data.database.entity

import com.example.betcoin.data.model.BetStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [Bet] entity data class construction and default values.
 */
class BetTest {

  @Test
  fun defaultValues_areCorrect() {
    val bet = Bet(
      prompt = "Who wins?",
      status = BetStatus.PENDING_CONFIRMATION,
      createdAt = 1000L,
    )

    assertThat(bet.id).isEqualTo(0L)
    assertThat(bet.winningOutcomeId).isNull()
    assertThat(bet.resolvedAt).isNull()
  }

  @Test
  fun requiredFields_areSetCorrectly() {
    val bet = Bet(
      prompt = "Who wins the game?",
      status = BetStatus.ACTIVE,
      createdAt = 1712793600000L,
    )

    assertThat(bet.prompt).isEqualTo("Who wins the game?")
    assertThat(bet.status).isEqualTo(BetStatus.ACTIVE)
    assertThat(bet.createdAt).isEqualTo(1712793600000L)
  }

  @Test
  fun allFields_areSetCorrectly() {
    val bet = Bet(
      id = 5L,
      prompt = "Best pizza topping?",
      status = BetStatus.RESOLVED,
      winningOutcomeId = 10L,
      createdAt = 1000L,
      resolvedAt = 2000L,
    )

    assertThat(bet.id).isEqualTo(5L)
    assertThat(bet.prompt).isEqualTo("Best pizza topping?")
    assertThat(bet.status).isEqualTo(BetStatus.RESOLVED)
    assertThat(bet.winningOutcomeId).isEqualTo(10L)
    assertThat(bet.resolvedAt).isEqualTo(2000L)
  }

  @Test
  fun winningOutcomeId_isNullable() {
    val bet = Bet(
      prompt = "Test",
      status = BetStatus.PENDING_CONFIRMATION,
      winningOutcomeId = null,
      createdAt = 1000L,
    )

    assertThat(bet.winningOutcomeId).isNull()
  }

  @Test
  fun resolvedAt_isNullable() {
    val bet = Bet(
      prompt = "Test",
      status = BetStatus.CANCELLED,
      resolvedAt = null,
      createdAt = 1000L,
    )

    assertThat(bet.resolvedAt).isNull()
  }

  @Test
  fun allBetStatuses_canBeAssigned() {
    for (status in BetStatus.values()) {
      val bet = Bet(prompt = "Test", status = status, createdAt = 1000L)
      assertThat(bet.status).isEqualTo(status)
    }
  }
}
