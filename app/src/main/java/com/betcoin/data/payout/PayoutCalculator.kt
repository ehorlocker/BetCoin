package com.betcoin.data.payout

/** One participant's stake in a bet, as seen by [PayoutCalculator]. */
data class PayoutEntry(
  val participantId: Long,
  val outcomeId: Long,
  val wagerAmount: Long,
)

/**
 * Result of [PayoutCalculator.calculate].
 *
 * @property payouts gross payout per participant id; the values always sum to the total pot.
 * @property isPush true when nobody picked the winning outcome and every wager is refunded.
 */
data class PayoutResult(
  val payouts: Map<Long, Long>,
  val isPush: Boolean,
)

/** Pure payout math for resolving a bet (spec §5.3). */
object PayoutCalculator {

  /**
   * Splits the total pot among the participants who picked [winningOutcomeId], in proportion
   * to their wagers. Each share is `wager * totalPot / winningWagers` in [Long] math, multiplying
   * before dividing. The rounding remainder goes to the winner with the largest wager (lowest
   * participant id on a tie). Losers get 0. If nobody picked the winning outcome, the bet is a
   * push and every participant gets their wager back.
   *
   * @throws IllegalArgumentException if a wager is not positive or a participant id repeats.
   * @throws ArithmeticException if `wager * totalPot` overflows [Long], far beyond any
   *   realistic balance.
   */
  fun calculate(participants: List<PayoutEntry>, winningOutcomeId: Long): PayoutResult {
    require(participants.all { it.wagerAmount > 0 }) { "Wagers must be positive" }
    require(participants.map { it.participantId }.toSet().size == participants.size) {
      "Participant ids must be unique"
    }

    val winners = participants.filter { it.outcomeId == winningOutcomeId }
    if (winners.isEmpty()) {
      return PayoutResult(
        payouts = participants.associate { it.participantId to it.wagerAmount },
        isPush = true,
      )
    }

    val totalPot = participants.sumOf { it.wagerAmount }
    val winningWagers = winners.sumOf { it.wagerAmount }

    val payouts = participants.associateTo(LinkedHashMap()) { it.participantId to 0L }
    for (winner in winners) {
      payouts[winner.participantId] =
        Math.multiplyExact(winner.wagerAmount, totalPot) / winningWagers
    }

    val remainder = totalPot - payouts.values.sum()
    val remainderWinner = winners.minWith(
      compareByDescending<PayoutEntry> { it.wagerAmount }.thenBy { it.participantId },
    )
    payouts[remainderWinner.participantId] =
      payouts.getValue(remainderWinner.participantId) + remainder

    return PayoutResult(payouts = payouts, isPush = false)
  }
}
