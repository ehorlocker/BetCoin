package com.betcoin.data.model

/**
 * Base type for business-rule violations thrown by the repository layer.
 *
 * Repositories check every rule before writing, so nothing is persisted when one of these is
 * thrown. ViewModels map each subclass to a user-facing message.
 */
sealed class BetCoinException(message: String) : Exception(message)

/** The user with [userId] has [available] BetCoin but the operation needs [required]. */
class InsufficientBalanceException(
  val userId: Long,
  val required: Long,
  val available: Long,
) : BetCoinException("User $userId needs $required BetCoin but has $available")

/** Another user already has [username], compared case-insensitively. */
class DuplicateUsernameException(
  val username: String,
) : BetCoinException("Username \"$username\" is already taken")

/** Locking the bet would leave every participant on the same outcome. */
class UnanimousPickException :
  BetCoinException("All participants picked the same outcome")

/** The operation conflicts with an ACTIVE bet (e.g. deleting a user who is in one). */
class ActiveBetConflictException(message: String) : BetCoinException(message)

/** The bet with [betId] is in [status], which doesn't allow the requested operation. */
class InvalidBetStateException(
  val betId: Long,
  val status: BetStatus,
) : BetCoinException("Bet $betId is $status, which doesn't allow this operation")

/** The user with [userId] doesn't meet the bailout conditions. */
class NotEligibleForBailoutException(
  val userId: Long,
) : BetCoinException("User $userId is not eligible for a bailout")

/** An input failed validation (blank text, out-of-range amount, too few outcomes, and so on). */
class InvalidInputException(message: String) : BetCoinException(message)

/** The entered admin recovery code doesn't match the stored hash. */
class WrongRecoveryCodeException : BetCoinException("Wrong recovery code")
