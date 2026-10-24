package com.betcoin.testing

import android.net.Uri
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.ActiveBetConflictException
import com.betcoin.data.model.DuplicateUsernameException
import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.model.NotEligibleForBailoutException
import com.betcoin.data.repository.UserRepository
import com.betcoin.data.repository.local.LocalUserRepository
import com.betcoin.data.security.PinHasher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Shared in-memory fake of [UserRepository] for unit and instrumented tests.
 *
 * Applies the same rules as [LocalUserRepository] and hashes PINs with the same [PinHasher] (its blocking forms, so tests stay on the test dispatcher), so
 * no plain-text PIN is ever kept. Users seeded through [users] or [userCount] carry a placeholder
 * hash that no PIN matches; create users with [createUser] to test PIN checks.
 */
class FakeUserRepository : UserRepository {
    private val _users = MutableStateFlow<List<User>>(emptyList())
    private val _lockedBalances = MutableStateFlow<Map<Long, Long>>(emptyMap())

    var shouldThrowOnCreate: Throwable? = null
    var shouldThrowOnDelete: Throwable? = null
    var shouldThrowOnBailout: Throwable? = null
    var shouldThrowOnRename: Throwable? = null
    var shouldThrowOnProfilePicture: Throwable? = null
    var shouldThrowOnVerifyPin: Throwable? = null

    /** When set, [createUser] suspends on it before doing anything, so tests can interleave other writes. */
    var createUserGate: CompletableDeferred<Unit>? = null
    var nextCreatedUserId: Long = 1L

    /** IDs of users treated as participants in an ACTIVE bet (blocks [deleteUser]). */
    var usersInActiveBets: Set<Long> = emptySet()

    var users: List<User>
        get() = _users.value
        set(value) { _users.value = value }

    /** Coins each user has locked in ACTIVE bets, keyed by user ID. */
    var lockedBalances: Map<Long, Long>
        get() = _lockedBalances.value
        set(value) { _lockedBalances.value = value }

    /** Seeds [value] placeholder users with IDs 0 until [value], named `User<n>`. */
    var userCount: Int
        get() = users.size
        set(value) {
            users = List(value) { index ->
                User(
                    id = index.toLong(),
                    username = "User$index",
                    pinHash = PLACEHOLDER_HASH,
                    createdAt = System.currentTimeMillis(),
                )
            }
        }

    /**
     * Adds a player whose PIN is [pin] (hashed like the real repository) with the given stats and
     * returns their ID.
     */
    fun addPlayer(
        username: String,
        pin: String = "1234",
        balance: Long = LocalUserRepository.STARTING_BALANCE,
        totalDebt: Long = 0L,
        totalWins: Int = 0,
        totalLosses: Int = 0,
    ): Long {
        val id = (users.maxOfOrNull { it.id } ?: 0L) + 1
        users = users + User(
            id = id,
            username = username,
            pinHash = PinHasher.hashBlocking(pin),
            balance = balance,
            totalDebt = totalDebt,
            totalWins = totalWins,
            totalLosses = totalLosses,
            createdAt = 0L,
        )
        return id
    }

    /** Number of collectors currently subscribed to [getAllUsers]. */
    val allUsersSubscriberCount: Int
        get() = _users.subscriptionCount.value

    override fun getAllUsers(): Flow<List<User>> = _users

