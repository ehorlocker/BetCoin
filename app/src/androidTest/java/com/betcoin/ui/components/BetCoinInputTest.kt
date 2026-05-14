package com.betcoin.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.betcoin.ui.theme.BetCoinTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for BetCoinInput.
 */
class BetCoinInputTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun input_displaysPlaceholder() {
        composeTestRule.setContent {
            BetCoinTheme {
                BetCoinInput(
                    value = "",
                    onValueChange = { },
                    placeholder = "Enter amount",
                )
            }
        }

        composeTestRule.onNodeWithText("Enter amount")
            .assertIsDisplayed()
    }

    @Test
    fun input_displaysValue() {
        composeTestRule.setContent {
            BetCoinTheme {
                BetCoinInput(
                    value = "100",
                    onValueChange = { },
                    placeholder = "Enter amount",
                )
            }
        }

        composeTestRule.onNodeWithText("100")
            .assertIsDisplayed()
    }

    @Test
    fun input_triggersOnValueChange() {
        var currentValue = ""
        composeTestRule.setContent {
            BetCoinTheme {
                BetCoinInput(
                    value = currentValue,
                    onValueChange = { newValue: String -> currentValue = newValue },
                    placeholder = "Enter amount",
                )
            }
        }

        composeTestRule.onNodeWithText("Enter amount")
            .performClick()
            .performTextInput("50")

        assertThat(currentValue).isEqualTo("50")
    }
}
