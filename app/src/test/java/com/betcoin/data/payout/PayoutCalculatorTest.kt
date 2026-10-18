package com.betcoin.data.payout

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class PayoutCalculatorTest {

  private val outcomeA = 10L
  private val outcomeB = 11L
  private val outcomeC = 12L

  private fun entry(id: Long, outcome: Long, wager: Long) = PayoutEntry(id, outcome, wager)

  private fun assertSumsToPot(entries: List<PayoutEntry>, result: PayoutResult) {
    assertThat(result.payouts.values.sum()).isEqualTo(entries.sumOf { it.wagerAmount })
  }

  @Test
  fun proportionalSplit_winnersShareThePotByWager() {
    val entries = listOf(
      entry(1, outcomeA, 100),
      entry(2, outcomeA, 300),
      entry(3, outcomeB, 400),
    )

    val result = PayoutCalculator.calculate(entries, outcomeA)

    assertThat(result.isPush).isFalse()
    assertThat(result.payouts).containsExactly(1L, 200L, 2L, 600L, 3L, 0L)
    assertSumsToPot(entries, result)
  }

  @Test
  fun remainder_goesToWinnerWithLargestWager() {
    // Pot 100, winning wagers 30: shares are 10*100/30 = 33 and 20*100/30 = 66, remainder 1.
    val entries = listOf(
      entry(1, outcomeA, 10),
      entry(2, outcomeA, 20),
      entry(3, outcomeB, 70),
    )

    val result = PayoutCalculator.calculate(entries, outcomeA)

    assertThat(result.payouts).containsExactly(1L, 33L, 2L, 67L, 3L, 0L)
    assertSumsToPot(entries, result)
  }

  @Test
  fun remainder_tieOnLargestWager_goesToLowestParticipantId() {
    // Pot 100, three winners at 10 each: shares are 33, remainder 1.
    val entries = listOf(
      entry(7, outcomeA, 10),
      entry(4, outcomeA, 10),
      entry(9, outcomeA, 10),
      entry(2, outcomeB, 70),
    )

    val result = PayoutCalculator.calculate(entries, outcomeA)

    assertThat(result.payouts).containsExactly(7L, 33L, 4L, 34L, 9L, 33L, 2L, 0L)
    assertSumsToPot(entries, result)
  }

  @Test
  fun push_nobodyPickedWinningOutcome_everyoneRefunded() {
    val entries = listOf(
      entry(1, outcomeA, 50),
      entry(2, outcomeB, 75),
    )

    val result = PayoutCalculator.calculate(entries, outcomeC)

    assertThat(result.isPush).isTrue()
    assertThat(result.payouts).containsExactly(1L, 50L, 2L, 75L)
    assertSumsToPot(entries, result)
  }

  @Test
  fun singleWinner_takesWholePot() {
    val entries = listOf(
      entry(1, outcomeA, 25),
      entry(2, outcomeB, 100),
      entry(3, outcomeC, 333),
    )

    val result = PayoutCalculator.calculate(entries, outcomeA)

    assertThat(result.isPush).isFalse()
    assertThat(result.payouts).containsExactly(1L, 458L, 2L, 0L, 3L, 0L)
  }

  @Test
  fun everyonePickedWinningOutcome_eachGetsWagerBack() {
    val entries = listOf(
      entry(1, outcomeA, 40),
      entry(2, outcomeA, 60),
    )

    val result = PayoutCalculator.calculate(entries, outcomeA)

    assertThat(result.isPush).isFalse()
    assertThat(result.payouts).containsExactly(1L, 40L, 2L, 60L)
  }

  @Test
  fun largeValues_doNotOverflow() {
    // Balances far beyond anything realistic (starting balance is 1,000): wager * pot is ~3e18.
    val entries = listOf(
      entry(1, outcomeA, 1_000_000_000L),
      entry(2, outcomeA, 999_999_999L),
      entry(3, outcomeB, 1_000_000_001L),
    )

    val result = PayoutCalculator.calculate(entries, outcomeA)

    // Pot 3,000,000,000 over winning wagers 1,999,999,999.
    assertThat(result.payouts[1]).isEqualTo(1_500_000_001L)
    assertThat(result.payouts[2]).isEqualTo(1_499_999_999L)
    assertThat(result.payouts[3]).isEqualTo(0L)
    assertSumsToPot(entries, result)
  }

  @Test
  fun manySmallWinners_sumAlwaysEqualsPot() {
    val entries = (1L..13L).map { entry(it, if (it % 3 == 0L) outcomeB else outcomeA, it * 7) }

    val result = PayoutCalculator.calculate(entries, outcomeA)

    assertSumsToPot(entries, result)
    entries.filter { it.outcomeId == outcomeB }.forEach {
      assertThat(result.payouts[it.participantId]).isEqualTo(0L)
    }
  }

  @Test
  fun nonPositiveWager_throws() {
    assertThrows(IllegalArgumentException::class.java) {
      PayoutCalculator.calculate(listOf(entry(1, outcomeA, 0)), outcomeA)
    }
  }

  @Test
  fun duplicateParticipantId_throws() {
    assertThrows(IllegalArgumentException::class.java) {
      PayoutCalculator.calculate(
        listOf(entry(1, outcomeA, 10), entry(1, outcomeB, 10)),
        outcomeA,
      )
    }
  }
}
