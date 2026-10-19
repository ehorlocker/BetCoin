package com.betcoin.data.repository.local

import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.room.withTransaction
import com.betcoin.data.avatar.AvatarStore
import com.betcoin.data.database.BetCoinDatabase
import com.betcoin.data.database.entity.User
import com.betcoin.data.model.ActiveBetConflictException
import com.betcoin.data.model.DuplicateUsernameException
import com.betcoin.data.model.InvalidInputException
import com.betcoin.data.model.NotEligibleForBailoutException
import com.betcoin.data.repository.UserRepository
import com.betcoin.data.security.PinHasher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Room-backed implementation of [UserRepository].
 *
 * PINs are hashed with bcrypt through [PinHasher] and never stored or logged in plain text.
 * Every rule is checked before writing, and multi-step writes run in one Room transaction, so a
 * thrown [com.betcoin.data.model.BetCoinException] leaves the database unchanged. Profile
 * picture files live in [AvatarStore]; they are only removed after the database change commits.
 */
class LocalUserRepository @Inject constructor(
    private val database: BetCoinDatabase,
    private val avatarStore: AvatarStore,
) : UserRepository {

    private val userDao = database.userDao()
    private val participantDao = database.betParticipantDao()

    override suspend fun createUser(username: String, pin: String): Long {
        val name = validUsername(username)
        requireValidPin(pin)
        val pinHash = PinHasher.hash(pin)
        return database.withTransaction {
            if (userDao.getByUsername(name) != null) throw DuplicateUsernameException(name)
            try {
                userDao.insert(
                    User(
                        username = name,
                        pinHash = pinHash,
                        balance = STARTING_BALANCE,
                        createdAt = System.currentTimeMillis(),
                    )
                )
            } catch (e: SQLiteConstraintException) {
                throw DuplicateUsernameException(name)
            }
        }
    }

    override suspend fun verifyPin(userId: Long, pin: String): Boolean {
        val user = userDao.getById(userId) ?: return false
        return PinHasher.verify(pin, user.pinHash)
    }

    override suspend fun getUser(userId: Long): User? = userDao.getById(userId)

    override fun getAllUsers(): Flow<List<User>> = userDao.getAll()

    override fun getLeaderboard(): Flow<List<User>> = userDao.getLeaderboard()

    override fun getLockedBalance(userId: Long): Flow<Long> =
        userDao.observeLockedBalance(userId)

    override suspend fun bailout(userId: Long) {
        database.withTransaction {
            val user = requireUser(userId)
            val locked = userDao.getLockedBalance(userId)
            if (user.balance + locked >= BAILOUT_THRESHOLD) {
                throw NotEligibleForBailoutException(userId)
            }
            userDao.applyBailout(userId, BAILOUT_AMOUNT)
        }
    }

    override suspend fun forceBailout(userId: Long) {
        if (userDao.applyBailout(userId, BAILOUT_AMOUNT) == 0) throw userNotFound(userId)
    }

    override suspend fun deleteUser(userId: Long) {
        val avatarRef = database.withTransaction {
            val user = requireUser(userId)
            if (userDao.countBetsWithStatus(userId) > 0) {
                throw ActiveBetConflictException(
                    "${user.username} is in an active bet and can't be deleted"
                )
            }
            participantDao.detachUser(userId, user.username)
            userDao.delete(user)
            user.avatarRef
        }
        avatarRef?.let { withContext(NonCancellable) { avatarStore.delete(it) } }
    }

    override suspend fun resetPin(userId: Long, newPin: String) {
        requireValidPin(newPin)
        val pinHash = PinHasher.hash(newPin)
        database.withTransaction {
            val user = requireUser(userId)
            userDao.update(user.copy(pinHash = pinHash))
        }
    }

    override suspend fun updateBalance(userId: Long, delta: Long) {
        if (userDao.addToBalance(userId, delta) == 0) throw userNotFound(userId)
    }

    override suspend fun setBalance(userId: Long, newBalance: Long) {
        database.withTransaction {
            requireUser(userId)
            userDao.updateBalance(userId, newBalance)
        }
    }

    override suspend fun updateUsername(userId: Long, newUsername: String) {
        val name = validUsername(newUsername)
        database.withTransaction {
            val user = requireUser(userId)
            val existing = userDao.getByUsername(name)
            if (existing != null && existing.id != userId) throw DuplicateUsernameException(name)
            try {
                userDao.update(user.copy(username = name))
            } catch (e: SQLiteConstraintException) {
                throw DuplicateUsernameException(name)
            }
        }
    }

    override suspend fun updateProfilePicture(userId: Long, imageUri: Uri) {
        requireUser(userId)
        val newRef = avatarStore.save(imageUri)
        // Set as the last statement of the transaction block: from then on the DB points at
        // newRef, so a later failure (e.g. cancellation while resuming) must keep the file.
        var committed = false
        val oldRef = try {
            database.withTransaction {
                val old = requireUser(userId).avatarRef
                if (userDao.updateAvatarRef(userId, newRef) == 0) throw userNotFound(userId)
                committed = true
                old
            }
        } catch (e: Throwable) {
            if (!committed) withContext(NonCancellable) { avatarStore.delete(newRef) }
            throw e
        }
        if (oldRef != null && oldRef != newRef) {
            withContext(NonCancellable) { avatarStore.delete(oldRef) }
        }
    }

    override suspend fun clearProfilePicture(userId: Long) {
        val oldRef = requireUser(userId).avatarRef ?: return
        avatarStore.delete(oldRef)
        userDao.updateAvatarRef(userId, null)
    }

    private suspend fun requireUser(userId: Long): User =
        userDao.getById(userId) ?: throw userNotFound(userId)

    private fun userNotFound(userId: Long) = InvalidInputException("User $userId doesn't exist")

    private fun validUsername(username: String): String {
        val name = username.trim()
        if (name.length !in 1..MAX_USERNAME_LENGTH) {
            throw InvalidInputException("Username must be 1–$MAX_USERNAME_LENGTH characters")
        }
        return name
    }

    private fun requireValidPin(pin: String) {
        if (!PinHasher.isValidPin(pin)) throw InvalidInputException("PIN must be exactly 4 digits")
    }

    companion object {
        const val STARTING_BALANCE = 1000L
        const val BAILOUT_AMOUNT = 1000L
        const val BAILOUT_THRESHOLD = 100L
        const val MAX_USERNAME_LENGTH = 20
    }
}
