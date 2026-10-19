package com.betcoin.ui.betdetail

import com.betcoin.data.model.BetCoinException
import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.repository.BetRepository
import com.betcoin.ui.home.FakeBetRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [BetRepository] for Bet Detail action tests: serves [bet], records resolve/cancel/reopen calls,
 * and can throw [failure] or suspend on [gate] when one of them is called.
 */
class RecordingBetRepository(
    initial: BetWithDetails?,
) : BetRepository by FakeBetRepository() {
    val bet = MutableStateFlow(initial)

    /** Calls in order, e.g. `resolve(7, 1)`, `cancel(7)`, `reopen(7)`. */
    val calls = mutableListOf<String>()

    /** Thrown by the next resolve/cancel/reopen call. */
    var failure: BetCoinException? = null

    /** When set, resolve/cancel/reopen suspend until it completes. */
    var gate: CompletableDeferred<Unit>? = null

    override fun getBetWithDetails(betId: Long): Flow<BetWithDetails?> = bet

    override suspend fun resolveBet(betId: Long, winningOutcomeId: Long) = record("resolve($betId, $winningOutcomeId)")

    override suspend fun cancelBet(betId: Long) = record("cancel($betId)")

    override suspend fun reopenBet(betId: Long) = record("reopen($betId)")

    private suspend fun record(call: String) {
        calls += call
        gate?.await()
        failure?.let { throw it }
    }
}