    override fun getLeaderboard(): Flow<List<User>> = _users.map { list ->
        list.sortedWith(
            compareByDescending<User> { it.balance - it.totalDebt }
                .thenByDescending { it.balance }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.username }
        )
    }

    override fun getLockedBalance(userId: Long): Flow<Long> =
        _lockedBalances.map { it[userId] ?: 0L }

    override suspend fun createUser(username: String, pin: String): Long {
        createUserGate?.await()
        shouldThrowOnCreate?.let { throw it }
        val name = validUsername(username)
        requireValidPin(pin)
        requireUnique(name, exceptId = null)
        val id = nextCreatedUserId++
        users = users + User(
            id = id,
            username = name,
            pinHash = PinHasher.hashBlocking(pin),
            balance = LocalUserRepository.STARTING_BALANCE,
            createdAt = System.currentTimeMillis(),
        )
        return id
    }

    override suspend fun verifyPin(userId: Long, pin: String): Boolean {
        shouldThrowOnVerifyPin?.let { throw it }
        val user = users.find { it.id == userId } ?: return false
        return PinHasher.verifyBlocking(pin, user.pinHash)
    }

    override suspend fun getUser(userId: Long): User? = users.find { it.id == userId }

    override suspend fun bailout(userId: Long) {
        shouldThrowOnBailout?.let { throw it }
        val user = requireUser(userId)
        val locked = lockedBalances[userId] ?: 0L
        if (user.balance + locked >= LocalUserRepository.BAILOUT_THRESHOLD) {
            throw NotEligibleForBailoutException(userId)
        }
        forceBailout(userId)
    }

    override suspend fun forceBailout(userId: Long) {
        requireUser(userId)
        val amount = LocalUserRepository.BAILOUT_AMOUNT
        updateUser(userId) {
            it.copy(
                balance = it.balance + amount,
                bailoutCount = it.bailoutCount + 1,
                totalDebt = it.totalDebt + amount,
            )
        }
    }

    override suspend fun deleteUser(userId: Long) {
        shouldThrowOnDelete?.let { throw it }
        val user = requireUser(userId)
        if (userId in usersInActiveBets) {
            throw ActiveBetConflictException("${user.username} is in an active bet and can't be deleted")
        }
        users = users.filterNot { it.id == userId }
    }

    override suspend fun resetPin(userId: Long, newPin: String) {
        requireValidPin(newPin)
        requireUser(userId)
        val pinHash = PinHasher.hashBlocking(newPin)
        updateUser(userId) { it.copy(pinHash = pinHash) }
    }

    override suspend fun updateBalance(userId: Long, delta: Long) {
        requireUser(userId)
        updateUser(userId) { it.copy(balance = it.balance + delta) }
    }

    override suspend fun setBalance(userId: Long, newBalance: Long) {
        requireUser(userId)
        updateUser(userId) { it.copy(balance = newBalance) }
    }

    override suspend fun updateUsername(userId: Long, newUsername: String) {
        shouldThrowOnRename?.let { throw it }
        val name = validUsername(newUsername)
        requireUser(userId)
        requireUnique(name, exceptId = userId)
        updateUser(userId) { it.copy(username = name) }
    }

    /** User IDs passed to [updateProfilePicture], in call order. */
    val profilePictureUpdates = mutableListOf<Long>()

    /** User IDs passed to [clearProfilePicture], in call order. */
    val profilePictureClears = mutableListOf<Long>()

    override suspend fun updateProfilePicture(userId: Long, imageUri: Uri) {
        shouldThrowOnProfilePicture?.let { throw it }
        requireUser(userId)
        profilePictureUpdates += userId
    }

    override suspend fun clearProfilePicture(userId: Long) {
        shouldThrowOnProfilePicture?.let { throw it }
        requireUser(userId)
        profilePictureClears += userId
    }

    private fun updateUser(userId: Long, transform: (User) -> User) {
        users = users.map { if (it.id == userId) transform(it) else it }
    }

    private fun requireUser(userId: Long): User =
        users.find { it.id == userId } ?: throw InvalidInputException("User $userId doesn't exist")

    private fun requireUnique(name: String, exceptId: Long?) {
        if (users.any { it.id != exceptId && it.username.equals(name, ignoreCase = true) }) {
            throw DuplicateUsernameException(name)
        }
    }

    private fun validUsername(username: String): String {
        val name = username.trim()
        if (name.length !in 1..LocalUserRepository.MAX_USERNAME_LENGTH) {
            throw InvalidInputException("Username must be 1–20 characters")
        }
        return name
    }

    private fun requireValidPin(pin: String) {
        if (!PinHasher.isValidPin(pin)) throw InvalidInputException("PIN must be exactly 4 digits")
    }

    companion object {
        /** Stand-in hash for seeded users. It isn't a valid bcrypt hash, so no PIN matches it. */
        const val PLACEHOLDER_HASH = "not-a-bcrypt-hash"
    }
}
