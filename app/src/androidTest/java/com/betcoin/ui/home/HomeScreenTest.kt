package com.betcoin.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.betcoin.data.database.entity.User
import com.betcoin.data.repository.BetRepository
import com.betcoin.data.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [HomeScreen].
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen_showsTitleAndCounts() {
        val fakeBetRepo = object : BetRepository {
            override fun getActiveBets() = flowOf(emptyList<com.betcoin.data.model.BetWithDetails>())
            override fun getAllBets() = flowOf(emptyList<com.betcoin.data.model.BetWithDetails>())
            override fun getBetWithDetails(betId: Long) = flowOf(com.betcoin.data.model.BetWithDetails(
                bet = com.betcoin.data.database.entity.Bet(id = 1, prompt = "", status = com.betcoin.data.model.BetStatus.ACTIVE, createdAt = 0),
                outcomes = emptyList(),
                participants = emptyList()
            ))
            override suspend fun createBet(prompt: String, outcomes: List<String>) = 0L
            override suspend fun addParticipant(betId: Long, userId: Long, outcomeId: Long, wagerAmount: Long) {}
            override suspend fun removeParticipant(participantId: Long) {}
            override suspend fun lockBet(betId: Long) {}
            override suspend fun resolveBet(betId: Long, winningOutcomeId: Long) {}
            override suspend fun cancelBet(betId: Long) {}
            override suspend fun forceCancelBet(betId: Long) {}
            override suspend fun reopenBet(betId: Long) {}
            override suspend fun adminResolveBet(betId: Long, winningOutcomeId: Long) {}
            override suspend fun updateBetPrompt(betId: Long, newPrompt: String) {}
            override suspend fun adminRemoveParticipant(betId: Long, participantId: Long) {}
            override suspend fun adminEditWager(participantId: Long, newWagerAmount: Long) {}
            override suspend fun deleteBet(betId: Long) {}
            override suspend fun repeatBet(betId: Long) = 0L
        }
        val fakeUserRepo = object : UserRepository {
            override fun getAllUsers() = flowOf(listOf(
                User(id = 1, username = "Alice", pinHash = "hash", createdAt = 0),
                User(id = 2, username = "Bob", pinHash = "hash", createdAt = 0),
            ))
            override fun getLeaderboard() = flowOf(emptyList<User>())
            override suspend fun createUser(username: String, pin: String) = 0L
            override suspend fun verifyPin(userId: Long, pin: String) = false
            override suspend fun getUser(userId: Long) = null
            override suspend fun bailout(userId: Long) {}
            override suspend fun deleteUser(userId: Long) {}
            override suspend fun resetPin(userId: Long, newPin: String) {}
            override suspend fun updateBalance(userId: Long, delta: Long) {}
            override suspend fun setBalance(userId: Long, newBalance: Long) {}
            override suspend fun updateUsername(userId: Long, newUsername: String) {}
        }

        composeTestRule.setContent {
            HomeScreen(viewModel = HomeViewModel(fakeBetRepo, fakeUserRepo))
        }

        composeTestRule.onNodeWithText("BetCoin").assertIsDisplayed()
        composeTestRule.onNodeWithText("Active bets: 0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Players: 2").assertIsDisplayed()
    }
}
