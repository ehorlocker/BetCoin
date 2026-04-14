# KAN-10: Repository Interfaces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Define method signatures with KDoc on the three repository interfaces (`UserRepository`, `BetRepository`, `SettingsRepository`) that form the contract between ViewModels and the data layer.

**Architecture:** Thin interfaces using `Flow<T>` for observable queries and `suspend` for one-shot mutations. No result wrappers. PIN hashing and transaction boundaries are implementation concerns. The three existing empty interface files get populated with method signatures and documentation.

**Tech Stack:** Kotlin, Room (`Flow`), Kotlin Coroutines (`suspend`)

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `app/src/main/java/com/betcoin/data/repository/UserRepository.kt` | Modify | 11 methods: user CRUD, PIN, bailout, balance |
| `app/src/main/java/com/betcoin/data/repository/BetRepository.kt` | Modify | 17 methods: bet lifecycle, queries, admin ops |
| `app/src/main/java/com/betcoin/data/repository/SettingsRepository.kt` | Modify | 4 methods: admin PIN, first-launch detection |

No test files — interfaces contain no logic. Tests will accompany `Local*Repository` implementations.

---

### Task 1: UserRepository interface (11 methods)

**Files:**
- Modify: `app/src/main/java/com/betcoin/data/repository/UserRepository.kt`

- [ ] **Step 1: Replace the empty interface with the full definition**

```kotlin
package com.betcoin.data.repository

import com.betcoin.data.database.entity.User
import kotlinx.coroutines.flow.Flow

/**
 * Contract for user management operations.
 *
 * Implementations handle player creation, PIN verification, bailouts,
 * deletion, and balance management.
 */
interface UserRepository {

    /**
     * Creates a new user with the given [username] and [pin].
     *
     * The implementation hashes the PIN before storage. The new user
     * receives a starting balance of 1000.
     *
     * @return the new user's row ID.
     */
    suspend fun createUser(username: String, pin: String): Long

    /**
     * Verifies [pin] against the stored hash for [userId].
     *
     * @return `true` if the PIN matches.
     */
    suspend fun verifyPin(userId: Long, pin: String): Boolean

    /**
     * Retrieves a single user by [userId].
     *
     * @return the [User], or `null` if not found.
     */
    suspend fun getUser(userId: Long): User?

    /**
     * Observes all users.
     */
    fun getAllUsers(): Flow<List<User>>

    /**
     * Observes all users sorted by balance descending.
     */
    fun getLeaderboard(): Flow<List<User>>

    /**
     * Grants the user a bailout: adds 1000 to balance, increments
     * [User.bailoutCount], and adds 1000 to [User.totalDebt].
     */
    suspend fun bailout(userId: Long)

    /**
     * Deletes a user.
     *
     * Blocks if the user is a participant in any ACTIVE bet. Before
     * deletion, snapshots the username into
     * [BetParticipant.deletedUsername][com.betcoin.data.database.entity.BetParticipant.deletedUsername]
     * and nulls the foreign key on all related participants.
     */
    suspend fun deleteUser(userId: Long)

    /**
     * Resets the user's PIN to [newPin] (admin operation — no old PIN required).
     * The implementation hashes the new PIN before storage.
     */
    suspend fun resetPin(userId: Long, newPin: String)

    /**
     * Adjusts the user's balance by signed delta [amount].
     */
    suspend fun updateBalance(userId: Long, amount: Long)

    /**
     * Sets the user's balance to the exact [newBalance] value (admin operation).
     */
    suspend fun setBalance(userId: Long, newBalance: Long)

    /**
     * Renames the user (admin operation). [newUsername] must be unique.
     */
    suspend fun updateUsername(userId: Long, newUsername: String)
}
```

- [ ] **Step 2: Verify the project compiles**

Run: `./gradlew compileDevDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

The `LocalUserRepository` stub will fail to compile because it no longer satisfies the interface. We need to temporarily suppress this. Add `override` stubs with `TODO()` bodies:

- [ ] **Step 3: Update LocalUserRepository to compile against the new interface**

Open `app/src/main/java/com/betcoin/data/repository/local/LocalUserRepository.kt` and replace its contents:

```kotlin
package com.betcoin.data.repository.local

