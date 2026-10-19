package com.betcoin.ui.admin

import com.betcoin.data.model.ThemePreference
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.betcoin.ui.components.PIN_INPUT_TAG
import com.betcoin.ui.theme.BetCoinTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/** Compose UI tests for [AdminPinGate]. */
class AdminPinGateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var verified = 0
    private var forgot = 0
    private var dismissed = 0

    private fun showGate() {
        composeTestRule.setContent {
            BetCoinTheme(themePreference = ThemePreference.DARK) {
                AdminPinGate(
                    verifier = { it == "1234" },
                    onVerified = { verified++ },
                    onForgotPin = { forgot++ },
                    onDismiss = { dismissed++ },
                )
            }
        }
    }

    @Test
    fun gate_showsAdminPrompt() {
        showGate()

        composeTestRule.onNodeWithText("Admin, enter your PIN").assertExists()
    }

    @Test
    fun correctPin_callsOnVerified() {
        showGate()

        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).performTextInput("1234")
        composeTestRule.waitForIdle()

        assertThat(verified).isEqualTo(1)
    }

    @Test
    fun forgotPin_callsOnForgotPin() {
        showGate()

        composeTestRule.onNodeWithText("Forgot admin PIN?").performClick()

        assertThat(forgot).isEqualTo(1)
    }

    @Test
    fun cancel_callsOnDismissOnly() {
        showGate()

        composeTestRule.onNodeWithText("Cancel").performClick()

        assertThat(dismissed).isEqualTo(1)
        assertThat(verified).isEqualTo(0)
        assertThat(forgot).isEqualTo(0)
    }
}
