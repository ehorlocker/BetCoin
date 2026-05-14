package com.betcoin.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.betcoin.ui.theme.BetCoinTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for BetCoinChip.
 */
class BetCoinChipTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chip_displaysLabel() {
        composeTestRule.setContent {
            BetCoinTheme {
                BetCoinChip(
                    label = "Over 2.5",
                    selected = false,
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Over 2.5")
            .assertIsDisplayed()
    }

    @Test
    fun chip_selected_displaysAndIsSelected() {
        composeTestRule.setContent {
            BetCoinTheme {
                BetCoinChip(
                    label = "Over 2.5",
                    selected = true,
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Over 2.5")
            .assertIsDisplayed()
            .assertIsSelected()
    }

    @Test
    fun chip_unselected_isNotSelected() {
        composeTestRule.setContent {
            BetCoinTheme {
                BetCoinChip(
                    label = "Under 2.5",
                    selected = false,
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Under 2.5")
            .assertIsNotSelected()
    }

    @Test
    fun chip_triggersOnClick() {
        var clicked = false
        composeTestRule.setContent {
            BetCoinTheme {
                BetCoinChip(
                    label = "Under 2.5",
                    selected = false,
                    onClick = { clicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Under 2.5")
            .performClick()

        assertThat(clicked).isTrue()
    }
}
