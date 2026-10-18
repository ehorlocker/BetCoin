package com.betcoin.data.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mindrot.jbcrypt.BCrypt

/**
 * Hashes and checks player PINs with bcrypt (jBCrypt, cost [COST]), per spec §5.5.
 *
 * [hash] and [verify] run on [Dispatchers.Default] because bcrypt is deliberately slow. Test fakes
 * use the blocking forms of the same calls, so no layer ever keeps a plain-text PIN.
 */
object PinHasher {

  /** bcrypt work factor for player PINs. */
  const val COST = 10

  private val PIN_PATTERN = Regex("^[0-9]{4}$")

  /** Returns true if [pin] is exactly 4 ASCII digits. */
  fun isValidPin(pin: String): Boolean = PIN_PATTERN.matches(pin)

  /** Returns a bcrypt hash of [pin], computed on [Dispatchers.Default]. */
  suspend fun hash(pin: String): String = withContext(Dispatchers.Default) { hashBlocking(pin) }

  /** Returns true if [pin] matches [hash], checked on [Dispatchers.Default]. */
  suspend fun verify(pin: String, hash: String): Boolean =
    withContext(Dispatchers.Default) { verifyBlocking(pin, hash) }

  /**
   * Blocking form of [hash] for callers that control their own threading, such as test fakes
   * running on a test dispatcher.
   */
  fun hashBlocking(pin: String): String = BCrypt.hashpw(pin, BCrypt.gensalt(COST))

  /** Blocking form of [verify]. A malformed [hash] never matches. */
  fun verifyBlocking(pin: String, hash: String): Boolean =
    try {
      BCrypt.checkpw(pin, hash)
    } catch (e: IllegalArgumentException) {
      false
    } catch (e: IndexOutOfBoundsException) {
      false
    }
}
