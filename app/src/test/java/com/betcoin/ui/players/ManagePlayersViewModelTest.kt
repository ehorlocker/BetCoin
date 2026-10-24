package com.betcoin.ui.players

import com.betcoin.testing.FakeUserRepository
import com.betcoin.testing.FakeAvatarStore
import android.database.sqlite.SQLiteException
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.InvalidInputException
import com.betcoin.ui.common.GENERIC_ERROR_MESSAGE
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
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
import java.io.IOException

/**
 * Tests for [ManagePlayersViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManagePlayersViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeUserRepository: FakeUserRepository
    private lateinit var fakeAvatarStore: FakeAvatarStore
    private lateinit var viewModel: ManagePlayersViewModel

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
        viewModel = ManagePlayersViewModel(fakeUserRepository, fakeAvatarStore)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
    }

    private val state get() = viewModel.uiState.value
    private val form get() = checkNotNull(state.addPlayerForm)

    private fun user(id: Long, username: String) = User(
        id = id,
        username = username,
        pinHash = FakeUserRepository.PLACEHOLDER_HASH,
        createdAt = 0L,
    )

    private fun fillForm(username: String, pin: String, confirmPin: String = pin) {
        viewModel.onAddPlayerClicked()
        viewModel.onUsernameChanged(username)
        viewModel.onPinChanged(pin)
        viewModel.onConfirmPinChanged(confirmPin)
    }

    @Test
    fun initialState_isLoading() {
        viewModel = ManagePlayersViewModel(fakeUserRepository, fakeAvatarStore)

        assertThat(state.isLoading).isTrue()
    }

    @Test
    fun avatars_mapsUsersWithAvatarRefToLoadedModel() = runTest {
        fakeUserRepository.users = listOf(user(1L, "Alice").copy(avatarRef = "a.jpg"), user(2L, "Bob"))
        fakeAvatarStore.images["a.jpg"] = "MODEL"
        createViewModel()

        assertThat(state.avatars).containsExactly(1L, "MODEL")
    }

    @Test
    fun avatars_missingImage_isAbsent() = runTest {
        fakeUserRepository.users = listOf(user(1L, "Alice").copy(avatarRef = "a.jpg"))
        createViewModel()

        assertThat(state.avatars).isEmpty()
    }

    @Test
    fun players_emitsRepositoryList() = runTest {
        fakeUserRepository.users = listOf(user(1L, "Alice"), user(2L, "Bob"))
        createViewModel()

        assertThat(state.isLoading).isFalse()
        assertThat(state.players.map { it.username }).containsExactly("Alice", "Bob").inOrder()
    }

    @Test
    fun players_emptyList() = runTest {
        createViewModel()

        assertThat(state.isLoading).isFalse()
        assertThat(state.players).isEmpty()
    }

    @Test
    fun players_followRepositoryUpdates() = runTest {
        createViewModel()

        fakeUserRepository.users = listOf(user(1L, "Alice"))
        advanceUntilIdle()

        assertThat(state.players.map { it.username }).containsExactly("Alice")
    }

    @Test
    fun addPlayerClicked_opensEmptyForm() = runTest {
        createViewModel()

        viewModel.onAddPlayerClicked()

        assertThat(state.addPlayerForm).isEqualTo(ManagePlayersViewModel.AddPlayerForm())
    }

    @Test
    fun addPlayer_emptyUsername_showsError() = runTest {
        createViewModel()

        fillForm("   ", "1234")
        viewModel.onConfirmAddPlayer()
        advanceUntilIdle()

        assertThat(form.error).isEqualTo("Username cannot be empty")
        assertThat(fakeUserRepository.users).isEmpty()
    }

    @Test
    fun addPlayer_usernameOver20Chars_showsError() = runTest {
        createViewModel()

        fillForm("A".repeat(21), "1234")
        viewModel.onConfirmAddPlayer()
        advanceUntilIdle()

        assertThat(form.error).isEqualTo("Username must be at most 20 characters")
        assertThat(fakeUserRepository.users).isEmpty()
    }

    @Test
    fun addPlayer_usernameOf20CharsAfterTrimming_isAccepted() = runTest {
        createViewModel()

        fillForm("  ${"A".repeat(20)}  ", "1234")
        viewModel.onConfirmAddPlayer()
        advanceUntilIdle()

        assertThat(state.addPlayerForm).isNull()
        assertThat(fakeUserRepository.users.single().username).isEqualTo("A".repeat(20))
    }

    @Test
    fun addPlayer_shortPin_showsError() = runTest {
        createViewModel()

        fillForm("Alice", "123")
        viewModel.onConfirmAddPlayer()
        advanceUntilIdle()

        assertThat(form.error).isEqualTo("PIN must be exactly 4 digits")
        assertThat(fakeUserRepository.users).isEmpty()
    }

    @Test
    fun pinFields_keepOnlyFourDigits() = runTest {
        createViewModel()
        viewModel.onAddPlayerClicked()

        viewModel.onPinChanged("1a2b3c4d5")
        viewModel.onConfirmPinChanged("98-765")

        assertThat(form.pin).isEqualTo("1234")
        assertThat(form.confirmPin).isEqualTo("9876")
    }

    @Test
    fun addPlayer_mismatchedPins_showsErrorAndCreatesNothing() = runTest {
        createViewModel()

        fillForm("Alice", "1234", confirmPin = "4321")
        viewModel.onConfirmAddPlayer()
        advanceUntilIdle()

        assertThat(form.error).isEqualTo("PINs don't match")
        assertThat(fakeUserRepository.users).isEmpty()
    }

    @Test
    fun addPlayer_missingConfirmation_showsMismatchError() = runTest {
        createViewModel()

        fillForm("Alice", "1234", confirmPin = "")
        viewModel.onConfirmAddPlayer()
        advanceUntilIdle()

        assertThat(form.error).isEqualTo("PINs don't match")
    }

    @Test
    fun addPlayer_duplicateUsernameIgnoringCase_showsError() = runTest {
        fakeUserRepository.users = listOf(user(1L, "Alice"))
        createViewModel()

        fillForm("alice", "1234")
        viewModel.onConfirmAddPlayer()
        advanceUntilIdle()

        assertThat(form.error).isEqualTo("Username already exists")
        assertThat(form.isSaving).isFalse()
        assertThat(fakeUserRepository.users).hasSize(1)
    }

    @Test
    fun addPlayer_valid_createsPlayerWithPinAndClosesDialog() = runTest {
        fakeUserRepository.nextCreatedUserId = 42L
        createViewModel()

        fillForm("  Bob ", "5678")
        viewModel.onConfirmAddPlayer()
        advanceUntilIdle()

        assertThat(state.addPlayerForm).isNull()
        assertThat(state.snackbarMessage).isEqualTo("Player Bob created")
        assertThat(state.players.map { it.username }).containsExactly("Bob")
        assertThat(fakeUserRepository.verifyPin(42L, "5678")).isTrue()
    }

    @Test
    fun addPlayer_repositoryRejects_showsMessageAndKeepsForm() = runTest {
        fakeUserRepository.shouldThrowOnCreate = InvalidInputException("Username must be 1–20 characters")
        createViewModel()

        fillForm("Bob", "5678")
        viewModel.onConfirmAddPlayer()
        advanceUntilIdle()

        assertThat(form.error).isEqualTo("That's no longer valid. Check it and try again.")
        assertThat(form.username).isEqualTo("Bob")
        assertThat(form.isSaving).isFalse()
    }

    @Test
    fun addPlayer_ioFailure_showsGenericMessageAndKeepsForm() = runTest {
        fakeUserRepository.shouldThrowOnCreate = IOException("disk full")
        createViewModel()

        fillForm("Bob", "5678")
        viewModel.onConfirmAddPlayer()
        advanceUntilIdle()

        assertThat(form.error).isEqualTo(GENERIC_ERROR_MESSAGE)
        assertThat(form.isSaving).isFalse()
    }

    @Test
    fun addPlayer_sqliteFailure_showsGenericMessage() = runTest {
        fakeUserRepository.shouldThrowOnCreate = SQLiteException("locked")
        createViewModel()

        fillForm("Bob", "5678")
        viewModel.onConfirmAddPlayer()
        advanceUntilIdle()

        assertThat(form.error).isEqualTo(GENERIC_ERROR_MESSAGE)
        assertThat(form.isSaving).isFalse()
    }

    @Test
    fun usersEmissionDuringCreateUserIsNotLost() = runTest {
        val gate = CompletableDeferred<Unit>()
        fakeUserRepository.createUserGate = gate
        createViewModel()
        fillForm("Bob", "5678")

        viewModel.onConfirmAddPlayer()
        fakeUserRepository.users = fakeUserRepository.users + user(99L, "Other")
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(state.players.map { it.username }).containsAtLeast("Other", "Bob")
        assertThat(state.addPlayerForm).isNull()
    }

    @Test
    fun addPlayer_errorClearsOnEdit() = runTest {
        createViewModel()

        fillForm("", "1234")
        viewModel.onConfirmAddPlayer()
        assertThat(form.error).isNotNull()

        viewModel.onUsernameChanged("A")
        assertThat(form.error).isNull()
    }

    @Test
    fun dismissAddPlayer_closesFormAndDropsPins() = runTest {
        createViewModel()
        fillForm("Bob", "5678")

        viewModel.onDismissAddPlayerDialog()
        assertThat(state.addPlayerForm).isNull()

        viewModel.onAddPlayerClicked()
        assertThat(form.pin).isEmpty()
        assertThat(form.confirmPin).isEmpty()
    }

    @Test
    fun snackbarShown_clearsMessage() = runTest {
        createViewModel()
        fillForm("Bob", "5678")
        viewModel.onConfirmAddPlayer()
        advanceUntilIdle()

        viewModel.onSnackbarShown()

        assertThat(state.snackbarMessage).isNull()
    }

    @Test
    fun initialsOf_usesFirstLettersOfUpToTwoWords() {
        assertThat(initialsOf("jay")).isEqualTo("J")
        assertThat(initialsOf(" Mary  jane watson ")).isEqualTo("MJ")
    }
}
