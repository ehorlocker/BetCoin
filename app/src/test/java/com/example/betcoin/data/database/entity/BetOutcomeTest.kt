package com.example.betcoin.data.database.entity

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [BetOutcome] entity data class construction.
 */
class BetOutcomeTest {

  @Test
  fun defaultId_isZero() {
    val outcome = BetOutcome(betId = 1L, outcomeText = "Option A")

    assertThat(outcome.id).isEqualTo(0L)
  }

  @Test
  fun requiredFields_areSetCorrectly() {
    val outcome = BetOutcome(betId = 5L, outcomeText = "Team Alpha")

    assertThat(outcome.betId).isEqualTo(5L)
    assertThat(outcome.outcomeText).isEqualTo("Team Alpha")
  }

  @Test
  fun allFields_areSetCorrectly() {
    val outcome = BetOutcome(id = 10L, betId = 3L, outcomeText = "Pepperoni")

    assertThat(outcome.id).isEqualTo(10L)
    assertThat(outcome.betId).isEqualTo(3L)
    assertThat(outcome.outcomeText).isEqualTo("Pepperoni")
  }
}
