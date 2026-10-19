package com.betcoin.ui.components

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.betcoin.ui.theme.BetCoinTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/** Compose UI tests for the shared admin PIN components. */
class AdminPinComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun showReveal(
        acknowledged: Boolean,
        onAcknowledgedChanged: (Boolean) -> Unit = {},
        onContinue: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            BetCoinTheme {
                RecoveryCodeReveal(
                    code = "ABCD-2345-EFGH",
                    acknowledged = acknowledged,
                    onAcknowledgedChanged = onAcknowledgedChanged,
                    onContinue = onContinue,
                )
            }
        }
    }

    @Test
    fun recoveryCodeReveal_continueDisabledUntilAcknowledged() {
        val changes = mutableListOf<Boolean>()
        var continues = 0
        showReveal(
            acknowledged = false,
            onAcknowledgedChanged = { changes += it },
            onContinue = { continues++ },
        )

        composeTestRule.onNodeWithTag(RECOVERY_CODE_REVEAL_TAG).assertTextEquals("ABCD-2345-EFGH")
        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()

        composeTestRule.onNodeWithText("I've written this down").performClick()
        assertThat(changes).containsExactly(true)
        assertThat(continues).isEqualTo(0)
    }

    @Test
    fun recoveryCodeReveal_continueEnabledWhenAcknowledged() {
        var continues = 0
        showReveal(acknowledged = true, onContinue = { continues++ })

        composeTestRule.onNodeWithText("Continue").assertIsEnabled().performClick()
        assertThat(continues).isEqualTo(1)
    }
}
