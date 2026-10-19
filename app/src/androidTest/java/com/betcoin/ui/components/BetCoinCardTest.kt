package com.betcoin.ui.components

import com.betcoin.data.model.ThemePreference
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.betcoin.ui.theme.BetCoinTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for BetCoinCard.
 */
class BetCoinCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun card_displaysContent() {
        composeTestRule.setContent {
            BetCoinTheme(themePreference = ThemePreference.DARK) {
                BetCoinCard {
                    androidx.compose.material3.Text("Bet Title")
                }
            }
        }

        composeTestRule.onNodeWithText("Bet Title")
            .assertIsDisplayed()
    }

    @Test
    fun card_isClickable_whenOnClickProvided() {
        var clicked = false
        composeTestRule.setContent {
            BetCoinTheme(themePreference = ThemePreference.DARK) {
                BetCoinCard(onClick = { clicked = true }) {
                    androidx.compose.material3.Text("Clickable Card")
                }
            }
        }

        composeTestRule.onNodeWithText("Clickable Card")
            .assertHasClickAction()
            .performClick()

        assertThat(clicked).isTrue()
    }
}
