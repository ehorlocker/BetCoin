package com.betcoin.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.betcoin.ui.common.GENERIC_ERROR_MESSAGE
import com.betcoin.ui.theme.BetCoinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose UI tests for [LaunchErrorScreen]. */
@RunWith(AndroidJUnit4::class)
class LaunchErrorScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun show(onRetry: () -> Unit = {}) {
        composeTestRule.setContent {
            BetCoinTheme { LaunchErrorScreen(onRetry = onRetry) }
        }
    }

    @Test
    fun showsTitleAndGenericMessage() {
        show()
        composeTestRule.onNodeWithText(LAUNCH_ERROR_TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText(GENERIC_ERROR_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun retry_invokesCallbackOnce() {
        var calls = 0
        show(onRetry = { calls++ })
        composeTestRule.onNodeWithText("Retry").performClick()
        assertEquals(1, calls)
    }
}
