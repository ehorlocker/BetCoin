package com.betcoin.navigation

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.betcoin.ui.components.PIN_INPUT_TAG
import com.betcoin.ui.newbet.NEW_BET_OUTCOME_TAG_PREFIX
import com.betcoin.ui.newbet.NEW_BET_PLAYER_TAG
import com.betcoin.ui.newbet.NEW_BET_PROMPT_TAG
import com.betcoin.ui.newbet.NEW_BET_WAGER_TAG
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every forward edge of the §4.1 graph below Home, plus the back step from each screen, driven
 * through the real MainActivity and NavGraph.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavGraphForwardEdgesTest : NavGraphTestBase() {

    @Before
    fun seed() {
        seedAdminPin()
    }

    private fun launchHome() {
        launchApp()
        awaitHome()
    }

    private fun scrollClick(text: String) {
        waitForText(text)
        compose.onNodeWithText(text).performScrollTo().performClick()
    }

    @Test
    fun home_leaderboard_playerDetail_andBackEachStep() {
        seedPlayers("Alice" to "1111")
        launchHome()

        clickTag("home_leaderboard")
        waitForText("Net Worth")
        clickTag("leaderboard_row_Alice")
        waitForText("Player Detail")
        waitForText("Alice")

        pressBack()
        waitForText("Net Worth")
        pressBack()
        awaitHome()
    }

    @Test
    fun home_betHistory_betDetail_andBackEachStep() {
        val betId = runBlocking {
            val (alice, bob) = seedPlayers("Alice" to "1111", "Bob" to "2222").let { it[0] to it[1] }
            val id = betRepository.createBet("Who wins Smash?", listOf("Jay", "Austin"))
            val outcomes = betRepository.getBetWithDetails(id).let { flow ->
                flow.first()!!.outcomes
            }
            betRepository.addParticipant(id, alice, outcomes[0].id, 100)
            betRepository.addParticipant(id, bob, outcomes[1].id, 50)
            betRepository.lockBet(id)
            id
        }
        launchHome()

        clickTag("home_bet_history")
        clickTag("bet_history_row_$betId")
        waitForText("Bet Detail")
        waitForText("Who wins Smash?")

        pressBack()
        waitForText("Bet History")
        clickText("Back")
        awaitHome()
    }

    @Test
    fun home_newBet_lockBet_opensBetDetail_replacingNewBet() {
        seedPlayers("Alice" to "1111", "Bob" to "2222")
        launchHome()

        clickTag("home_new_bet")
        waitForTag(NEW_BET_PROMPT_TAG)
        compose.onNodeWithTag(NEW_BET_PROMPT_TAG).performTextInput("Who wins Smash?")
        compose.onNodeWithTag("${NEW_BET_OUTCOME_TAG_PREFIX}0").performTextInput("Jay")
        compose.onNodeWithTag("${NEW_BET_OUTCOME_TAG_PREFIX}1").performTextInput("Austin")
        scrollClick("Next")
        waitForText("Step B", substring = true)

        addParticipant("Alice (1000)", "Jay", "100", "1111", "Jay · 100 BetCoin")
        addParticipant("Bob (1000)", "Austin", "50", "2222", "Austin · 50 BetCoin")
        scrollClick("Continue")
        waitForText("Step C", substring = true)
        scrollClick("Lock Bet")
        compose.onNodeWithTag(PIN_INPUT_TAG).performTextInput("1111")
        waitForText("Bob, enter your PIN")
        compose.onNodeWithTag(PIN_INPUT_TAG).performTextInput("2222")

        waitForText("Bet Detail")
        waitForText("Who wins Smash?")
        // New Bet was popped on lock: back lands on Home, not on the finished New Bet flow.
        pressBack()
        awaitHome()
    }

    private fun addParticipant(player: String, pick: String, wager: String, pin: String, joined: String) {
        compose.onNodeWithTag(NEW_BET_PLAYER_TAG).performScrollTo().performClick()
        clickText(player)
        compose.onNodeWithText(pick).performScrollTo().performClick()
        compose.onNodeWithTag(NEW_BET_WAGER_TAG).performScrollTo().performTextInput(wager)
        scrollClick("Add Participant")
        compose.onNodeWithTag(PIN_INPUT_TAG).performTextInput(pin)
        waitForText(joined)
    }

    @Test
    fun home_newBet_closeAndSystemBack_returnHome() {
        seedPlayers("Alice" to "1111", "Bob" to "2222")
        launchHome()

        clickTag("home_new_bet")
        waitForText("Step A", substring = true)
        clickText("Close")
        awaitHome()

        clickTag("home_new_bet")
        waitForText("Step A", substring = true)
        pressBack()
        awaitHome()
    }

    @Test
    fun home_settings_andBack() {
        launchHome()

        compose.onNodeWithContentDescription("Settings").performClick()
        waitForText("Theme")
        pressBack()
        awaitHome()

        compose.onNodeWithContentDescription("Settings").performClick()
        waitForText("Theme")
        compose.onNodeWithContentDescription("Back").performClick()
        awaitHome()
    }

    @Test
    fun home_managePlayers_playerDetail_andBackEachStep() {
        seedPlayers("Alice" to "1111")
        launchHome()

        clickTag("home_manage_players")
        clickTag("player_row_Alice")
        waitForText("Player Detail")

        pressBack()
        waitForText("Manage Players")
        pressBack()
        awaitHome()
    }

    @Test
    fun managePlayers_addPlayer_staysOnManagePlayers() {
        launchHome()

        clickTag("home_manage_players")
        clickTag("manage_players_add")
        compose.onNodeWithTag("add_player_username").performTextInput("Carol")
        compose.onNodeWithTag("add_player_pin").performTextInput("3333")
        compose.onNodeWithTag("add_player_pin_confirm").performTextInput("3333")
        compose.onNodeWithTag("add_player_create").performClick()

        waitForTag("player_row_Carol")
        waitForText("Manage Players")
        clickText("Back")
        awaitHome()
    }
}
