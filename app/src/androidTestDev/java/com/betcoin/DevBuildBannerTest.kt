package com.betcoin

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.betcoin.ui.components.DEV_BUILD_BANNER_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dev flavor only: launches the real app and checks the root dev build banner is shown on every
 * top-level destination (spec §14 Dev Build Indicator).
 */
@RunWith(AndroidJUnit4::class)
class DevBuildBannerTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun banner_isShownOnEveryTopLevelDestination() {
        // Start destination is Onboarding on a fresh install, Home otherwise.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(hasText("Welcome to BetCoin") or hasText("Manage Players"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        if (composeTestRule.onAllNodes(hasText("Welcome to BetCoin")).fetchSemanticsNodes().isNotEmpty()) {
            assertBannerShown()
            composeTestRule.onNodeWithText("4-digit PIN").performTextInput("1234")
            composeTestRule.onNodeWithText("Confirm").performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodes(hasText("New Bet")).fetchSemanticsNodes().isNotEmpty()
            }
        }
        assertBannerShown()

        for (destination in listOf("New Bet", "Leaderboard", "Bet History", "Manage Players")) {
            val button = composeTestRule.onNodeWithText(destination)
            // New Bet stays disabled with a hint until 2 players exist (spec §4.2 Home).
            if (SemanticsProperties.Disabled in button.fetchSemanticsNode().config) {
                composeTestRule.onNodeWithText("Add at least 2 players", substring = true).assertIsDisplayed()
                continue
            }
            button.performClick()
            composeTestRule.waitForIdle()
            assertBannerShown()
            Espresso.pressBack()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodes(hasText("New Bet")).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun assertBannerShown() {
        composeTestRule.onNodeWithTag(DEV_BUILD_BANNER_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("DEV BUILD · betcoin_dev.db").assertIsDisplayed()
    }
}
