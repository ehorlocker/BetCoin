package com.betcoin.ui.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.betcoin.ui.theme.BetCoinTheme
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for BetCoinButton variants.
 */
class BetCoinButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun primaryButton_displaysText() {
        composeTestRule.setContent {
            BetCoinTheme {
                BetCoinButton(
                    text = "Place Bet",
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Place Bet")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertIsEnabled()
    }

    @Test
    fun secondaryButton_displaysText() {
        composeTestRule.setContent {
            BetCoinTheme {
                BetCoinButton(
                    text = "Cancel",
                    variant = ButtonVariant.Secondary,
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun bettingActionButton_displaysText() {
        composeTestRule.setContent {
            BetCoinTheme {
                BetCoinButton(
                    text = "Confirm Bet",
                    variant = ButtonVariant.BettingAction,
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Confirm Bet")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun disabledButton_isNotEnabled() {
        composeTestRule.setContent {
            BetCoinTheme {
                BetCoinButton(
                    text = "Disabled",
                    enabled = false,
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Disabled")
            .assertIsNotEnabled()
    }
}
