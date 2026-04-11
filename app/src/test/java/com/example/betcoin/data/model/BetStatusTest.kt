package com.example.betcoin.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [BetStatus] enum values.
 */
class BetStatusTest {

  @Test
  fun betStatus_hasFourValues() {
    val values = BetStatus.values()
    assertThat(values).hasLength(4)
  }

  @Test
  fun betStatus_containsPendingConfirmation() {
    val status = BetStatus.valueOf("PENDING_CONFIRMATION")
    assertThat(status).isEqualTo(BetStatus.PENDING_CONFIRMATION)
  }

  @Test
  fun betStatus_containsActive() {
    val status = BetStatus.valueOf("ACTIVE")
    assertThat(status).isEqualTo(BetStatus.ACTIVE)
  }

  @Test
  fun betStatus_containsResolved() {
    val status = BetStatus.valueOf("RESOLVED")
    assertThat(status).isEqualTo(BetStatus.RESOLVED)
  }

  @Test
  fun betStatus_containsCancelled() {
    val status = BetStatus.valueOf("CANCELLED")
    assertThat(status).isEqualTo(BetStatus.CANCELLED)
  }
}