import com.betcoin.data.database.entity.User
import com.betcoin.data.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LocalUserRepository @Inject constructor() : UserRepository {
    override suspend fun createUser(username: String, pin: String): Long = TODO()
    override suspend fun verifyPin(userId: Long, pin: String): Boolean = TODO()
    override suspend fun getUser(userId: Long): User? = TODO()
    override fun getAllUsers(): Flow<List<User>> = TODO()
    override fun getLeaderboard(): Flow<List<User>> = TODO()
    override suspend fun bailout(userId: Long): Unit = TODO()
    override suspend fun deleteUser(userId: Long): Unit = TODO()
    override suspend fun resetPin(userId: Long, newPin: String): Unit = TODO()
    override suspend fun updateBalance(userId: Long, amount: Long): Unit = TODO()
    override suspend fun setBalance(userId: Long, newBalance: Long): Unit = TODO()
    override suspend fun updateUsername(userId: Long, newUsername: String): Unit = TODO()
}
```

- [ ] **Step 4: Verify the project compiles**

Run: `./gradlew compileDevDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/betcoin/data/repository/UserRepository.kt \
       app/src/main/java/com/betcoin/data/repository/local/LocalUserRepository.kt
git commit -m "feat: Define UserRepository interface (11 methods)"
```

---

### Task 2: BetRepository interface (17 methods)

**Files:**
- Modify: `app/src/main/java/com/betcoin/data/repository/BetRepository.kt`

- [ ] **Step 1: Replace the empty interface with the full definition**

```kotlin
package com.betcoin.data.repository

import com.betcoin.data.model.BetWithDetails
import kotlinx.coroutines.flow.Flow

/**
 * Contract for bet lifecycle operations.
 *
 * Implementations handle bet creation, locking, resolution, cancellation,
 * admin operations, and history queries.
 */
interface BetRepository {

    /**
     * Creates a new bet with the given [prompt] and [outcomes] (text labels).
     *
     * The bet starts in `PENDING_CONFIRMATION` status. Outcome rows are
     * inserted alongside the bet.
     *
     * @return the new bet's row ID.
     */
    suspend fun createBet(prompt: String, outcomes: List<String>): Long

    /**
     * Adds a participant to [betId] who picks [outcomeId] with [wager].
     */
    suspend fun addParticipant(betId: Long, userId: Long, outcomeId: Long, wager: Long)

    /**
     * Removes a participant before the bet is locked.
     */
    suspend fun removeParticipant(participantId: Long)

    /**
     * Observes a single bet with its outcomes and participants.
     */
    fun getBetWithDetails(betId: Long): Flow<BetWithDetails>

    /**
     * Observes all bets with `ACTIVE` status.
     */
    fun getActiveBets(): Flow<List<BetWithDetails>>

    /**
     * Observes all bets regardless of status.
     */
    fun getAllBets(): Flow<List<BetWithDetails>>

    /**
     * Locks the bet: validates participants, deducts wagers from balances,
     * and transitions status to `ACTIVE`.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun lockBet(betId: Long)

    /**
     * Resolves the bet: calculates payouts, distributes winnings, updates
     * user stats, and transitions status to `RESOLVED`.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun resolveBet(betId: Long, winningOutcomeId: Long)

    /**
     * Cancels the bet: refunds all wagers and transitions status to `CANCELLED`.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun cancelBet(betId: Long)

    /**
     * Admin-only cancel: same as [cancelBet] but requires no participant consent.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun forceCancelBet(betId: Long)

    /**
     * Reopens a resolved or cancelled bet: reverses payouts/refunds, clears
     * resolution data, and transitions status back to `ACTIVE`.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun reopenBet(betId: Long)

    /**
     * Admin-only resolve: same as [resolveBet] but requires no participant PINs.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun adminResolveBet(betId: Long, winningOutcomeId: Long)

    /**
     * Admin operation: edits the bet's prompt text.
     */
    suspend fun updateBetPrompt(betId: Long, newPrompt: String)

    /**
     * Admin operation: removes a participant from an ACTIVE bet and refunds
     * their wager.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun adminRemoveParticipant(betId: Long, participantId: Long)

    /**
     * Admin operation: adjusts a participant's wager to [newWager], refunding
     * or deducting the balance difference.
     *
     * Implementation must run inside a transaction.
     */
    suspend fun adminEditWager(participantId: Long, newWager: Long)

    /**
     * Deletes a bet and its outcomes and participants. Only allowed for
     * `RESOLVED` or `CANCELLED` bets.
     */
    suspend fun deleteBet(betId: Long)

