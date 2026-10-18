package com.betcoin.testing

import com.betcoin.data.model.DuplicateUsernameException
import com.betcoin.data.model.NotEligibleForBailoutException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test

/** Checks that [FakeUserRepository] keeps no plain-text PINs and follows the real rules. */
class FakeUserRepositoryTest {

    private val repository = FakeUserRepository()

    @Test
    fun createUser_storesBcryptHashNotPin() = runTest {
        val id = repository.createUser("Alice", "1234")

        val user = repository.getUser(id)!!
        assertThat(user.pinHash).startsWith("\$2a\$10\$")
        assertThat(user.pinHash).doesNotContain("1234")
        assertThat(repository.verifyPin(id, "1234")).isTrue()
        assertThat(repository.verifyPin(id, "0000")).isFalse()
    }

    @Test
    fun resetPin_rehashes() = runTest {
        val id = repository.createUser("Alice", "1234")

        repository.resetPin(id, "5678")

        assertThat(repository.verifyPin(id, "5678")).isTrue()
        assertThat(repository.verifyPin(id, "1234")).isFalse()
    }

    @Test
    fun seededUsers_matchNoPin() = runTest {
        repository.userCount = 1
        assertThat(repository.verifyPin(0L, "1234")).isFalse()
    }

    @Test
    fun createUser_duplicateIgnoringCase_throws() = runTest {
        repository.createUser("Jay", "1234")
        try {
            repository.createUser("jay", "1234")
            fail("Expected DuplicateUsernameException")
        } catch (e: DuplicateUsernameException) {
            assertThat(repository.users).hasSize(1)
        }
    }

    @Test
    fun bailout_countsLockedBalance() = runTest {
        val id = repository.createUser("Alice", "1234")
        repository.setBalance(id, 50)
        repository.lockedBalances = mapOf(id to 50L)
        try {
            repository.bailout(id)
            fail("Expected NotEligibleForBailoutException")
        } catch (e: NotEligibleForBailoutException) {
            assertThat(repository.getUser(id)!!.bailoutCount).isEqualTo(0)
        }
    }
}
