package com.betcoin.ui.admin

import com.betcoin.testing.FakeSettingsRepository
import com.betcoin.data.repository.SettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Tests for [AdminGateViewModel]: the admin PIN verifier used by the gate dialog. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminGateViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeSettingsRepository: FakeSettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSettingsRepository = FakeSettingsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun verifyPin_correct_returnsTrue() = runTest(testDispatcher) {
        fakeSettingsRepository.setAdminPin("1234")
        val viewModel = AdminGateViewModel(fakeSettingsRepository)

        assertThat(viewModel.verifyPin("1234")).isTrue()
    }

    @Test
    fun verifyPin_wrong_returnsFalse() = runTest(testDispatcher) {
        fakeSettingsRepository.setAdminPin("1234")
        val viewModel = AdminGateViewModel(fakeSettingsRepository)

        assertThat(viewModel.verifyPin("9999")).isFalse()
    }

    @Test
    fun verifyPin_repositoryThrows_returnsFalse() = runTest(testDispatcher) {
        val throwing = object : SettingsRepository by fakeSettingsRepository {
            override suspend fun verifyAdminPin(pin: String): Boolean =
                throw IllegalStateException("boom")
        }
        val viewModel = AdminGateViewModel(throwing)

        assertThat(viewModel.verifyPin("1234")).isFalse()
    }
}
