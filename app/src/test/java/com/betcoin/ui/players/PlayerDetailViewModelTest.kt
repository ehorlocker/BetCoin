package com.betcoin.ui.players

import androidx.lifecycle.SavedStateHandle
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.NotEligibleForBailoutException
import com.betcoin.navigation.Routes
import com.betcoin.testing.FakeUserRepository
import com.betcoin.ui.players.PlayerDetailViewModel.UiState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests for [PlayerDetailViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeUserRepository
    private lateinit var viewModel: PlayerDetailViewModel
    private var playerId = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeUserRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Creates a real player through the fake, so their PIN is hashed and verifiable. */
    private suspend fun createPlayer(balance: Long, pin: String = "1234"): Long {
        val id = repository.createUser("Jay", pin)
        repository.setBalance(id, balance)
        return id
    }

    private fun TestScope.createViewModel(id: Long = playerId) {
        viewModel = PlayerDetailViewModel(SavedStateHandle(mapOf(Routes.PLAYER_ID_ARG to id)), repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
    }

    private val loaded get() = viewModel.uiState.value as UiState.Loaded

    @Test
    fun initialState_isLoading() {
        viewModel = PlayerDetailViewModel(SavedStateHandle(mapOf(Routes.PLAYER_ID_ARG to 1L)), repository)

        assertThat(viewModel.uiState.value).isEqualTo(UiState.Loading)
    }

    @Test
    fun loaded_showsAllStats() = runTest {
        repository.users = listOf(
            User(
                id = 7L,
                username = "Alice",
                pinHash = FakeUserRepository.PLACEHOLDER_HASH,
                balance = 1500,
                totalWins = 5,
                totalLosses = 2,
                totalEarnings = 2000,
                totalLost = 500,
                bailoutCount = 1,
                totalDebt = 1000,
                createdAt = 0L,
            ),
        )
        repository.lockedBalances = mapOf(7L to 250L)
        createViewModel(7L)

        val stats = loaded.stats
        assertThat(stats.username).isEqualTo("Alice")
        assertThat(stats.balance).isEqualTo(1500)
        assertThat(stats.netWorth).isEqualTo(500)
        assertThat(stats.totalWins).isEqualTo(5)
        assertThat(stats.totalLosses).isEqualTo(2)
        assertThat(stats.totalEarnings).isEqualTo(2000)
        assertThat(stats.totalLost).isEqualTo(500)
        assertThat(stats.bailoutCount).isEqualTo(1)
        assertThat(stats.totalDebt).isEqualTo(1000)
        assertThat(stats.lockedBalance).isEqualTo(250)
        assertThat(stats.isEligibleForBailout).isFalse()
    }

    @Test
    fun netWorth_canBeNegative() = runTest {
        playerId = createPlayer(balance = 50)
        repository.forceBailout(playerId)
        repository.setBalance(playerId, 200)
        createViewModel()

        assertThat(loaded.stats.netWorth).isEqualTo(-800)
    }

    @Test
    fun unknownPlayer_isNotFound() = runTest {
        createViewModel(99L)

        assertThat(viewModel.uiState.value).isEqualTo(UiState.NotFound)
    }

    @Test
    fun deletedWhileOpen_becomesNotFound() = runTest {
        playerId = createPlayer(balance = 1000)
        createViewModel()

        repository.deleteUser(playerId)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(UiState.NotFound)
    }

    @Test
    fun eligibility_balancePlusLockedBelow100() = runTest {
        playerId = createPlayer(balance = 60)
        repository.lockedBalances = mapOf(playerId to 39L)
        createViewModel()

        assertThat(loaded.stats.isEligibleForBailout).isTrue()
    }

    @Test
    fun eligibility_lockedWagersCountTowardThreshold() = runTest {
        playerId = createPlayer(balance = 60)
        repository.lockedBalances = mapOf(playerId to 40L)
        createViewModel()

        assertThat(loaded.stats.isEligibleForBailout).isFalse()
    }

    @Test
    fun eligibility_followsLockedBalanceUpdates() = runTest {
        playerId = createPlayer(balance = 10)
        repository.lockedBalances = mapOf(playerId to 500L)
        createViewModel()
        assertThat(loaded.stats.isEligibleForBailout).isFalse()

        repository.lockedBalances = emptyMap()
        advanceUntilIdle()

        assertThat(loaded.stats.isEligibleForBailout).isTrue()
    }

    @Test
    fun bailoutClicked_whenEligible_asksForPin() = runTest {
        playerId = createPlayer(balance = 0)
        createViewModel()

        viewModel.onBailoutClicked()
        advanceUntilIdle()

        assertThat(loaded.showBailoutPin).isTrue()
    }

    @Test
    fun bailoutClicked_whenNotEligible_doesNothing() = runTest {
        playerId = createPlayer(balance = 100)
        createViewModel()

        viewModel.onBailoutClicked()
        advanceUntilIdle()

        assertThat(loaded.showBailoutPin).isFalse()
    }

    @Test
    fun verifyPin_checksThatPlayersPin() = runTest {
        playerId = createPlayer(balance = 0, pin = "4321")
        createViewModel()

        assertThat(viewModel.verifyPin("1234")).isFalse()
        assertThat(viewModel.verifyPin("4321")).isTrue()
    }

    @Test
    fun pinDismissed_closesDialogWithoutBailout() = runTest {
        playerId = createPlayer(balance = 0)
        createViewModel()

        viewModel.onBailoutClicked()
        viewModel.onBailoutPinDismissed()
        advanceUntilIdle()

        assertThat(loaded.showBailoutPin).isFalse()
        assertThat(loaded.stats.balance).isEqualTo(0)
        assertThat(loaded.stats.bailoutCount).isEqualTo(0)
    }

    @Test
    fun pinVerified_grantsBailoutAndHidesButton() = runTest {
        playerId = createPlayer(balance = 40)
        createViewModel()

        viewModel.onBailoutClicked()
        viewModel.onBailoutPinVerified()
        advanceUntilIdle()

        val state = loaded
        assertThat(state.showBailoutPin).isFalse()
        assertThat(state.isBailingOut).isFalse()
        assertThat(state.message).isEqualTo("Bailout granted: +1,000 BetCoin")
        assertThat(state.stats.balance).isEqualTo(1040)
        assertThat(state.stats.bailoutCount).isEqualTo(1)
        assertThat(state.stats.totalDebt).isEqualTo(1000)
        assertThat(state.stats.netWorth).isEqualTo(40)
        assertThat(state.stats.isEligibleForBailout).isFalse()
    }

    @Test
    fun pinVerified_repositoryRefuses_showsNotEligible() = runTest {
        playerId = createPlayer(balance = 0)
        repository.shouldThrowOnBailout = NotEligibleForBailoutException(playerId)
        createViewModel()

        viewModel.onBailoutClicked()
        viewModel.onBailoutPinVerified()
        advanceUntilIdle()

        assertThat(loaded.message).isEqualTo("Not eligible for a bailout")
        assertThat(loaded.isBailingOut).isFalse()
        assertThat(loaded.stats.bailoutCount).isEqualTo(0)
    }

    @Test
    fun messageShown_clearsMessage() = runTest {
        playerId = createPlayer(balance = 0)
        createViewModel()
        viewModel.onBailoutPinVerified()
        advanceUntilIdle()

        viewModel.onMessageShown()
        advanceUntilIdle()

        assertThat(loaded.message).isNull()
    }

    @Test
    fun statRows_listEveryStatInSpecOrder() = runTest {
        playerId = createPlayer(balance = 0)
        createViewModel()

        assertThat(statRows(loaded.stats).map { it.first }).containsExactly(
            "Balance",
            "Net worth",
            "W-L",
            "Total earnings",
            "Total lost",
            "Bailouts",
            "Total debt",
            "Locked in active bets",
        ).inOrder()
    }
}
