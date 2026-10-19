package com.betcoin.ui.settings

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.betcoin.data.model.ThemePreference
import com.betcoin.ui.theme.BetCoinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose UI tests for the stateless [SettingsScreen]. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun show(
        state: SettingsUiState = SettingsUiState(),
        onThemeSelected: (ThemePreference) -> Unit = {},
        onBack: () -> Unit = {},
        devToolsRow: @androidx.compose.runtime.Composable () -> Unit = {},
    ) {
        composeTestRule.setContent {
            BetCoinTheme(themePreference = ThemePreference.DARK) {
                SettingsScreen(
                    uiState = state,
                    versionLabel = "1.0 (dev)",
                    onThemeSelected = onThemeSelected,
                    onErrorShown = {},
                    onBack = onBack,
                    devToolsRow = devToolsRow,
                )
            }
        }
    }

    @Test
    fun showsThreeThemeOptions_withCurrentSelected() {
        show(SettingsUiState(themePreference = ThemePreference.DARK))

        composeTestRule.onNodeWithText("Dark").assertIsSelected()
        composeTestRule.onNodeWithText("Light").assertIsNotSelected()
        composeTestRule.onNodeWithText("System default").assertIsNotSelected()
    }

    @Test
    fun clickingLight_callsOnThemeSelected() {
        var selected: ThemePreference? = null
        show(onThemeSelected = { selected = it })

        composeTestRule.onNodeWithText("Light").performClick()

        assert(selected == ThemePreference.LIGHT) { "Expected LIGHT but was $selected" }
    }

    @Test
    fun showsVersionLabel() {
        show()

        composeTestRule.onNodeWithText("1.0 (dev)").assertIsDisplayed()
    }

    @Test
    fun rendersDevToolsRowSlot() {
        show(devToolsRow = { Text("Dev Tools") })

        composeTestRule.onNodeWithText("Dev Tools").assertIsDisplayed()
    }

    @Test
    fun back_callsOnBack() {
        var backed = false
        show(onBack = { backed = true })

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assert(backed) { "onBack was not invoked" }
    }
}
