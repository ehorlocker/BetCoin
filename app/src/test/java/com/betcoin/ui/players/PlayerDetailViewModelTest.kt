package com.betcoin.ui.players

import androidx.lifecycle.SavedStateHandle
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.BetStatus
import com.betcoin.data.model.InvalidBetStateException
import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.model.NotEligibleForBailoutException
import com.betcoin.data.model.InvalidImageException
import com.betcoin.navigation.Routes
import com.betcoin.testing.FakeAvatarStore
import com.betcoin.testing.FakeCameraCaptureStore
import com.betcoin.testing.testUri
import kotlinx.coroutines.CompletableDeferred
import com.betcoin.testing.FakeUserRepository
import com.betcoin.ui.common.GENERIC_ERROR_MESSAGE
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
import java.io.IOException

/**
 * Tests for [PlayerDetailViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeUserRepository
    private val avatarStore = FakeAvatarStore()
    private val cameraStore = FakeCameraCaptureStore()
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

    private fun TestScope.createViewModel(
        id: Long = playerId,
        state: Map<String, Any?> = emptyMap(),
    ) {
        viewModel = newViewModel(SavedStateHandle(mapOf<String, Any?>(Routes.PLAYER_ID_ARG to id) + state))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun newViewModel(handle: SavedStateHandle) =
        PlayerDetailViewModel(handle, repository, avatarStore, cameraStore)

    private val loaded get() = viewModel.uiState.value as UiState.Loaded

    @Test
    fun initialState_isLoading() {
        viewModel = newViewModel(SavedStateHandle(mapOf(Routes.PLAYER_ID_ARG to 1L)))

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
    fun verifyPin_falseWhenTheRepositoryThrowsIoOrBusinessError() = runTest {
        playerId = createPlayer(balance = 0)
        createViewModel()

        repository.shouldThrowOnVerifyPin = IOException("disk")
        assertThat(viewModel.verifyPin("1234")).isFalse()
        repository.shouldThrowOnVerifyPin = InvalidInputException("User 1 not found")
        assertThat(viewModel.verifyPin("1234")).isFalse()
    }

    @Test
    fun verifyPin_propagatesUnexpectedExceptions() = runTest {
        playerId = createPlayer(balance = 0)
        createViewModel()
        repository.shouldThrowOnVerifyPin = IllegalStateException("bug")

        val result = runCatching { viewModel.verifyPin("1234") }

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
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
    fun pinVerified_otherBusinessError_showsMapperTextNotExceptionMessage() = runTest {
        playerId = createPlayer(balance = 0)
        repository.shouldThrowOnBailout = InvalidBetStateException(77L, BetStatus.RESOLVED)
        createViewModel()

        viewModel.onBailoutClicked()
        viewModel.onBailoutPinVerified()
        advanceUntilIdle()

        assertThat(loaded.message).isEqualTo("This bet has changed and can't be updated any more")
        assertThat(loaded.isBailingOut).isFalse()
    }

    @Test
    fun pinVerified_ioFailure_showsGenericMessage() = runTest {
        playerId = createPlayer(balance = 0)
        repository.shouldThrowOnBailout = IOException("disk")
        createViewModel()

        viewModel.onBailoutClicked()
        viewModel.onBailoutPinVerified()
        advanceUntilIdle()

        assertThat(loaded.message).isEqualTo(GENERIC_ERROR_MESSAGE)
        assertThat(loaded.isBailingOut).isFalse()
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

    private suspend fun createPlayerWithAvatar(ref: String = "ref.jpg"): Long {
        val id = createPlayer(balance = 500)
        repository.users = repository.users.map { if (it.id == id) it.copy(avatarRef = ref) else it }
        return id
    }

    @Test
    fun loaded_withAvatarRef_exposesModelAndHasProfilePicture() = runTest {
        avatarStore.images["ref.jpg"] = "MODEL"
        playerId = createPlayerWithAvatar()
        createViewModel()

        assertThat(loaded.avatarModel).isEqualTo("MODEL")
        assertThat(loaded.hasProfilePicture).isTrue()
    }

    @Test
    fun loaded_missingAvatarImage_modelNullButHasProfilePicture() = runTest {
        playerId = createPlayerWithAvatar()
        createViewModel()

        assertThat(loaded.avatarModel).isNull()
        assertThat(loaded.hasProfilePicture).isTrue()
    }

    @Test
    fun changePicture_showsPinThenOptions() = runTest {
        playerId = createPlayer(balance = 500)
        createViewModel()

        viewModel.onChangePictureClicked()
        assertThat(loaded.showPicturePin).isTrue()

        viewModel.onPicturePinVerified()
        assertThat(loaded.showPicturePin).isFalse()
        assertThat(loaded.showPictureOptions).isTrue()
    }

    @Test
    fun pinDismissed_hidesPin_noOptions() = runTest {
        playerId = createPlayer(balance = 500)
        createViewModel()

        viewModel.onChangePictureClicked()
        viewModel.onPicturePinDismissed()

        assertThat(loaded.showPicturePin).isFalse()
        assertThat(loaded.showPictureOptions).isFalse()
    }

    @Test
    fun imagePicked_null_changesNothing() = runTest {
        playerId = createPlayer(balance = 500)
        createViewModel()

        viewModel.onImagePicked(null)
        advanceUntilIdle()

        assertThat(repository.profilePictureUpdates).isEmpty()
        assertThat(loaded.message).isNull()
    }

    @Test
    fun imagePicked_updatesAndShowsMessage() = runTest {
        playerId = createPlayer(balance = 500)
        createViewModel()

        viewModel.onImagePicked(testUri())
        advanceUntilIdle()

        assertThat(repository.profilePictureUpdates).containsExactly(playerId)
        assertThat(loaded.message).isEqualTo("Profile picture updated")
        assertThat(loaded.isUpdatingPicture).isFalse()
    }

    @Test
    fun imagePicked_invalidImage_showsCouldntUseThatImage() = runTest {
        playerId = createPlayer(balance = 500)
        repository.shouldThrowOnProfilePicture = InvalidImageException()
        createViewModel()

        viewModel.onImagePicked(testUri())
        advanceUntilIdle()

        assertThat(loaded.message).isEqualTo("Couldn't use that image")
        assertThat(loaded.isUpdatingPicture).isFalse()
    }

    @Test
    fun imagePicked_ioError_showsGenericMessage() = runTest {
        playerId = createPlayer(balance = 500)
        repository.shouldThrowOnProfilePicture = IOException("disk")
        createViewModel()

        viewModel.onImagePicked(testUri())
        advanceUntilIdle()

        assertThat(loaded.message).isEqualTo(GENERIC_ERROR_MESSAGE)
        assertThat(loaded.isUpdatingPicture).isFalse()
    }

    @Test
    fun imagePicked_whileUpdating_isIgnored() = runTest {
        playerId = createPlayer(balance = 500)
        val gate = CompletableDeferred<Unit>()
        val blocking = object : com.betcoin.data.repository.UserRepository by repository {
            override suspend fun updateProfilePicture(userId: Long, imageUri: android.net.Uri) {
                repository.updateProfilePicture(userId, imageUri)
                gate.await()
            }
        }
        viewModel = PlayerDetailViewModel(
            SavedStateHandle(mapOf(Routes.PLAYER_ID_ARG to playerId)),
            blocking,
            avatarStore,
            cameraStore,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onImagePicked(testUri())
        advanceUntilIdle()
        assertThat(loaded.isUpdatingPicture).isTrue()
        viewModel.onImagePicked(testUri())
        viewModel.onChangePictureClicked()
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(repository.profilePictureUpdates).hasSize(1)
        assertThat(loaded.showPicturePin).isFalse()
        assertThat(loaded.isUpdatingPicture).isFalse()
    }

    @Test
    fun takePhoto_setsLaunchCameraAndSavedUri() = runTest {
        playerId = createPlayer(balance = 500)
        createViewModel()
        viewModel.onPicturePinVerified()

        viewModel.onTakePhotoSelected()
        advanceUntilIdle()

        assertThat(loaded.launchCamera).isSameInstanceAs(cameraStore.nextUri)
        assertThat(loaded.showPictureOptions).isFalse()
    }

    @Test
    fun takePhoto_twice_createsOneCapture() = runTest {
        playerId = createPlayer(balance = 500)
        createViewModel()

        viewModel.onTakePhotoSelected()
        viewModel.onTakePhotoSelected()
        advanceUntilIdle()
        viewModel.onTakePhotoSelected()
        advanceUntilIdle()

        assertThat(cameraStore.createCount).isEqualTo(1)
    }

    @Test
    fun cameraUnavailable_discardsAndShowsGenericMessage() = runTest {
        playerId = createPlayer(balance = 500)
        createViewModel()
        viewModel.onTakePhotoSelected()
        advanceUntilIdle()
        viewModel.onCameraLaunched()

        viewModel.onCameraUnavailable()
        advanceUntilIdle()

        assertThat(cameraStore.discarded).containsExactly(cameraStore.nextUri)
        assertThat(repository.profilePictureUpdates).isEmpty()
        assertThat(loaded.message).isEqualTo(GENERIC_ERROR_MESSAGE)
    }

    @Test
    fun cameraLaunched_clearsLaunchCamera() = runTest {
        playerId = createPlayer(balance = 500)
        createViewModel()
        viewModel.onTakePhotoSelected()
        advanceUntilIdle()

        viewModel.onCameraLaunched()

        assertThat(loaded.launchCamera).isNull()
        // The saved capture Uri survives, so the result can still be handled.
        viewModel.onPhotoCaptured(true)
        advanceUntilIdle()
        assertThat(repository.profilePictureUpdates).containsExactly(playerId)
    }

    @Test
    fun photoCaptured_success_updatesThenDiscards() = runTest {
        playerId = createPlayer(balance = 500)
        createViewModel()
        viewModel.onTakePhotoSelected()
        advanceUntilIdle()

        viewModel.onPhotoCaptured(true)
        advanceUntilIdle()

        assertThat(repository.profilePictureUpdates).containsExactly(playerId)
        assertThat(cameraStore.discarded).containsExactly(cameraStore.nextUri)
        assertThat(loaded.message).isEqualTo("Profile picture updated")
    }

    @Test
    fun photoCaptured_updateFails_stillDiscards() = runTest {
        playerId = createPlayer(balance = 500)
        repository.shouldThrowOnProfilePicture = InvalidImageException()
        createViewModel()
        viewModel.onTakePhotoSelected()
        advanceUntilIdle()

        viewModel.onPhotoCaptured(true)
        advanceUntilIdle()

        assertThat(cameraStore.discarded).containsExactly(cameraStore.nextUri)
        assertThat(loaded.message).isEqualTo("Couldn't use that image")
    }

    @Test
    fun photoCaptured_cancelled_discardsWithoutUpdate() = runTest {
        playerId = createPlayer(balance = 500)
        createViewModel()
        viewModel.onTakePhotoSelected()
        advanceUntilIdle()

        viewModel.onPhotoCaptured(false)
        advanceUntilIdle()

        assertThat(repository.profilePictureUpdates).isEmpty()
        assertThat(cameraStore.discarded).containsExactly(cameraStore.nextUri)
    }

    @Test
    fun photoCaptured_withoutPendingCapture_doesNothing() = runTest {
        playerId = createPlayer(balance = 500)
        createViewModel()

        viewModel.onPhotoCaptured(true)
        advanceUntilIdle()

        assertThat(repository.profilePictureUpdates).isEmpty()
        assertThat(cameraStore.discarded).isEmpty()
    }

    @Test
    fun photoCaptured_restoresUriFromSavedState() = runTest {
        playerId = createPlayer(balance = 500)
        val uri = testUri()
        createViewModel(state = mapOf("captureUri" to uri))

        viewModel.onPhotoCaptured(true)
        advanceUntilIdle()

        assertThat(repository.profilePictureUpdates).containsExactly(playerId)
        assertThat(cameraStore.discarded).containsExactly(uri)
    }

    @Test
    fun takePhoto_createFails_showsGenericMessage() = runTest {
        playerId = createPlayer(balance = 500)
        cameraStore.createFailure = IOException("no space")
        createViewModel()

        viewModel.onTakePhotoSelected()
        advanceUntilIdle()

        assertThat(loaded.message).isEqualTo(GENERIC_ERROR_MESSAGE)
        assertThat(loaded.launchCamera).isNull()
    }

    @Test
    fun removePhoto_clearsAndShowsMessage() = runTest {
        playerId = createPlayerWithAvatar()
        createViewModel()

        viewModel.onRemovePhotoSelected()
        advanceUntilIdle()

        assertThat(repository.profilePictureClears).containsExactly(playerId)
        assertThat(loaded.message).isEqualTo("Profile picture removed")
        assertThat(loaded.isUpdatingPicture).isFalse()
    }
}
