package com.betcoin.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.betcoin.ui.theme.BetCoinTheme
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for BetCoinAvatar.
 */
class BetCoinAvatarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun avatar_displaysInitials() {
        composeTestRule.setContent {
            BetCoinTheme {
                BetCoinAvatar(initials = "JD")
            }
        }

        composeTestRule.onNodeWithText("JD")
            .assertIsDisplayed()
    }

    @Test
    fun avatar_displaysSingleInitial() {
        composeTestRule.setContent {
            BetCoinTheme {
                BetCoinAvatar(initials = "A")
            }
        }

        composeTestRule.onNodeWithText("A")
            .assertIsDisplayed()
    }
}
