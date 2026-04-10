package com.example.betcoin.data.database.converter

import com.example.betcoin.data.model.BetStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [BetStatusConverter] Room TypeConverter.
 */
class BetStatusConverterTest {

  private val converter = BetStatusConverter()

  @Test
  fun fromBetStatus_convertsPendingConfirmationToString() {
    val result = converter.fromBetStatus(BetStatus.PENDING_CONFIRMATION)
    assertThat(result).isEqualTo("PENDING_CONFIRMATION")
  }

  @Test
  fun fromBetStatus_convertsActiveToString() {
    val result = converter.fromBetStatus(BetStatus.ACTIVE)
    assertThat(result).isEqualTo("ACTIVE")
  }

  @Test
  fun fromBetStatus_convertsResolvedToString() {
    val result = converter.fromBetStatus(BetStatus.RESOLVED)
    assertThat(result).isEqualTo("RESOLVED")
  }

  @Test
  fun fromBetStatus_convertsCancelledToString() {
    val result = converter.fromBetStatus(BetStatus.CANCELLED)
    assertThat(result).isEqualTo("CANCELLED")
  }

  @Test
  fun toBetStatus_convertsStringToPendingConfirmation() {
    val result = converter.toBetStatus("PENDING_CONFIRMATION")
    assertThat(result).isEqualTo(BetStatus.PENDING_CONFIRMATION)
  }

  @Test
  fun toBetStatus_convertsStringToActive() {
    val result = converter.toBetStatus("ACTIVE")
    assertThat(result).isEqualTo(BetStatus.ACTIVE)
  }

  @Test
  fun toBetStatus_convertsStringToResolved() {
    val result = converter.toBetStatus("RESOLVED")
    assertThat(result).isEqualTo(BetStatus.RESOLVED)
  }

  @Test
  fun toBetStatus_convertsStringToCancelled() {
    val result = converter.toBetStatus("CANCELLED")
    assertThat(result).isEqualTo(BetStatus.CANCELLED)
  }

  @Test
  fun roundTrip_allValuesConvertCorrectly() {
    for (status in BetStatus.values()) {
      val asString = converter.fromBetStatus(status)
      val backToStatus = converter.toBetStatus(asString)
      assertThat(backToStatus).isEqualTo(status)
    }
  }
}
