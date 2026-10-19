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
 * @property isCapped true when the even-money cap applied: not a push, and the losing wagers
 *   exceeded the winning wagers, so the unmatched part was refunded to the losers.
 */
data class PayoutResult(
  val payouts: Map<Long, Long>,
  val isPush: Boolean,
  val isCapped: Boolean,
)

/** Pure payout math for resolving a bet (spec §5.3). */
object PayoutCalculator {

  /**
   * Resolves the bet for [winningOutcomeId]. If nobody picked it, the bet is a push and every
   * participant gets their wager back (checked before the cap).
   *
   * Otherwise winners can win at most their own wager (even money):
   * - Uncapped (`losingWagers <= winningWagers`): each winner gets
   *   `wager * totalPot / winningWagers`, losers get 0, and the rounding remainder goes to the
   *   winner with the largest wager.
   * - Capped (`losingWagers > winningWagers`): each winner gets exactly `2 * wager`, and the
   *   unmatched `refundPool = losingWagers - winningWagers` is refunded to the losers as
   *   `wager * refundPool / losingWagers`, with the rounding remainder going to the loser with
   *   the largest wager.
   *
   * Math is [Long], multiplying before dividing. Ties for the remainder go to the lowest
   * participant id. Payouts always sum to the total pot.
   *
   * @throws IllegalArgumentException if a wager is not positive or a participant id repeats.
   * @throws ArithmeticException if a wager multiplied by the pot (or refund pool) overflows
   *   [Long], far beyond any realistic balance.
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
        isCapped = false,
      )
    }

    val losers = participants.filter { it.outcomeId != winningOutcomeId }
    val totalPot = participants.sumOf { it.wagerAmount }
    val winningWagers = winners.sumOf { it.wagerAmount }
    val losingWagers = totalPot - winningWagers
    val isCapped = losingWagers > winningWagers

    val payouts = participants.associateTo(LinkedHashMap()) { it.participantId to 0L }
    val remainderRecipient: PayoutEntry
    if (isCapped) {
      for (winner in winners) {
        payouts[winner.participantId] = 2 * winner.wagerAmount
      }
      val refundPool = losingWagers - winningWagers
      for (loser in losers) {
        payouts[loser.participantId] =
          Math.multiplyExact(loser.wagerAmount, refundPool) / losingWagers
      }
      remainderRecipient = losers.minWith(largestWagerThenLowestId)
    } else {
      for (winner in winners) {
        payouts[winner.participantId] =
          Math.multiplyExact(winner.wagerAmount, totalPot) / winningWagers
      }
      remainderRecipient = winners.minWith(largestWagerThenLowestId)
    }

    val remainder = totalPot - payouts.values.sum()
    payouts[remainderRecipient.participantId] =
      payouts.getValue(remainderRecipient.participantId) + remainder

    return PayoutResult(payouts = payouts, isPush = false, isCapped = isCapped)
  }

  private val largestWagerThenLowestId =
    compareByDescending<PayoutEntry> { it.wagerAmount }.thenBy { it.participantId }
}
