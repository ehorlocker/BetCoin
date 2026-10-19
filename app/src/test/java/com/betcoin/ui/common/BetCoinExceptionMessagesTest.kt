package com.betcoin.ui.common

import com.betcoin.data.model.ActiveBetConflictException
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.DuplicateUsernameException
import com.betcoin.data.model.InsufficientBalanceException
import com.betcoin.data.model.InvalidBetStateException
import com.betcoin.data.model.InvalidImageException
import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.model.NotEligibleForBailoutException
import com.betcoin.data.model.UnanimousPickException
import com.betcoin.data.model.WrongRecoveryCodeException
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tests for [toUserMessage]. */
class BetCoinExceptionMessagesTest {

    @Test
    fun insufficientBalance() {
        assertThat(InsufficientBalanceException(1, 50, 10).toUserMessage())
            .isEqualTo("Not enough BetCoin: needs 50, has 10")
    }

    @Test
    fun duplicateUsername() {
        assertThat(DuplicateUsernameException("Jay").toUserMessage()).isEqualTo("Username already exists")
    }

    @Test
    fun unanimousPick() {
        assertThat(UnanimousPickException().toUserMessage())
            .isEqualTo("Everyone picked the same outcome. Change at least one pick.")
    }

    @Test
    fun activeBetConflict() {
        assertThat(ActiveBetConflictException("Jay is in a bet").toUserMessage())
            .isEqualTo("Not allowed while an active bet is involved")
    }

    @Test
    fun invalidBetState() {
        assertThat(InvalidBetStateException(7, BetStatus.RESOLVED).toUserMessage())
            .isEqualTo("This bet has changed and can't be updated any more")
    }

    @Test
    fun notEligibleForBailout() {
        assertThat(NotEligibleForBailoutException(3).toUserMessage()).isEqualTo("Not eligible for a bailout")
    }

    @Test
    fun invalidInput_doesNotLeakRepositoryMessage() {
        val message = InvalidInputException("User 3 not found").toUserMessage()

        assertThat(message).isEqualTo("That's no longer valid. Check it and try again.")
        assertThat(message).doesNotContain("3")
    }

    @Test
    fun wrongRecoveryCode() {
        assertThat(WrongRecoveryCodeException().toUserMessage()).isEqualTo("Wrong recovery code")
    }

    @Test
    fun invalidImage() {
        assertThat(InvalidImageException().toUserMessage()).isEqualTo("Couldn't use that image")
    }

    @Test
    fun genericMessage() {
        assertThat(GENERIC_ERROR_MESSAGE).isEqualTo("Something went wrong. Try again.")
    }
}
