package com.betcoin.data.repository

import java.security.SecureRandom

/**
 * Generates and normalises admin recovery codes (spec §5.6).
 *
 * A code is [LENGTH] characters from [ALPHABET] (uppercase letters and digits without the
 * ambiguous 0/O/1/I), shown to the user as `XXXX-XXXX-XXXX`. Repositories hash the
 * [normalize]d form, so what the user types back is matched case-insensitively with or
 * without dashes.
 */
object RecoveryCode {

    /** Characters a code is drawn from: A–Z and 2–9 without 0, O, 1 and I. */
    const val ALPHABET: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Number of code characters, not counting dashes. */
    const val LENGTH: Int = 12

    private const val GROUP_SIZE = 4

    private val secureRandom = SecureRandom()

    /** Returns a fresh code formatted as `XXXX-XXXX-XXXX`, drawn from [random]. */
    fun generate(random: SecureRandom = secureRandom): String {
        val raw = CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }
        return format(String(raw))
    }

    /**
     * Returns [input] uppercased with dashes and whitespace removed, or `null` if what's left
     * isn't exactly [LENGTH] characters from [ALPHABET].
     */
    fun normalize(input: String): String? {
        val cleaned = input.filterNot { it == '-' || it.isWhitespace() }.uppercase()
        return cleaned.takeIf { it.length == LENGTH && it.all { c -> c in ALPHABET } }
    }

    private fun format(raw: String): String = raw.chunked(GROUP_SIZE).joinToString("-")
}