    /**
     * Creates a new bet with the same prompt and outcomes as [betId], but no
     * participants.
     *
     * @return the new bet's row ID.
     */
    suspend fun repeatBet(betId: Long): Long
}
```

- [ ] **Step 2: Update LocalBetRepository to compile against the new interface**

Open `app/src/main/java/com/betcoin/data/repository/local/LocalBetRepository.kt` and replace its contents:

```kotlin
package com.betcoin.data.repository.local

import com.betcoin.data.model.BetWithDetails
import com.betcoin.data.repository.BetRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LocalBetRepository @Inject constructor() : BetRepository {
    override suspend fun createBet(prompt: String, outcomes: List<String>): Long = TODO()
    override suspend fun addParticipant(betId: Long, userId: Long, outcomeId: Long, wager: Long): Unit = TODO()
    override suspend fun removeParticipant(participantId: Long): Unit = TODO()
    override fun getBetWithDetails(betId: Long): Flow<BetWithDetails> = TODO()
    override fun getActiveBets(): Flow<List<BetWithDetails>> = TODO()
    override fun getAllBets(): Flow<List<BetWithDetails>> = TODO()
    override suspend fun lockBet(betId: Long): Unit = TODO()
    override suspend fun resolveBet(betId: Long, winningOutcomeId: Long): Unit = TODO()
    override suspend fun cancelBet(betId: Long): Unit = TODO()
    override suspend fun forceCancelBet(betId: Long): Unit = TODO()
    override suspend fun reopenBet(betId: Long): Unit = TODO()
    override suspend fun adminResolveBet(betId: Long, winningOutcomeId: Long): Unit = TODO()
    override suspend fun updateBetPrompt(betId: Long, newPrompt: String): Unit = TODO()
    override suspend fun adminRemoveParticipant(betId: Long, participantId: Long): Unit = TODO()
    override suspend fun adminEditWager(participantId: Long, newWager: Long): Unit = TODO()
    override suspend fun deleteBet(betId: Long): Unit = TODO()
    override suspend fun repeatBet(betId: Long): Long = TODO()
}
```

- [ ] **Step 3: Verify the project compiles**

Run: `./gradlew compileDevDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/betcoin/data/repository/BetRepository.kt \
       app/src/main/java/com/betcoin/data/repository/local/LocalBetRepository.kt
git commit -m "feat: Define BetRepository interface (17 methods)"
```

---

### Task 3: SettingsRepository interface (4 methods)

**Files:**
- Modify: `app/src/main/java/com/betcoin/data/repository/SettingsRepository.kt`

- [ ] **Step 1: Replace the empty interface with the full definition**

```kotlin
package com.betcoin.data.repository

/**
 * Contract for application settings operations.
 *
 * Implementations handle admin PIN management and first-launch detection.
 */
interface SettingsRepository {

    /**
     * Returns the stored admin PIN hash, or `null` if no PIN has been set.
     */
    suspend fun getAdminPinHash(): String?

    /**
     * Hashes and stores the admin PIN.
     */
    suspend fun setAdminPin(pin: String)

    /**
     * Verifies [pin] against the stored admin PIN hash.
     *
     * @return `true` if the PIN matches.
     */
    suspend fun verifyAdminPin(pin: String): Boolean

    /**
     * Returns `true` if no admin PIN has been set (first launch).
     */
    suspend fun isFirstLaunch(): Boolean
}
```

- [ ] **Step 2: Update LocalSettingsRepository to compile against the new interface**

Open `app/src/main/java/com/betcoin/data/repository/local/LocalSettingsRepository.kt` and replace its contents:

```kotlin
package com.betcoin.data.repository.local

import com.betcoin.data.repository.SettingsRepository
import javax.inject.Inject

class LocalSettingsRepository @Inject constructor() : SettingsRepository {
    override suspend fun getAdminPinHash(): String? = TODO()
    override suspend fun setAdminPin(pin: String): Unit = TODO()
    override suspend fun verifyAdminPin(pin: String): Boolean = TODO()
    override suspend fun isFirstLaunch(): Boolean = TODO()
}
```

- [ ] **Step 3: Verify the project compiles**

Run: `./gradlew compileDevDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Run existing tests to confirm no regressions**

Run: `./gradlew testDevDebugUnitTest 2>&1 | tail -10`
Expected: All existing tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/betcoin/data/repository/SettingsRepository.kt \
       app/src/main/java/com/betcoin/data/repository/local/LocalSettingsRepository.kt
git commit -m "feat: Define SettingsRepository interface (4 methods)"
```
