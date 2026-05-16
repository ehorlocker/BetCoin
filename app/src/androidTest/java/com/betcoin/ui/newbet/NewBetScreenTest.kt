package com.betcoin.ui.newbet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [NewBetScreen].
 */
@RunWith(AndroidJUnit4::class)
class NewBetScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun newBetScreen_showsTitle() {
        composeTestRule.setContent {
            NewBetScreen()
        }

        composeTestRule.onNodeWithText("New Bet - Coming Soon").assertIsDisplayed()
    }

    @Test
    fun newBetScreen_backButton_invokesCallback() {
        var backClicked = false

        composeTestRule.setContent {
            NewBetScreen(onNavigateBack = { backClicked = true })
        }

        composeTestRule.onNodeWithText("Back").assertIsDisplayed().performClick()
        assert(backClicked) { "Back callback was not invoked" }
    }
}
