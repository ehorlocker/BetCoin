package com.betcoin.ui.leaderboard

import com.betcoin.data.database.entity.User
import com.betcoin.testing.FakeAvatarStore
import com.betcoin.testing.FakeUserRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests for [LeaderboardViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LeaderboardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeUserRepository: FakeUserRepository
    private lateinit var fakeAvatarStore: FakeAvatarStore
    private lateinit var viewModel: LeaderboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeUserRepository = FakeUserRepository()
        fakeAvatarStore = FakeAvatarStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Creates the ViewModel and keeps its WhileSubscribed state collected for the test. */
    private fun TestScope.createViewModel() {
        viewModel = LeaderboardViewModel(fakeUserRepository, fakeAvatarStore)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
    }

    private val state get() = viewModel.uiState.value

    private fun user(
        id: Long,
        name: String,
        balance: Long = 1000L,
        totalDebt: Long = 0L,
        wins: Int = 0,
        losses: Int = 0,
    ) = User(
        id = id,
        username = name,
        pinHash = FakeUserRepository.PLACEHOLDER_HASH,
        balance = balance,
        totalDebt = totalDebt,
        totalWins = wins,
        totalLosses = losses,
        createdAt = 0L,
    )

    @Test
    fun initialState_isLoading() {
        viewModel = LeaderboardViewModel(fakeUserRepository, fakeAvatarStore)

        assertThat(state.isLoading).isTrue()
        assertThat(state.rows).isEmpty()
    }

    @Test
    fun rows_carryAvatarModel() = runTest {
        fakeUserRepository.users = listOf(
            user(1L, "Alice").copy(avatarRef = "a.jpg"),
            user(2L, "Bob"),
        )
        fakeAvatarStore.images["a.jpg"] = "MODEL"
        createViewModel()

        val byId = state.rows.associateBy { it.userId }
        assertThat(byId.getValue(1L).avatarModel).isEqualTo("MODEL")
        assertThat(byId.getValue(2L).avatarModel).isNull()
    }

    @Test
    fun noPlayers_showsEmptyNotLoading() = runTest {
        createViewModel()

        assertThat(state.isLoading).isFalse()
        assertThat(state.rows).isEmpty()
    }

    @Test
    fun rows_haveSequentialRanksInRepositoryOrder() = runTest {
        fakeUserRepository.users = listOf(
            user(2L, "bob", balance = 1500L),
            user(1L, "amy"),
            user(3L, "cal"),
        )
        createViewModel()

        assertThat(state.rows.map { it.username }).containsExactly("bob", "amy", "cal").inOrder()
        assertThat(state.rows.map { it.rank }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun rows_useNetWorthNotBalance() = runTest {
        fakeUserRepository.users = listOf(
            user(1L, "rich", balance = 3000L, totalDebt = 2000L),
            user(2L, "steady", balance = 1500L),
        )
        createViewModel()

        assertThat(state.rows.map { it.username }).containsExactly("steady", "rich").inOrder()
        assertThat(state.rows.single { it.username == "rich" }.netWorth).isEqualTo(1000L)
    }

    @Test
    fun negativeNetWorth_isKeptAndRankedLast() = runTest {
        fakeUserRepository.users = listOf(
            user(1L, "broke", balance = 200L, totalDebt = 1000L),
            user(2L, "zero", balance = 0L),
        )
        createViewModel()

        assertThat(state.rows.map { it.username }).containsExactly("zero", "broke").inOrder()
        assertThat(state.rows.last().netWorth).isEqualTo(-800L)
    }

    @Test
    fun rows_carryIdAndWinLoss() = runTest {
        fakeUserRepository.users = listOf(user(7L, "jay", wins = 5, losses = 3))
        createViewModel()

        assertThat(state.rows).containsExactly(
            LeaderboardViewModel.Row(
                userId = 7L,
                rank = 1,
                username = "jay",
                netWorth = 1000L,
                wins = 5,
                losses = 3,
            ),
        )
    }

    @Test
    fun repositoryChange_reRanksRows() = runTest {
        fakeUserRepository.users = listOf(user(1L, "a"), user(2L, "b", balance = 500L))
        createViewModel()
        assertThat(state.rows.map { it.username }).containsExactly("a", "b").inOrder()

        fakeUserRepository.users = listOf(user(1L, "a"), user(2L, "b", balance = 2000L))
        advanceUntilIdle()

        assertThat(state.rows.map { it.username }).containsExactly("b", "a").inOrder()
    }
}
