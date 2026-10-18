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
 * Compose UI tests for [NewBetScreen] Steps A to C, backed by the real repositories on an
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
    private var lockedBetId: Long? = null

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
            NewBetScreen(
                viewModel = viewModel,
                onNavigateBack = { navigatedBack = true },
                onLocked = { lockedBetId = it },
            )
        }
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(15_000) {
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

    /** A pending bet with Alice on Jay (100) and Bob on [bobPick] (50), opened at Step B. */
    private fun showStepBWithTwoParticipants(bobPick: Int = 1): Triple<Long, Long, Long> {
        val (betId, alice, bob) = runBlocking {
            val alice = userRepository.createUser("Alice", "1111")
            val bob = userRepository.createUser("Bob", "2222")
            val betId = betRepository.createBet("Who wins Smash?", listOf("Jay", "Austin"))
            val outcomes = betRepository.getBetWithDetails(betId).first()!!.outcomes
            betRepository.addParticipant(betId, alice, outcomes[0].id, 100)
            betRepository.addParticipant(betId, bob, outcomes[bobPick].id, 50)
            Triple(betId, alice, bob)
        }
        showScreen(betId)
        waitForText("Step B · Add Participants")
        return Triple(betId, alice, bob)
    }

    @Test
    fun stepC_summaryShowsPromptOutcomesAndPicks() {
        showStepBWithTwoParticipants()

        button("Continue").performClick()

        waitForText("Step C · Review & Lock")
        composeTestRule.onNodeWithText("Who wins Smash?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Jay · 100 BetCoin").assertIsDisplayed()
        composeTestRule.onNodeWithText("Austin · 50 BetCoin").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total pot: 150 BetCoin").assertIsDisplayed()
        button("Lock Bet").assertIsEnabled()
    }

    @Test
    fun stepC_unanimousPick_showsErrorAndBlocksLocking() {
        showStepBWithTwoParticipants(bobPick = 0)

        button("Continue").performClick()

        waitForText("Everyone picked the same outcome")
        button("Lock Bet").assertIsNotEnabled()
    }

    @Test
    fun stepC_back_returnsToStepBWithParticipantsKept() {
        val (betId, _, _) = showStepBWithTwoParticipants()
        button("Continue").performClick()
        waitForText("Step C · Review & Lock")

        button("Back").performClick()

        waitForText("Step B · Add Participants")
        composeTestRule.onNodeWithText("Jay · 100 BetCoin").assertIsDisplayed()
        composeTestRule.onNodeWithText("Austin · 50 BetCoin").assertIsDisplayed()
        button("Continue").assertIsEnabled()
        assertThat(navigatedBack).isFalse()
        val participants = runBlocking { betRepository.getBetWithDetails(betId).first()!!.participants }
        assertThat(participants).hasSize(2)
    }

    @Test
    fun stepC_lock_asksEachPinInOrderThenLocksTheBet() {
        val (betId, alice, bob) = showStepBWithTwoParticipants()
        button("Continue").performClick()
        waitForText("Step C · Review & Lock")

        button("Lock Bet").performClick()
        composeTestRule.onNodeWithText("1 of 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice, enter your PIN").assertIsDisplayed()
        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).performTextInput("2222")
        waitForText("Wrong PIN")
        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).performTextInput("1111")
        waitForText("Bob, enter your PIN")
        composeTestRule.onNodeWithText("2 of 2").assertIsDisplayed()
        assertThat(lockedBetId).isNull()
        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).performTextInput("2222")

        composeTestRule.waitUntil(5_000) { lockedBetId == betId }
        runBlocking {
            assertThat(userRepository.getUser(alice)!!.balance).isEqualTo(900L)
            assertThat(userRepository.getUser(bob)!!.balance).isEqualTo(950L)
            assertThat(betRepository.getBetWithDetails(betId).first()!!.bet.status)
                .isEqualTo(com.betcoin.data.model.BetStatus.ACTIVE)
        }
    }

    @Test
    fun stepC_cancellingAPin_locksNothing() {
        val (betId, alice, _) = showStepBWithTwoParticipants()
        button("Continue").performClick()
        waitForText("Step C · Review & Lock")

        button("Lock Bet").performClick()
        composeTestRule.onNodeWithTag(PIN_INPUT_TAG).performTextInput("1111")
        waitForText("Bob, enter your PIN")
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodes(hasText("Bob, enter your PIN")).fetchSemanticsNodes().isEmpty()
        }
        assertThat(lockedBetId).isNull()
        composeTestRule.onNodeWithText("Step C · Review & Lock").assertIsDisplayed()
        runBlocking {
            assertThat(userRepository.getUser(alice)!!.balance).isEqualTo(1000L)
            assertThat(betRepository.getBetWithDetails(betId).first()!!.bet.status)
                .isEqualTo(com.betcoin.data.model.BetStatus.PENDING_CONFIRMATION)
        }
    }

    @Test
    fun stepC_balanceChangedSinceStepB_blocksLockingWithAMessage() {
        val (_, alice, _) = showStepBWithTwoParticipants()
        button("Continue").performClick()
        waitForText("Step C · Review & Lock")

        runBlocking { userRepository.setBalance(alice, 30L) }

        waitForText("Alice's balance changed")
        button("Lock Bet").assertIsNotEnabled()
    }
}
