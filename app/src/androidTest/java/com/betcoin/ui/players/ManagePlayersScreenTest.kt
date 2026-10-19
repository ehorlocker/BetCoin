package com.betcoin.ui.players

import com.betcoin.data.model.ThemePreference
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.betcoin.ui.theme.BetCoinTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [ManagePlayersScreen].
 */
@RunWith(AndroidJUnit4::class)
class ManagePlayersScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val repository = PlayersTestRepository()

    private fun showScreen(
        onPlayerClick: (Long) -> Unit = {},
        onAdminPanel: () -> Unit = {},
    ) {
        val viewModel = ManagePlayersViewModel(repository)
        composeTestRule.setContent {
            BetCoinTheme(themePreference = ThemePreference.DARK) {
                ManagePlayersScreen(
                    viewModel = viewModel,
                    onPlayerClick = onPlayerClick,
                    onAdminPanel = onAdminPanel,
                )
            }
        }
    }

    private fun typeInto(label: String, text: String) {
        composeTestRule.onNode(hasSetTextAction() and hasText(label)).performTextInput(text)
    }

    @Test
    fun showsTitleAndPlayersWithAvatarAndUsername() {
        repository.addPlayer("Alice")
        repository.addPlayer("Bob")
        showScreen()

        composeTestRule.onNodeWithText("Manage Players").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
        composeTestRule.onNodeWithText("A").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bob").assertIsDisplayed()
        composeTestRule.onNodeWithText("B").assertIsDisplayed()
    }

    @Test
    fun hasNoDeleteRenameOrBailoutActions() {
        repository.addPlayer("Alice", balance = 0)
        showScreen()

        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Delete", ignoreCase = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Rename", ignoreCase = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Bailout", ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun emptyState_showsMessage() {
        showScreen()

        composeTestRule.onNodeWithText("No players yet. Add one to get started!").assertIsDisplayed()
    }

    @Test
    fun tapPlayer_opensPlayerDetailForThatPlayer() {
        repository.addPlayer("Alice")
        val bobId = repository.addPlayer("Bob")
        var clicked: Long? = null
        showScreen(onPlayerClick = { clicked = it })

        composeTestRule.onNodeWithText("Bob").performClick()

        assertThat(clicked).isEqualTo(bobId)
    }

    @Test
    fun gearIcon_callsAdminPanelHook() {
        var adminClicks = 0
        showScreen(onAdminPanel = { adminClicks++ })

        composeTestRule.onNodeWithContentDescription("Admin Panel").performClick()

        assertThat(adminClicks).isEqualTo(1)
    }

    @Test
    fun addPlayer_asksForPinTwice() {
        showScreen()

        composeTestRule.onNodeWithText("Add Player").performClick()

        composeTestRule.onNodeWithText("Create New Player").assertIsDisplayed()
        composeTestRule.onNodeWithText("Username").assertIsDisplayed()
        composeTestRule.onNodeWithText("PIN (4 digits)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirm PIN").assertIsDisplayed()
    }

    @Test
    fun addPlayer_mismatchedPins_showsErrorAndCreatesNothing() {
        showScreen()
        composeTestRule.onNodeWithText("Add Player").performClick()

        typeInto("Username", "Carol")
        typeInto("PIN (4 digits)", "1234")
        typeInto("Confirm PIN", "4321")
        composeTestRule.onNodeWithText("Create").performClick()

        composeTestRule.onNodeWithText("PINs don't match").assertIsDisplayed()
        assertThat(repository.users.value).isEmpty()
    }

    @Test
    fun addPlayer_matchingPins_addsPlayerToList() {
        showScreen()
        composeTestRule.onNodeWithText("Add Player").performClick()

        typeInto("Username", "Carol")
        typeInto("PIN (4 digits)", "1234")
        typeInto("Confirm PIN", "1234")
        composeTestRule.onNodeWithText("Create").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { repository.users.value.isNotEmpty() }
        composeTestRule.onNodeWithText("Create New Player").assertDoesNotExist()
        composeTestRule.onNodeWithText("Carol").assertIsDisplayed()
    }
}
