package com.betcoin.ui.newbet

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.betcoin.data.database.BetCoinDatabase
import com.betcoin.data.repository.local.LocalBetRepository
import com.betcoin.data.repository.local.LocalUserRepository
import com.betcoin.navigation.Routes
import com.betcoin.ui.components.PIN_INPUT_TAG
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [NewBetScreen] Steps A and B, backed by the real repositories on an
 * in-memory Room database.
 */
@RunWith(AndroidJUnit4::class)
class NewBetScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: BetCoinDatabase
    private lateinit var betRepository: LocalBetRepository
    private lateinit var userRepository: LocalUserRepository
    private var navigatedBack = false

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, BetCoinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        betRepository = LocalBetRepository(database)
        userRepository = LocalUserRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun showScreen(betId: Long? = null) {
        val handle = SavedStateHandle(betId?.let { mapOf(Routes.NEW_BET_BET_ID_ARG to it) } ?: emptyMap())
        val viewModel = NewBetViewModel(handle, betRepository, userRepository)
        composeTestRule.setContent {
            NewBetScreen(viewModel = viewModel, onNavigateBack = { navigatedBack = true })
        }
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun button(text: String): SemanticsNodeInteraction =
        composeTestRule.onNodeWithText(text).performScrollTo()

    private fun fillStepA() {
        composeTestRule.onNodeWithTag(NEW_BET_PROMPT_TAG).performTextInput("Who wins Smash?")
        composeTestRule.onNodeWithTag("${NEW_BET_OUTCOME_TAG_PREFIX}0").performTextInput("Jay")
        composeTestRule.onNodeWithTag("${NEW_BET_OUTCOME_TAG_PREFIX}1").performTextInput("Austin")
    }

    private fun pendingBetCount(): Int = runBlocking {
        if (betRepository.getPendingBet().first() == null) 0 else 1
    }

    @Test
    fun stepA_nextEnabledOnlyWithValidPromptAndOutcomes() {
        showScreen()

        composeTestRule.onNodeWithText("Step A · Create Bet").assertIsDisplayed()
        button("Next").assertIsNotEnabled()

        fillStepA()
        button("Next").assertIsEnabled()

        button("Add Outcome").performClick()
        composeTestRule.onNodeWithTag("${NEW_BET_OUTCOME_TAG_PREFIX}2").performScrollTo().performTextInput("JAY")
        composeTestRule.onNodeWithText("Outcomes must be different").assertIsDisplayed()
        button("Next").assertIsNotEnabled()
    }

    @Test
    fun stepA_addOutcomeDisabledAtEight() {
        showScreen()

        repeat(6) { button("Add Outcome").performClick() }

        composeTestRule.onNodeWithText("Outcomes (8 of 8)").assertIsDisplayed()
        button("Add Outcome").assertIsNotEnabled()
    }

    @Test
    fun stepA_backWhenEmpty_leavesWithoutAsking() {
        showScreen()

        composeTestRule.onNodeWithText("Close").performClick()

        composeTestRule.waitForIdle()
        assertThat(navigatedBack).isTrue()
        composeTestRule.onNodeWithText("Discard this bet?").assertDoesNotExist()
    }

    @Test
    fun stepA_closeAfterText_asksBeforeLeaving() {
        showScreen()
        composeTestRule.onNodeWithTag(NEW_BET_PROMPT_TAG).performTextInput("Draft")

        composeTestRule.onNodeWithText("Close").performClick()
        composeTestRule.onNodeWithText("Discard this bet?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Keep editing").performClick()
        assertThat(navigatedBack).isFalse()

        composeTestRule.onNodeWithText("Close").performClick()
        composeTestRule.onNodeWithText("Discard").performClick()
        composeTestRule.waitUntil(5_000) { navigatedBack }
    }

    @Test
    fun stepB_addAndRemoveParticipantsWithPin() {
        runBlocking {
            userRepository.createUser("Alice", "1111")
            userRepository.createUser("Bob", "2222")
        }
        showScreen()
        fillStepA()
        button("Next").performClick()
        waitForText("Step B · Add Participants")
        assertThat(pendingBetCount()).isEqualTo(1)
        composeTestRule.onNodeWithText("Who wins Smash?").assertIsDisplayed()
        button("Continue").assertIsNotEnabled()

        // Alice joins on Jay for 100 after a wrong PIN and then the right one.
        composeTestRule.onNodeWithTag(NEW_BET_PLAYER_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Alice (1000)").performClick()
        composeTestRule.onNodeWithText("Jay").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(NEW_BET_WAGER_TAG).performScrollTo().performTextInput("100")
        button("Add Participant").performClick()
        composeTestRule.onNodeWithText("Alice, enter your PIN").assertIsDisplayed()
        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).performTextInput("9999")
        waitForText("Wrong PIN")
        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).performTextInput("1111")
        waitForText("Jay · 100 BetCoin")

        // Alice is no longer offered; Bob joins on Austin.
        composeTestRule.onNodeWithTag(NEW_BET_PLAYER_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Alice (1000)").assertDoesNotExist()
        composeTestRule.onNodeWithText("Bob (1000)").performClick()
        composeTestRule.onNodeWithText("Austin").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(NEW_BET_WAGER_TAG).performScrollTo().performTextInput("50")
        button("Add Participant").performClick()
        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).performTextInput("2222")
        waitForText("Austin · 50 BetCoin")
        button("Continue").assertIsEnabled()
        composeTestRule.onNodeWithText("Every player has joined.").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Remove Bob").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodes(hasText("Austin · 50 BetCoin")).fetchSemanticsNodes().isEmpty()
        }
        button("Continue").assertIsNotEnabled()
    }

    @Test
    fun stepB_wagerAboveBalanceShowsError() {
        runBlocking { userRepository.createUser("Alice", "1111") }
        val betId = runBlocking { betRepository.createBet("Prompt", listOf("Yes", "No")) }
        showScreen(betId)
        waitForText("Step B · Add Participants")

        composeTestRule.onNodeWithTag(NEW_BET_PLAYER_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Alice (1000)").performClick()
        composeTestRule.onNodeWithText("Yes").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(NEW_BET_WAGER_TAG).performScrollTo().performTextInput("1001")

        composeTestRule.onNodeWithText("Alice only has 1000 BetCoin").assertIsDisplayed()
        button("Add Participant").assertIsNotEnabled()
    }

    @Test
    fun stepB_openedWithBetId_closeDiscardsThePendingBet() {
        val betId = runBlocking { betRepository.createBet("Recovered bet", listOf("Yes", "No")) }
        showScreen(betId)
        waitForText("Recovered bet")

        composeTestRule.onNodeWithText("Close").performClick()
        composeTestRule.onNodeWithText("Discard this bet?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard").performClick()

        composeTestRule.waitUntil(5_000) { navigatedBack }
        assertThat(pendingBetCount()).isEqualTo(0)
    }
}
