package com.betcoin.ui.players

import com.betcoin.data.database.entity.User
import com.betcoin.data.repository.UserRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests for [ManagePlayersViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManagePlayersViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeUserRepository: FakeUserRepository
    private lateinit var viewModel: ManagePlayersViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeUserRepository = FakeUserRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isLoading() = runTest {
        viewModel = ManagePlayersViewModel(fakeUserRepository)

        assertThat(viewModel.uiState.value.isLoading).isTrue()
    }

    @Test
    fun players_emitsCorrectList() = runTest {
        val user = User(
            id = 1L,
            username = "Alice",
            pinHash = "hash",
            balance = 1500,
            totalWins = 5,
            totalLosses = 2,
            totalEarnings = 2000,
            totalLost = 500,
            totalDebt = 0,
            bailoutCount = 0,
            createdAt = System.currentTimeMillis(),
        )
        fakeUserRepository.users = listOf(user)
        viewModel = ManagePlayersViewModel(fakeUserRepository)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.players).hasSize(1)
        assertThat(viewModel.uiState.value.players[0].username).isEqualTo("Alice")
    }

    @Test
    fun players_emptyList_showsEmptyState() = runTest {
        fakeUserRepository.users = emptyList()
        viewModel = ManagePlayersViewModel(fakeUserRepository)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.players).isEmpty()
    }

    @Test
    fun addPlayer_emptyUsername_showsError() = runTest {
        viewModel = ManagePlayersViewModel(fakeUserRepository)
        advanceUntilIdle()

        viewModel.onAddPlayerClicked()
        viewModel.onUsernameChanged("")
        viewModel.onPinChanged("1234")
        viewModel.onConfirmAddPlayer()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.addPlayerError).isEqualTo("Username cannot be empty")
    }

    @Test
    fun addPlayer_shortPin_showsError() = runTest {
        viewModel = ManagePlayersViewModel(fakeUserRepository)
        advanceUntilIdle()

        viewModel.onAddPlayerClicked()
        viewModel.onUsernameChanged("Alice")
        viewModel.onPinChanged("123")
        viewModel.onConfirmAddPlayer()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.addPlayerError).isEqualTo("PIN must be exactly 4 digits")
    }

    @Test
    fun addPlayer_nonDigitPin_showsError() = runTest {
        viewModel = ManagePlayersViewModel(fakeUserRepository)
        advanceUntilIdle()

        viewModel.onAddPlayerClicked()
        viewModel.onUsernameChanged("Alice")
        viewModel.onPinChanged("abcd")
        viewModel.onConfirmAddPlayer()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.addPlayerError).isEqualTo("PIN must be exactly 4 digits")
    }

    @Test
    fun addPlayer_duplicateUsername_showsError() = runTest {
        fakeUserRepository.users = listOf(
            User(
                id = 1L,
                username = "Alice",
                pinHash = "hash",
                balance = 1000,
                totalWins = 0,
                totalLosses = 0,
                totalEarnings = 0,
                totalLost = 0,
                totalDebt = 0,
                bailoutCount = 0,
                createdAt = System.currentTimeMillis(),
            )
        )
        viewModel = ManagePlayersViewModel(fakeUserRepository)
        advanceUntilIdle()

        viewModel.onAddPlayerClicked()
        viewModel.onUsernameChanged("Alice")
        viewModel.onPinChanged("1234")
        viewModel.onConfirmAddPlayer()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.addPlayerError).isEqualTo("Username already exists")
    }

    @Test
    fun addPlayer_valid_createsPlayerAndDismissesDialog() = runTest {
        fakeUserRepository.nextCreatedUserId = 42L
        viewModel = ManagePlayersViewModel(fakeUserRepository)
        advanceUntilIdle()

        viewModel.onAddPlayerClicked()
        viewModel.onUsernameChanged("Bob")
        viewModel.onPinChanged("5678")
        viewModel.onConfirmAddPlayer()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showAddPlayerDialog).isFalse()
        assertThat(viewModel.uiState.value.addPlayerError).isNull()
        assertThat(viewModel.uiState.value.snackbarMessage).isEqualTo("Player Bob created successfully")
    }

    @Test
    fun deletePlayer_showsConfirmation() = runTest {
        fakeUserRepository.users = listOf(
            User(
                id = 1L,
                username = "Alice",
                pinHash = "hash",
                balance = 1000,
                totalWins = 0,
                totalLosses = 0,
                totalEarnings = 0,
                totalLost = 0,
                totalDebt = 0,
                bailoutCount = 0,
                createdAt = System.currentTimeMillis(),
            )
        )
        viewModel = ManagePlayersViewModel(fakeUserRepository)
        advanceUntilIdle()

        viewModel.onDeletePlayerClicked(1L)

        assertThat(viewModel.uiState.value.showDeleteConfirmation).isTrue()
        assertThat(viewModel.uiState.value.selectedPlayerId).isEqualTo(1L)
    }

    @Test
    fun deletePlayer_confirmed_deletesAndShowsSnackbar() = runTest {
        fakeUserRepository.users = listOf(
            User(
                id = 1L,
                username = "Alice",
                pinHash = "hash",
                balance = 1000,
                totalWins = 0,
                totalLosses = 0,
                totalEarnings = 0,
                totalLost = 0,
                totalDebt = 0,
                bailoutCount = 0,
                createdAt = System.currentTimeMillis(),
            )
        )
        viewModel = ManagePlayersViewModel(fakeUserRepository)
        advanceUntilIdle()

        viewModel.onDeletePlayerClicked(1L)
        viewModel.onConfirmDeletePlayer()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showDeleteConfirmation).isFalse()
        assertThat(viewModel.uiState.value.snackbarMessage).isEqualTo("Player deleted")
    }

    @Test
    fun deletePlayer_error_showsSnackbar() = runTest {
        fakeUserRepository.users = listOf(
            User(
                id = 1L,
                username = "Alice",
                pinHash = "hash",
                balance = 1000,
                totalWins = 0,
                totalLosses = 0,
                totalEarnings = 0,
                totalLost = 0,
                totalDebt = 0,
                bailoutCount = 0,
                createdAt = System.currentTimeMillis(),
            )
        )
        fakeUserRepository.shouldThrowOnDelete = IllegalStateException("Player is in an active bet")
        viewModel = ManagePlayersViewModel(fakeUserRepository)
        advanceUntilIdle()

        viewModel.onDeletePlayerClicked(1L)
        viewModel.onConfirmDeletePlayer()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showDeleteConfirmation).isFalse()
        assertThat(viewModel.uiState.value.snackbarMessage).isEqualTo("Cannot delete: Player is in an active bet")
    }

    @Test
    fun renamePlayer_valid_renamesAndShowsSnackbar() = runTest {
        fakeUserRepository.users = listOf(
            User(
                id = 1L,
                username = "Alice",
                pinHash = "hash",
                balance = 1000,
                totalWins = 0,
                totalLosses = 0,
                totalEarnings = 0,
                totalLost = 0,
                totalDebt = 0,
                bailoutCount = 0,
                createdAt = System.currentTimeMillis(),
            )
        )
        viewModel = ManagePlayersViewModel(fakeUserRepository)
        advanceUntilIdle()

        viewModel.onRenamePlayerClicked(1L)
        viewModel.onRenameUsernameChanged("Alicia")
        viewModel.onConfirmRenamePlayer()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showRenameDialog).isFalse()
        assertThat(viewModel.uiState.value.snackbarMessage).isEqualTo("Player renamed to Alicia")
    }

    @Test
    fun renamePlayer_emptyName_showsError() = runTest {
        fakeUserRepository.users = listOf(
            User(
                id = 1L,
                username = "Alice",
                pinHash = "hash",
                balance = 1000,
                totalWins = 0,
                totalLosses = 0,
                totalEarnings = 0,
                totalLost = 0,
                totalDebt = 0,
                bailoutCount = 0,
                createdAt = System.currentTimeMillis(),
            )
        )
        viewModel = ManagePlayersViewModel(fakeUserRepository)
        advanceUntilIdle()

        viewModel.onRenamePlayerClicked(1L)
        viewModel.onRenameUsernameChanged("")
        viewModel.onConfirmRenamePlayer()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.renameError).isEqualTo("Username cannot be empty")
    }

    @Test
    fun renamePlayer_duplicateName_showsError() = runTest {
        fakeUserRepository.users = listOf(
            User(
                id = 1L,
                username = "Alice",
                pinHash = "hash",
                balance = 1000,
                totalWins = 0,
                totalLosses = 0,
                totalEarnings = 0,
                totalLost = 0,
                totalDebt = 0,
                bailoutCount = 0,
                createdAt = System.currentTimeMillis(),
            ),
            User(
                id = 2L,
                username = "Bob",
                pinHash = "hash",
                balance = 1000,
                totalWins = 0,
                totalLosses = 0,
                totalEarnings = 0,
                totalLost = 0,
                totalDebt = 0,
                bailoutCount = 0,
                createdAt = System.currentTimeMillis(),
            )
        )
        viewModel = ManagePlayersViewModel(fakeUserRepository)
        advanceUntilIdle()

        viewModel.onRenamePlayerClicked(1L)
        viewModel.onRenameUsernameChanged("Bob")
        viewModel.onConfirmRenamePlayer()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.renameError).isEqualTo("Username already exists")
    }

    @Test
    fun bailoutPlayer_showsSnackbar() = runTest {
        fakeUserRepository.users = listOf(
            User(
                id = 1L,
                username = "Alice",
                pinHash = "hash",
                balance = 1000,
                totalWins = 0,
                totalLosses = 0,
                totalEarnings = 0,
                totalLost = 0,
                totalDebt = 0,
                bailoutCount = 0,
                createdAt = System.currentTimeMillis(),
            )
        )
        viewModel = ManagePlayersViewModel(fakeUserRepository)
        advanceUntilIdle()

        viewModel.onBailoutPlayer(1L)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.snackbarMessage).isEqualTo("Bailout granted to Alice")
    }

    @Test
    fun bailoutPlayer_error_showsSnackbar() = runTest {
        fakeUserRepository.users = listOf(
            User(
                id = 1L,
                username = "Alice",
                pinHash = "hash",
                balance = 1000,
                totalWins = 0,
                totalLosses = 0,
                totalEarnings = 0,
                totalLost = 0,
                totalDebt = 0,
                bailoutCount = 0,
                createdAt = System.currentTimeMillis(),
            )
        )
        fakeUserRepository.shouldThrowOnBailout = RuntimeException("Database error")
        viewModel = ManagePlayersViewModel(fakeUserRepository)
        advanceUntilIdle()

        viewModel.onBailoutPlayer(1L)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.snackbarMessage).isEqualTo("Failed to grant bailout")
    }

    @Test
    fun snackbarShown_clearsMessage() = runTest {
        viewModel = ManagePlayersViewModel(fakeUserRepository)
        advanceUntilIdle()

        viewModel.onSnackbarShown()

        assertThat(viewModel.uiState.value.snackbarMessage).isNull()
    }
}
