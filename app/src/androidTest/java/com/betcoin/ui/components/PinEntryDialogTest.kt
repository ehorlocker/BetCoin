package com.betcoin.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.betcoin.ui.theme.BetCoinTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.mindrot.jbcrypt.BCrypt

/**
 * Compose UI tests for [PinEntryDialog] and [ConsentSequence].
 *
 * Verifiers check against bcrypt hashes, like the real repositories, so no plain-text PIN is kept
 * as test state.
 */
class PinEntryDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun bcryptVerifier(hash: String): suspend (String) -> Boolean =
        { pin -> BCrypt.checkpw(pin, hash) }

    private fun showDialog(
        onVerified: () -> Unit = {},
        onDismiss: () -> Unit = {},
        verifier: suspend (String) -> Boolean = bcryptVerifier(JAY_HASH),
    ) {
        composeTestRule.setContent {
            BetCoinTheme {
                PinEntryDialog(
                    label = "Jay",
                    verifier = verifier,
                    onVerified = onVerified,
                    onDismiss = onDismiss,
                    title = "Resolve: Jay wins",
                )
            }
        }
    }

    @Test
    fun dialog_showsWhosePinAndActionSummary() {
        showDialog()

        composeTestRule.onNodeWithText("Jay, enter your PIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("Resolve: Jay wins").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun correctPin_autoSubmitsOnFourthDigit() {
        var verified = 0
        showDialog(onVerified = { verified++ })

        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).performTextInput("123")
        composeTestRule.waitForIdle()
        assertThat(verified).isEqualTo(0)

        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).performTextInput("4")
        composeTestRule.waitUntil(timeoutMillis = 5_000) { verified == 1 }
        composeTestRule.onNodeWithText("Wrong PIN").assertDoesNotExist()
    }

    @Test
    fun threeDigits_doNotSubmit() {
        var attempts = 0
        showDialog(verifier = { attempts++; false })

        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).performTextInput("123")
        composeTestRule.waitForIdle()

        assertThat(attempts).isEqualTo(0)
        composeTestRule.onNodeWithText("Wrong PIN").assertDoesNotExist()
    }

    @Test
    fun nonDigits_areIgnored() {
        var submitted: String? = null
        showDialog(verifier = { submitted = it; false })

        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).performTextInput("1a2b-3 4")
        composeTestRule.waitUntil(timeoutMillis = 5_000) { submitted != null }

        assertThat(submitted).isEqualTo("1234")
    }

    @Test
    fun wrongPin_showsErrorClearsInputAndAllowsRetry() {
        var verified = 0
        showDialog(onVerified = { verified++ })

        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).performTextInput("9999")
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTextCount("Wrong PIN") == 1
        }
        assertThat(verified).isEqualTo(0)
        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).assertTextEquals("")

        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).performTextInput("1234")
        composeTestRule.waitUntil(timeoutMillis = 5_000) { verified == 1 }
    }

    @Test
    fun pinField_hasPasswordSemantics() {
        showDialog()

        // Password semantics stop TalkBack from reading the digits aloud (§5.5).
        composeTestRule.onNodeWithTag(PIN_INPUT_TAG)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
    }

    @Test
    fun cancel_callsOnDismissWithoutVerifying() {
        var dismissed = 0
        var verified = 0
        showDialog(onVerified = { verified++ }, onDismiss = { dismissed++ })

        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).performTextInput("12")
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        assertThat(dismissed).isEqualTo(1)
        assertThat(verified).isEqualTo(0)
    }

    // --- Consent sequence ---

    private fun showSequence(
        onAllConfirmed: () -> Unit = {},
        onAbort: () -> Unit = {},
    ) {
        val steps = listOf(
            ConsentStep("Jay", bcryptVerifier(JAY_HASH)),
            ConsentStep("Sam", bcryptVerifier(SAM_HASH)),
            ConsentStep("Alex", bcryptVerifier(ALEX_HASH)),
        )
        composeTestRule.setContent {
            BetCoinTheme {
                var showing by remember { mutableStateOf(true) }
                if (showing) {
                    ConsentSequence(
                        steps = steps,
                        onAllConfirmed = onAllConfirmed,
                        onAbort = { showing = false; onAbort() },
                        title = "Cancel bet",
                    )
                }
            }
        }
    }

    private fun enterPin(pin: String, nextPrompt: String?) {
        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).performTextInput(pin)
        if (nextPrompt != null) {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithTextCount(nextPrompt) == 1
            }
        }
    }

    @Test
    fun sequence_correctPins_stepThroughInOrderAndConfirmOnce() {
        var confirmed = 0
        var aborted = 0
        showSequence(onAllConfirmed = { confirmed++ }, onAbort = { aborted++ })

        composeTestRule.onNodeWithText("1 of 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Jay, enter your PIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel bet").assertIsDisplayed()

        enterPin("1234", nextPrompt = "Sam, enter your PIN")
        composeTestRule.onNodeWithText("2 of 3").assertIsDisplayed()
        // The next participant starts with an empty field.
        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).assertTextEquals("")

        enterPin("5678", nextPrompt = "Alex, enter your PIN")
        composeTestRule.onNodeWithText("3 of 3").assertIsDisplayed()

        enterPin("0000", nextPrompt = null)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { confirmed == 1 }
        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).assertDoesNotExist()
        composeTestRule.waitForIdle()

        assertThat(confirmed).isEqualTo(1)
        assertThat(aborted).isEqualTo(0)
    }

    @Test
    fun sequence_wrongPin_staysOnSameParticipant() {
        var confirmed = 0
        showSequence(onAllConfirmed = { confirmed++ })

        enterPin("1234", nextPrompt = "Sam, enter your PIN")
        enterPin("1234", nextPrompt = "Wrong PIN")

        composeTestRule.onNodeWithText("Sam, enter your PIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 of 3").assertIsDisplayed()
        assertThat(confirmed).isEqualTo(0)

        enterPin("5678", nextPrompt = "Alex, enter your PIN")
    }

    @Test
    fun sequence_cancelMidSequence_abortsWholeAction() {
        var confirmed = 0
        var aborted = 0
        showSequence(onAllConfirmed = { confirmed++ }, onAbort = { aborted++ })

        enterPin("1234", nextPrompt = "Sam, enter your PIN")
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        assertThat(aborted).isEqualTo(1)
        assertThat(confirmed).isEqualTo(0)
        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).assertDoesNotExist()
    }

    @Test
    fun sequence_stepsRebuiltOnRecomposition_keepProgressAndConfirmOnce() {
        var confirmed = 0
        var recompose by mutableIntStateOf(0)
        composeTestRule.setContent {
            BetCoinTheme {
                // Built inline like a real caller, so every recomposition gets new lambdas and a
                // list that isn't equal to the previous one.
                val steps = recompose.let {
                    listOf(
                        ConsentStep("Jay", bcryptVerifier(JAY_HASH)),
                        ConsentStep("Sam", bcryptVerifier(SAM_HASH)),
                    )
                }
                ConsentSequence(
                    steps = steps,
                    onAllConfirmed = { confirmed++ },
                    onAbort = {},
                )
            }
        }

        enterPin("1234", nextPrompt = "Sam, enter your PIN")
        recompose++
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("2 of 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sam, enter your PIN").assertIsDisplayed()

        enterPin("5678", nextPrompt = null)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { confirmed == 1 }
        // The caller recomposes before it stops rendering the sequence.
        recompose++
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).assertDoesNotExist()
        assertThat(confirmed).isEqualTo(1)
    }

    private fun onAllNodesWithTextCount(text: String): Int =
        composeTestRule.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    private companion object {
        // Low cost keeps the tests fast; the real repositories use cost 10.
        val JAY_HASH: String = BCrypt.hashpw("1234", BCrypt.gensalt(4))
        val SAM_HASH: String = BCrypt.hashpw("5678", BCrypt.gensalt(4))
        val ALEX_HASH: String = BCrypt.hashpw("0000", BCrypt.gensalt(4))
    }
}
