package com.betcoin.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.betcoin.data.database.BetCoinDatabase
import com.betcoin.data.database.entity.User
import com.betcoin.data.repository.local.LocalBetRepository
import com.betcoin.data.repository.local.LocalUserRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for Home's New Bet gate (§4.2 Home, §10): disabled with a hint below 2 players.
 */
@RunWith(AndroidJUnit4::class)
class HomeNewBetGateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: BetCoinDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, BetCoinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun showHomeWithPlayers(count: Int) {
        runBlocking {
            repeat(count) { index ->
                database.userDao().insert(User(username = "Player$index", pinHash = "hash", createdAt = 0L))
            }
        }
        val homeViewModel = HomeViewModel(LocalBetRepository(database), LocalUserRepository(database))
        composeTestRule.setContent {
            HomeScreen(viewModel = homeViewModel)
        }
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Players: $count").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun newBet_withOnePlayer_isDisabledWithHint() {
        showHomeWithPlayers(1)

        composeTestRule.onNodeWithText("Players: 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("New Bet").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Add at least 2 players", substring = true).assertIsDisplayed()
    }

    @Test
    fun newBet_withTwoPlayers_isEnabledWithoutHint() {
        showHomeWithPlayers(2)

        composeTestRule.onNodeWithText("Players: 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("New Bet").assertIsEnabled()
        composeTestRule.onNodeWithText("Add at least 2 players", substring = true).assertDoesNotExist()
    }
}
