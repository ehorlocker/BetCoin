# KAN-10: Repository Interfaces Design

## Overview

Define the three repository Kotlin interfaces that serve as the contract between
ViewModels and the data layer: `UserRepository`, `BetRepository`, and
`SettingsRepository`.

**Scope:** Interface definitions only. Method signatures, return types, KDoc.
`Local*Repository` implementations remain as stubs for follow-up tickets.

**Approach:** Thin interfaces. `Flow<T>` for observable queries, `suspend` for
one-shot mutations. No result wrappers or sealed-class error types. PIN hashing
and transaction boundaries are implementation concerns, not interface concerns.

## Files Modified

| File | Change |
|------|--------|
| `data/repository/UserRepository.kt` | Add 11 method signatures with KDoc |
| `data/repository/BetRepository.kt` | Add 17 method signatures with KDoc |
| `data/repository/SettingsRepository.kt` | Add 4 method signatures with KDoc |

No changes to `Local*Repository` stubs, `RepositoryModule`, or DAOs.

## UserRepository (11 methods)

```kotlin
interface UserRepository {
    suspend fun createUser(username: String, pin: String): Long
    suspend fun verifyPin(userId: Long, pin: String): Boolean
    suspend fun getUser(userId: Long): User?
    fun getAllUsers(): Flow<List<User>>
    fun getLeaderboard(): Flow<List<User>>
    suspend fun bailout(userId: Long)
    suspend fun deleteUser(userId: Long)
    suspend fun resetPin(userId: Long, newPin: String)
    suspend fun updateBalance(userId: Long, amount: Long)
    suspend fun setBalance(userId: Long, newBalance: Long)
    suspend fun updateUsername(userId: Long, newUsername: String)
}
```

### Method Details

- **createUser** -- Creates user with hashed PIN and starting balance of 1000.
  Returns the new user's ID.
- **verifyPin** -- Compares `pin` against stored hash. Returns `true` if match.
- **getUser** -- Returns `User?`. Null means not found.
- **getAllUsers** -- Observable stream of all users.
- **getLeaderboard** -- Observable stream of users sorted by balance descending.
- **bailout** -- Adds 1000 to balance, increments `bailoutCount`, adds 1000 to
  `totalDebt`.
- **deleteUser** -- Blocks if user has ACTIVE bets. Snapshots username into
  `deletedUsername` on participants, nulls the FK, then deletes.
- **resetPin** -- Admin operation. Sets new hashed PIN (no old PIN required).
- **updateBalance** -- Adjusts balance by signed delta `amount`.
- **setBalance** -- Admin operation. Sets balance to exact `newBalance` value.
- **updateUsername** -- Admin operation. Renames user. Must remain unique.

## BetRepository (17 methods)

```kotlin
interface BetRepository {
    suspend fun createBet(prompt: String, outcomes: List<String>): Long
    suspend fun addParticipant(betId: Long, userId: Long, outcomeId: Long, wager: Long)
    suspend fun removeParticipant(participantId: Long)
    fun getBetWithDetails(betId: Long): Flow<BetWithDetails>
    fun getActiveBets(): Flow<List<BetWithDetails>>
    fun getAllBets(): Flow<List<BetWithDetails>>
    suspend fun lockBet(betId: Long)
    suspend fun resolveBet(betId: Long, winningOutcomeId: Long)
    suspend fun cancelBet(betId: Long)
    suspend fun forceCancelBet(betId: Long)
    suspend fun reopenBet(betId: Long)
    suspend fun adminResolveBet(betId: Long, winningOutcomeId: Long)
    suspend fun updateBetPrompt(betId: Long, newPrompt: String)
    suspend fun adminRemoveParticipant(betId: Long, participantId: Long)
    suspend fun adminEditWager(participantId: Long, newWager: Long)
    suspend fun deleteBet(betId: Long)
    suspend fun repeatBet(betId: Long): Long
}
```

### Method Details

- **createBet** -- Creates bet with status `PENDING_CONFIRMATION`, inserts
  outcome rows. Returns new bet ID.
- **addParticipant** -- Adds a participant picking `outcomeId` with `wager`.
- **removeParticipant** -- Removes participant before bet is locked.
- **getBetWithDetails** -- Observable stream of a single bet with outcomes and
  participants (including user info).
- **getActiveBets** -- Observable stream of all `ACTIVE` status bets.
- **getAllBets** -- Observable stream of all bets regardless of status.
- **lockBet** -- Transactional: validates participants, deducts wagers from
  balances, sets status to `ACTIVE`.
- **resolveBet** -- Transactional: calculates payouts, distributes winnings,
  updates user stats, sets status to `RESOLVED`.
- **cancelBet** -- Transactional: refunds all wagers, sets status to
  `CANCELLED`.
- **forceCancelBet** -- Same as `cancelBet` but admin-only (no consent needed).
- **reopenBet** -- Transactional: reverses payouts/refunds, clears resolution,
  sets status back to `ACTIVE`.
- **adminResolveBet** -- Same as `resolveBet` but admin-only.
- **updateBetPrompt** -- Admin edits bet prompt text.
- **adminRemoveParticipant** -- Transactional: refunds wager, removes
  participant from ACTIVE bet.
- **adminEditWager** -- Transactional: adjusts wager amount, refunds/deducts
  balance difference.
- **deleteBet** -- Deletes bet, outcomes, participants. Only allowed for
  `RESOLVED` or `CANCELLED` bets.
- **repeatBet** -- Creates new bet with same prompt and outcomes, no
  participants. Returns new bet ID.

## SettingsRepository (4 methods)

```kotlin
interface SettingsRepository {
    suspend fun getAdminPinHash(): String?
    suspend fun setAdminPin(pin: String)
    suspend fun verifyAdminPin(pin: String): Boolean
    suspend fun isFirstLaunch(): Boolean
}
```

### Method Details

- **getAdminPinHash** -- Returns stored admin PIN hash, or `null` if not set.
- **setAdminPin** -- Hashes and stores admin PIN.
- **verifyAdminPin** -- Compares `pin` against stored hash. Returns `true` if
  match.
- **isFirstLaunch** -- Returns `true` if no admin PIN has been set yet.

## Testing

No unit tests for the interfaces themselves (no logic to test). Tests will be
written alongside the `Local*Repository` implementations in follow-up tickets.

## Dependencies

Required imports across the three interfaces:

- `com.betcoin.data.database.entity.User`
- `com.betcoin.data.model.BetWithDetails`
- `kotlinx.coroutines.flow.Flow`

## Spec Reference

App spec sections 6.0--6.3 (Repository Layer).
