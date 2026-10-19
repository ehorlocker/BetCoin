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
    assertThat(result.isCapped).isFalse()
    assertThat(result.payouts).containsExactly(1L, 200L, 2L, 600L, 3L, 0L)
    assertSumsToPot(entries, result)
  }

  @Test
  fun remainder_goesToWinnerWithLargestWager() {
    // Pot 37, winning wagers 30: shares are 10*37/30 = 12 and 20*37/30 = 24, remainder 1.
    val entries = listOf(
      entry(1, outcomeA, 10),
      entry(2, outcomeA, 20),
      entry(3, outcomeB, 7),
    )

    val result = PayoutCalculator.calculate(entries, outcomeA)

    assertThat(result.isCapped).isFalse()
    assertThat(result.payouts).containsExactly(1L, 12L, 2L, 25L, 3L, 0L)
    assertSumsToPot(entries, result)
  }

  @Test
  fun remainder_tieOnLargestWager_goesToLowestParticipantId() {
    // Pot 40, three winners at 10 each: shares are 13, remainder 1.
    val entries = listOf(
      entry(7, outcomeA, 10),
      entry(4, outcomeA, 10),
      entry(9, outcomeA, 10),
      entry(2, outcomeB, 10),
    )

    val result = PayoutCalculator.calculate(entries, outcomeA)

    assertThat(result.isCapped).isFalse()
    assertThat(result.payouts).containsExactly(7L, 13L, 4L, 14L, 9L, 13L, 2L, 0L)
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
    assertThat(result.isCapped).isFalse()
    assertThat(result.payouts).containsExactly(1L, 50L, 2L, 75L)
    assertSumsToPot(entries, result)
  }

  @Test
  fun singleWinner_uncapped_takesWholePot() {
    val entries = listOf(
      entry(1, outcomeA, 300),
      entry(2, outcomeB, 100),
      entry(3, outcomeC, 133),
    )

    val result = PayoutCalculator.calculate(entries, outcomeA)

    assertThat(result.isPush).isFalse()
    assertThat(result.isCapped).isFalse()
    assertThat(result.payouts).containsExactly(1L, 533L, 2L, 0L, 3L, 0L)
  }

  @Test
  fun everyonePickedWinningOutcome_eachGetsWagerBack() {
    val entries = listOf(
      entry(1, outcomeA, 40),
      entry(2, outcomeA, 60),
    )

    val result = PayoutCalculator.calculate(entries, outcomeA)

    assertThat(result.isPush).isFalse()
    assertThat(result.isCapped).isFalse()
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
    assertThat(result.isCapped).isFalse()
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
  fun capped_twoPlayers_smallWagerWins() {
    val entries = listOf(entry(1, outcomeA, 200), entry(2, outcomeB, 100))

    val result = PayoutCalculator.calculate(entries, outcomeB)

    assertThat(result.isCapped).isTrue()
    assertThat(result.payouts).containsExactly(1L, 100L, 2L, 200L)
    assertSumsToPot(entries, result)
  }

  @Test
  fun uncapped_twoPlayers_largeWagerWins() {
    val entries = listOf(entry(1, outcomeA, 200), entry(2, outcomeB, 100))

    val result = PayoutCalculator.calculate(entries, outcomeA)

    assertThat(result.isCapped).isFalse()
    assertThat(result.payouts).containsExactly(1L, 300L, 2L, 0L)
    assertSumsToPot(entries, result)
  }

  @Test
  fun capped_multipleWinners() {
    val entries = listOf(
      entry(1, outcomeA, 100),
      entry(2, outcomeA, 100),
      entry(3, outcomeB, 300),
    )

    val result = PayoutCalculator.calculate(entries, outcomeA)

    assertThat(result.isCapped).isTrue()
    assertThat(result.payouts).containsExactly(1L, 200L, 2L, 200L, 3L, 100L)
    assertSumsToPot(entries, result)
  }

  @Test
  fun capped_multipleLosers() {
    val entries = listOf(
      entry(1, outcomeA, 300),
      entry(2, outcomeB, 100),
      entry(3, outcomeB, 100),
    )

    val result = PayoutCalculator.calculate(entries, outcomeB)

    assertThat(result.isCapped).isTrue()
    assertThat(result.payouts).containsExactly(1L, 100L, 2L, 200L, 3L, 200L)
    assertSumsToPot(entries, result)
  }

  @Test
  fun capped_multiWinnerMultiLoser_remainderToLargestLoser() {
    // Winners 30, losers 107, refundPool 77: 33*77/107 = 23, 34*77/107 = 24, 40*77/107 = 28,
    // remainder 2 goes to the largest loser (id 5).
    val entries = listOf(
      entry(1, outcomeA, 10),
      entry(2, outcomeA, 20),
      entry(3, outcomeB, 33),
      entry(4, outcomeB, 34),
      entry(5, outcomeC, 40),
    )

    val result = PayoutCalculator.calculate(entries, outcomeA)

    assertThat(result.isCapped).isTrue()
    assertThat(result.payouts).containsExactly(1L, 20L, 2L, 40L, 3L, 23L, 4L, 24L, 5L, 30L)
    assertSumsToPot(entries, result)
  }

  @Test
  fun capped_remainderTieOnLargestLoser_goesToLowestParticipantId() {
    // Losers 60, refundPool 50: each 20*50/60 = 16, remainder 2 goes to the lowest-id loser.
    val entries = listOf(
      entry(1, outcomeA, 10),
      entry(4, outcomeB, 20),
      entry(2, outcomeB, 20),
      entry(3, outcomeB, 20),
    )

    val result = PayoutCalculator.calculate(entries, outcomeA)

    assertThat(result.isCapped).isTrue()
    assertThat(result.payouts).containsExactly(1L, 20L, 2L, 18L, 3L, 16L, 4L, 16L)
    assertSumsToPot(entries, result)
  }

  @Test
  fun capped_tinyLoserRefundCanRoundToZero() {
    // refundPool 91 over losers 101: id 2 gets 1*91/101 = 0, id 3 gets 100*91/101 = 90 + 1.
    val entries = listOf(
      entry(1, outcomeA, 10),
      entry(2, outcomeB, 1),
      entry(3, outcomeB, 100),
    )

    val result = PayoutCalculator.calculate(entries, outcomeA)

    assertThat(result.isCapped).isTrue()
    assertThat(result.payouts).containsExactly(1L, 20L, 2L, 0L, 3L, 91L)
    assertSumsToPot(entries, result)
  }

  @Test
  fun boundary_losingEqualsWinning_isUncapped() {
    val entries = listOf(entry(1, outcomeA, 100), entry(2, outcomeB, 100))

    val result = PayoutCalculator.calculate(entries, outcomeA)

    assertThat(result.isCapped).isFalse()
    assertThat(result.payouts).containsExactly(1L, 200L, 2L, 0L)
    assertSumsToPot(entries, result)
  }

  @Test
  fun noWinnerEverExceedsDoubleWager() {
    val entries = (1L..13L).map { entry(it, if (it % 3 == 0L) outcomeA else outcomeB, it * 7) }

    val result = PayoutCalculator.calculate(entries, outcomeA)

    assertThat(result.isCapped).isTrue()
    entries.filter { it.outcomeId == outcomeA }.forEach {
      assertThat(result.payouts[it.participantId]).isEqualTo(2 * it.wagerAmount)
    }
    assertSumsToPot(entries, result)
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
