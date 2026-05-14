package com.betcoin.ui.players

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.betcoin.data.database.entity.User
import com.betcoin.data.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

    @Test
    fun managePlayersScreen_showsTitleAndPlayer() {
        val fakeUserRepo = object : UserRepository {
            override fun getAllUsers() = flowOf(listOf(
                User(id = 1, username = "Alice", pinHash = "hash", balance = 1500, totalWins = 5, totalLosses = 2, totalEarnings = 2000, totalLost = 500, totalDebt = 0, bailoutCount = 0, createdAt = 0),
            ))
            override fun getLeaderboard(): Flow<List<User>> = flowOf(emptyList())
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
            ManagePlayersScreen(viewModel = ManagePlayersViewModel(fakeUserRepo))
        }

        composeTestRule.onNodeWithText("Manage Players").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
        composeTestRule.onNodeWithText("Balance: 1500").assertIsDisplayed()
    }

    @Test
    fun managePlayersScreen_emptyState_showsMessage() {
        val fakeUserRepo = object : UserRepository {
            override fun getAllUsers() = flowOf(emptyList<User>())
            override fun getLeaderboard(): Flow<List<User>> = flowOf(emptyList())
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
            ManagePlayersScreen(viewModel = ManagePlayersViewModel(fakeUserRepo))
        }

        composeTestRule.onNodeWithText("No players yet. Add one to get started!").assertIsDisplayed()
    }

    @Test
    fun managePlayersScreen_clickAddPlayer_showsDialog() {
        val fakeUserRepo = object : UserRepository {
            override fun getAllUsers() = flowOf(emptyList<User>())
            override fun getLeaderboard(): Flow<List<User>> = flowOf(emptyList())
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
            ManagePlayersScreen(viewModel = ManagePlayersViewModel(fakeUserRepo))
        }

        composeTestRule.onNodeWithText("Add Player").performClick()
        composeTestRule.onNodeWithText("Create New Player").assertIsDisplayed()
    }
}
