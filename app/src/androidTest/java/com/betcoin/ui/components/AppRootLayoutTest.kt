package com.betcoin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.betcoin.ui.theme.BetCoinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [AppRootLayout].
 */
@RunWith(AndroidJUnit4::class)
class AppRootLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun withLabel_drawsBannerAboveContent() {
        composeTestRule.setContent {
            BetCoinTheme {
                AppRootLayout(bannerLabel = "DEV BUILD · betcoin_dev.db") {
                    Box(Modifier.fillMaxSize().testTag("content")) { Text("Screen") }
                }
            }
        }
        composeTestRule.onNodeWithTag(DEV_BUILD_BANNER_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag("content").assertIsDisplayed()
    }

    @Test
    fun withoutLabel_drawsNothingAndReservesNoSpace() {
        composeTestRule.setContent {
            BetCoinTheme {
                AppRootLayout(bannerLabel = null) {
                    Box(Modifier.fillMaxSize().testTag("content")) { Text("Screen") }
                }
            }
        }
        composeTestRule.onNodeWithTag(DEV_BUILD_BANNER_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag("content").assertTopPositionInRootIsEqualTo(0.dp)
    }
}
