package com.betcoin.ui.common

import com.betcoin.data.model.ActiveBetConflictException
import com.betcoin.data.model.BetCoinException
import com.betcoin.data.model.DuplicateUsernameException
import com.betcoin.data.model.InsufficientBalanceException
import com.betcoin.data.model.InvalidBetStateException
import com.betcoin.data.model.InvalidImageException
import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.model.NotEligibleForBailoutException
import com.betcoin.data.model.UnanimousPickException
import com.betcoin.data.model.WrongRecoveryCodeException

/** Shown when a repository call fails with an `IOException` or `SQLiteException`. */
const val GENERIC_ERROR_MESSAGE = "Something went wrong. Try again."

/**
 * The user-facing message for this business-rule violation.
 *
 * Never uses [Throwable.message]: repository messages contain IDs. The `when` is exhaustive with
 * no `else`, so adding a [BetCoinException] subclass fails compilation until it gets a message.
 */
fun BetCoinException.toUserMessage(): String = when (this) {
    is InsufficientBalanceException -> "Not enough BetCoin: needs $required, has $available"
    is DuplicateUsernameException -> "Username already exists"
    is UnanimousPickException -> "Everyone picked the same outcome. Change at least one pick."
    is ActiveBetConflictException -> "Not allowed while an active bet is involved"
    is InvalidBetStateException -> "This bet has changed and can't be updated any more"
    is NotEligibleForBailoutException -> "Not eligible for a bailout"
    is InvalidInputException -> "That's no longer valid. Check it and try again."
    is WrongRecoveryCodeException -> "Wrong recovery code"
    is InvalidImageException -> "Couldn't use that image"
}
