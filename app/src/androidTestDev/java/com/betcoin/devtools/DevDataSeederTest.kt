package com.betcoin.devtools

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.betcoin.testing.FakeAvatarStore
import com.betcoin.data.database.BetCoinDatabase
import com.betcoin.data.model.BetStatus
import com.betcoin.data.repository.local.LocalBetRepository
import com.betcoin.data.repository.local.LocalSettingsRepository
import com.betcoin.data.repository.local.LocalUserRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Instrumented tests for [DevDataSeeder] against an in-memory Room database. */
@RunWith(AndroidJUnit4::class)
class DevDataSeederTest {

    private lateinit var context: Context
    private lateinit var database: BetCoinDatabase
    private lateinit var users: LocalUserRepository
    private lateinit var bets: LocalBetRepository
    private lateinit var settings: LocalSettingsRepository
    private lateinit var seeder: DevDataSeeder

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, BetCoinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        users = LocalUserRepository(database, FakeAvatarStore())
        bets = LocalBetRepository(database)
        settings = LocalSettingsRepository(database.appSettingsDao())
        seeder = DevDataSeeder(
            users, bets, settings,
            DevDatabaseTools(database, database.appSettingsDao(), context),
        )
        File(context.filesDir, "avatars").deleteRecursively()
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "avatars").deleteRecursively()
    }

    private suspend fun allUsers() = users.getAllUsers().first()

    private suspend fun allBets() = bets.getAllBets().first()

    @Test
    fun seedSampleData_createsAdminPinAndRecoveryCode() = runTest {
        val code = seeder.seedSampleData()

        assertThat(settings.verifyAdminPin("0000")).isTrue()
        assertThat(code).matches("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}")
        val newCode = settings.resetAdminPinWithRecoveryCode(code, "9999")
        assertThat(newCode).isNotEmpty()
        assertThat(settings.verifyAdminPin("9999")).isTrue()
    }

    @Test
    fun seedSampleData_createsFivePlayersWithPins() = runTest {
        seeder.seedSampleData()

        val all = allUsers()
        assertThat(all.map { it.username })
            .containsExactly("Austin", "Sam", "Jay", "Eric", "Mike")
        listOf("Austin" to "1111", "Sam" to "2222", "Jay" to "3333", "Eric" to "4444", "Mike" to "5555")
            .forEach { (name, pin) ->
                val user = all.first { it.username == name }
                assertThat(users.verifyPin(user.id, pin)).isTrue()
                assertThat(users.verifyPin(user.id, "0000")).isFalse()
            }
        assertThat(all.map { it.username }).doesNotContain("Taylor")
        assertThat(DevDataSeeder.SAMPLE_PLAYERS.map { it.first })
            .containsExactly("Austin", "Sam", "Jay", "Eric", "Mike").inOrder()
    }

    @Test
    fun seedSampleData_mikeIsBailoutEligible() = runTest {
        seeder.seedSampleData()

        val mike = allUsers().first { it.username == "Mike" }
        assertThat(mike.bailoutCount).isEqualTo(2)
        assertThat(mike.totalDebt).isEqualTo(2000L)
        assertThat(mike.balance).isEqualTo(40L)
        users.bailout(mike.id)
        val after = users.getUser(mike.id)!!
        assertThat(after.bailoutCount).isEqualTo(3)
        assertThat(after.balance).isEqualTo(1040L)
    }

    @Test
    fun seedSampleData_hasActiveBetWithUnpickedOutcome() = runTest {
        seeder.seedSampleData()

        val active = bets.getActiveBets().first()
        assertThat(active).hasSize(1)
        val bet = active.single()
        assertThat(bet.outcomes).hasSize(3)
        val picked = bet.participants.map { it.participant.outcomeId }.toSet()
        assertThat(bet.outcomes.count { it.id !in picked }).isEqualTo(1)
    }

    @Test
    fun seedSampleData_hasResolvedCancelledAndDeletedPlayerBets() = runTest {
        seeder.seedSampleData()

        val all = allBets()
        assertThat(all.count { it.bet.status == BetStatus.RESOLVED }).isEqualTo(2)
        assertThat(all.count { it.bet.status == BetStatus.CANCELLED }).isEqualTo(1)
        val deleted = all.filter { it.bet.status == BetStatus.RESOLVED }
            .flatMap { it.participants }
            .filter { it.participant.userId == null }
        assertThat(deleted.map { it.participant.deletedUsername }).containsExactly("Taylor")
    }

    @Test
    fun seedSampleData_twice_givesSameResult() = runTest {
        seeder.seedSampleData()
        val firstUsers = allUsers().map { it.username to it.balance }
        val firstBets = allBets().map { it.bet.prompt to it.bet.status }.toSet()

        users.createUser("Extra", "1234")
        bets.createBet("Pending?", listOf("A", "B"))
        seeder.seedSampleData()

        assertThat(allUsers().map { it.username to it.balance })
            .containsExactlyElementsIn(firstUsers)
        assertThat(allBets().map { it.bet.prompt to it.bet.status }.toSet()).isEqualTo(firstBets)
        assertThat(allBets()).hasSize(firstBets.size)
        assertThat(bets.getPendingBet().first()).isNull()
    }

    @Test
    fun seedPendingBet_addsPendingBetWithTwoParticipants() = runTest {
        seeder.seedSampleData()
        val balancesBefore = allUsers().associate { it.id to it.balance }

        seeder.seedPendingBet()

        val pending = bets.getPendingBet().first()!!
        assertThat(pending.bet.status).isEqualTo(BetStatus.PENDING_CONFIRMATION)
        assertThat(pending.participants).hasSize(2)
        assertThat(pending.participants.map { it.participant.outcomeId }.toSet()).hasSize(2)
        assertThat(allUsers().associate { it.id to it.balance }).isEqualTo(balancesBefore)
    }

    @Test
    fun seedPendingBet_withFewerThanTwoPlayers_throwsAndLeavesNoBet() = runTest {
        users.createUser("Solo", "1234")

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { seeder.seedPendingBet() }
        }

        assertThat(bets.getPendingBet().first()).isNull()
        assertThat(database.betDao().getAll().first()).isEmpty()
    }

    @Test
    fun seedPendingBet_whenPendingExists_throws() = runTest {
        seeder.seedSampleData()
        seeder.seedPendingBet()
        val pendingId = bets.getPendingBet().first()!!.bet.id

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { seeder.seedPendingBet() }
        }

        assertThat(bets.getPendingBet().first()!!.bet.id).isEqualTo(pendingId)
    }
}
