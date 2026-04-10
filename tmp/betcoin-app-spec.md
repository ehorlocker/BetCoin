# BetCoin App — Design Specification

> **Status:** Approved  
> **Platform:** Android (Native Kotlin)  
> **Architecture:** MVVM + Jetpack Compose + Room + Hilt  

---

## 1. Overview

BetCoin is a single-device Android app where friends bet imaginary "BetCoin" currency against each other on made-up bets. The app follows a pass-the-phone model — all data is local (Room database), no networking required.

### Core Flow
1. Someone creates a bet with a prompt and 2+ outcomes
2. Each participant picks an outcome, sets a wager, and confirms with their PIN
3. The bet goes live
4. When the real-world event resolves, participants select the winning outcome
5. Winnings are distributed proportionally from the pot

### Starting Balance
Every new user begins with **1,000 BetCoin**.

---

## 2. Architecture

### Stack
| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose |
| Navigation | Navigation Compose |
| State Management | StateFlow in ViewModels |
| DI | Dagger-Hilt |
| Database | Room |
| Async | Kotlin Coroutines + Flow |

### Reactive Data Flow
```
Room DAO (Flow) → Repository → ViewModel (StateFlow via .stateIn()) → Compose UI
```

### DI Modules
- **@DatabaseModule** — provides Room database instance + all DAOs
- **@RepositoryModule** — provides repository singletons

### ViewModel Pattern
- One ViewModel per screen (annotated `@HiltViewModel`)
- Room `Flow` queries collected via `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialValue)`
- One-shot actions (create, update, delete) via `viewModelScope.launch { ... }`
- Sealed UI state classes for loading/success/error states

---

## 3. Data Model

All currency values stored as `Long` (integer BetCoin) to avoid floating-point rounding.

### 3.1 User
| Column | Type | Constraints |
|--------|------|-------------|
| id | Long | PK, auto-generate |
| username | String | UNIQUE, NOT NULL |
| pinHash | String | NOT NULL |
| balance | Long | NOT NULL, default 1000 |
| totalWins | Int | NOT NULL, default 0 |
| totalLosses | Int | NOT NULL, default 0 |
| totalEarnings | Long | NOT NULL, default 0 |
| totalLost | Long | NOT NULL, default 0 |
| bailoutCount | Int | NOT NULL, default 0 |
| totalDebt | Long | NOT NULL, default 0 |
| createdAt | Long | NOT NULL (epoch millis) |

### 3.2 Bet
| Column | Type | Constraints |
|--------|------|-------------|
| id | Long | PK, auto-generate |
| prompt | String | NOT NULL |
| status | BetStatus | NOT NULL (enum: PENDING_CONFIRMATION, ACTIVE, RESOLVED, CANCELLED) |
| winningOutcomeId | Long? | FK → BetOutcome.id, NULL until resolved |
| createdAt | Long | NOT NULL (epoch millis) |
| resolvedAt | Long? | NULL until resolved/cancelled |

### 3.3 BetOutcome
| Column | Type | Constraints |
|--------|------|-------------|
| id | Long | PK, auto-generate |
| betId | Long | FK → Bet.id, NOT NULL |
| outcomeText | String | NOT NULL |

### 3.4 BetParticipant
| Column | Type | Constraints |
|--------|------|-------------|
| id | Long | PK, auto-generate |
| betId | Long | FK → Bet.id, NOT NULL |
| userId | Long? | FK → User.id, nullable (null after user deletion) |
| outcomeId | Long | FK → BetOutcome.id, NOT NULL |
| wagerAmount | Long | NOT NULL |
| payout | Long? | NULL until resolved |
| deletedUsername | String? | Snapshot of username, set when user is deleted |

### 3.5 AppSettings
| Column | Type | Constraints |
|--------|------|-------------|
| id | Int | PK, always 1 (single-row table) |
| adminPinHash | String | NOT NULL |

### Data Classes (non-entity)
- **BetWithDetails** — `bet: Bet`, `outcomes: List<BetOutcome>`, `participants: List<ParticipantWithUser>`
- **ParticipantWithUser** — `participant: BetParticipant`, `user: User?`, `deletedUsername: String?`, `outcome: BetOutcome`

---

## 4. Screen Flow & Navigation

### 4.1 Navigation Graph
```
App Launch ── Check for PENDING bets?
               ├── YES → Recovery Screen ──┬── Return to Bet → New Bet Step B/C
               │                           └── Discard Bet → Home
               └── NO → First Launch / Home

Home ──┬── New Bet ── Step A ── Step B ── Step C (Review & Lock)
       ├── Leaderboard ── Player Detail
       ├── Bet History ── Bet Detail ──┬── Resolve Flow
       │                               ├── Cancel Flow
       │                               ├── Reopen Flow (RESOLVED/CANCELLED → ACTIVE)
       │                               └── Repeat Bet → New Bet Step B (pre-filled)
       └── Manage Players ── Add Player
                           ├── Player Detail ── Bailout
                           └── Admin Panel (PIN-gated) ── Delete/Reset/PIN Reset
```

### 4.2 Screen Descriptions

#### Home Screen
- Hub with navigation buttons: **New Bet**, **Leaderboard**, **Bet History**, **Manage Players**
- May show summary stats (active bet count, etc.)
- **Dev flavor indicator**: When running the `dev` build flavor, display a persistent banner/badge on the Home Screen (e.g., "DEV MODE" in a warning color) so it's immediately obvious this is the test version. Check `BuildConfig.FLAVOR == "dev"` to toggle visibility.

#### Manage Players
- Lists all registered users
- **Add Player** button → navigates to player creation form
- Tap a player → Player Detail screen
- Gear icon → Admin Panel (requires master admin PIN)

#### New Bet Flow (3 steps)

**Step A — Create Bet**
- Text field for bet prompt (e.g., "Who wins the next game of Smash?")
- Dynamic list to add 2+ outcomes (e.g., "Jay", "Austin", "Mike")
- Must have at least 2 outcomes to proceed

**Step B — Add Participants**
- For each participant:
  - Select user from dropdown
  - Pick an outcome
  - Enter wager amount (cannot exceed user's current balance)
  - Enter PIN to confirm
- Participant list shows all added participants with an **X button** to remove before locking
- All participant data is local ViewModel state until Step C
- Minimum 2 participants required

**Step C — Review & Lock**
- Summary: bet prompt, outcomes, all participants with their picks and wagers
- **Unanimous pick check**: if all participants picked the same outcome, show error and block confirmation
- Each participant re-enters their PIN to confirm
- On confirmation: wagers deducted from balances atomically, bet status → ACTIVE

#### Active Bets (accessible from Bet History filtered to Active)
- List of all ACTIVE bets
- Tap → Bet Detail with Resolve and Cancel actions

#### Bet Detail
Accessed by tapping any bet in Bet History (or Active Bets). Displays the full breakdown of a bet:

- **Header**: Bet prompt (name), status badge (ACTIVE / RESOLVED / CANCELLED), date created
- **Outcomes Section**: List of all possible outcomes. If resolved, the winning outcome is highlighted
- **Participants Table**: For each participant:
  - Username
  - Outcome they picked
  - Wager amount
  - Result indicator: WIN / LOSS / PENDING (based on bet status)
  - Payout amount (if resolved; shows net gain/loss)
- **Pot Summary**: Total pot size, number of participants
- **Resolution Info** (if RESOLVED): Winning outcome name, resolved date
- **Actions** (if ACTIVE): **Resolve** and **Cancel** buttons
- **Actions** (if RESOLVED or CANCELLED): **Reopen Bet** and **Repeat Bet** buttons
- **Cancelled Info** (if CANCELLED): Shows "Cancelled — all wagers refunded"

#### Resolve Flow
- Select winning outcome from the bet's outcomes
- Each participant enters PIN to confirm resolution
- Payouts calculated and distributed, bet status → RESOLVED

#### Cancel Flow
- Any participant can propose cancellation
- Requires **all participants** to enter their PIN (unanimous consent)
- All wagers refunded, bet status → CANCELLED

#### Reopen Flow
- Available on **RESOLVED** or **CANCELLED** bets from the Bet Detail screen
- Requires **all participants** to enter their PIN (unanimous consent)
- If reopening a **RESOLVED** bet:
  - All payouts are reversed (winnings deducted from winners, wagers returned to losers)
  - W/L stats are reverted (winners lose a win, losers lose a loss, earnings/losses adjusted)
  - `winningOutcomeId` is cleared, `resolvedAt` is cleared
  - Bet status → ACTIVE
- If reopening a **CANCELLED** bet:
  - All wagers are re-deducted from participants' balances
  - Each participant must have sufficient balance to cover their original wager
  - If any participant can't cover their wager, the reopen is blocked with an error
  - Bet status → ACTIVE
- Participants, outcomes, and wagers remain the same as the original bet

#### Repeat Bet
- Available on any bet (any status) from the Bet Detail screen
- Creates a **new bet** pre-filled with:
  - Same bet prompt
  - Same outcomes
  - No participants (fresh start)
- Navigates to New Bet flow at **Step B** (Add Participants) with prompt and outcomes already set
- The original bet is unaffected

#### Leaderboard
- Table sorted by balance (descending)
- Columns: Rank, Username, Balance, W-L, Earnings, Losses, Bailouts, Debt
- Tap a row → Player Detail

#### Bet History
- Lists all bets with filter tabs: **All / Active / Resolved / Cancelled**
- Each entry shows: prompt, status, date, participant count
- Tap → Bet Detail

#### Player Detail
- Shows all user stats
- **Bailout** button (visible only when balance < 100)

#### Admin Panel (PIN-gated)
- Requires master admin PIN to access
- **Design principle:** The admin should be able to fix any data issue without ever needing to wipe app data.
- Actions:

  **Player Management:**
  - **Delete Player** — blocked if player has ACTIVE bets
  - **Reset Player Balance** — force bailout (adds 1000, increments bailout count + debt)
  - **Set Player Balance** — set any user's balance to an arbitrary value (for fixing corruption or manual adjustments)
  - **Edit Username** — rename a user (must still be unique)
  - **Reset Player PIN** — admin sets a new PIN directly (player doesn't need old PIN)

  **Bet Management:**
  - **Force Cancel Bet** — cancel any ACTIVE bet, refunding all wagers. No participant consent required.
  - **Admin Resolve Bet** — admin picks the winning outcome on any ACTIVE bet without participant PINs. Payouts calculated and distributed normally.
  - **Edit Bet Prompt** — fix typos or rename any bet (any status)
  - **Remove Participant from ACTIVE Bet** — refunds that participant's wager, removes them from the bet. Blocked if it would leave fewer than 2 participants (force cancel instead).
  - **Edit Participant Wager** — change a participant's wager amount on an ACTIVE bet. Adjusts user balance accordingly (refund difference or deduct additional).
  - **Delete Bet** — permanently remove a bet and all associated outcomes/participants from history. Only allowed on RESOLVED or CANCELLED bets.

  **App Settings:**
  - **Change Admin PIN** — change the master admin PIN

---

## 5. Business Logic

### 5.1 Bet Lifecycle
```
PENDING_CONFIRMATION → ACTIVE → RESOLVED
                              → CANCELLED

RESOLVED  → ACTIVE  (via Reopen — unanimous PIN consent, payouts reversed)
CANCELLED → ACTIVE  (via Reopen — unanimous PIN consent, wagers re-deducted)
```
- **PENDING_CONFIRMATION**: Bet created, participants being added (Step B)
- **ACTIVE**: All participants confirmed, wagers deducted
- **RESOLVED**: Winning outcome selected, payouts distributed
- **CANCELLED**: Unanimously cancelled, wagers refunded

### 5.2 Unanimous Pick Prevention
When locking a bet (Step C), if **all participants** chose the same outcome:
- Block the confirmation with an error message
- Bet does NOT go ACTIVE
- No wagers are deducted
- Users must go back and change picks

### 5.3 Payout Calculation
```
totalPot = sum of all participants' wagerAmount
winningWagers = sum of wagerAmount for participants who picked the winning outcome

For each winner:
  payout = (theirWager / winningWagers) * totalPot
```
- Integer division — rounding remainder goes to the winner with the largest wager
- Tiebreaker for rounding remainder: first participant added to the bet

### 5.4 Bailout System
- Available when a user's balance is **less than 100**
- Adds **1,000 BetCoin** to balance
- Increments `bailoutCount` by 1
- Adds 1,000 to `totalDebt`
- Bailout count and total debt are visible on the leaderboard (shame stats)

### 5.5 PIN System
- **4-digit numeric PIN**
- Stored as hash (bcrypt or Argon2)
- No lockout on failed attempts — just displays "Wrong PIN" error
- Required at:
  - Participant joining a bet (Step B)
  - Bet confirmation (Step C — each participant re-enters)
  - Outcome resolution (each participant confirms)
  - Cancellation (each participant confirms)
- **PIN entry UI**: reusable modal dialog composable (not a navigation destination)

### 5.6 Master Admin PIN
- Set on **first app launch** (onboarding prompt)
- Stored hashed in `AppSettings` table (single-row, id=1)
- Required to access the Admin Panel
- Can be changed from within the Admin Panel (requires current admin PIN)

### 5.7 User Deletion
- **Blocked** if user has any ACTIVE bets
- For RESOLVED/CANCELLED bets:
  - `BetParticipant` rows are preserved
  - `deletedUsername` field is populated with the user's username (snapshot)
  - `userId` FK is set to `null`
- This preserves bet history integrity while removing the user account

### 5.8 Win/Loss Tracking
- On bet resolution:
  - Winners: `totalWins += 1`, `totalEarnings += payout - wager` (net profit)
  - Losers: `totalLosses += 1`, `totalLost += wagerAmount`

---

## 6. Repository Layer

### 6.0 Abstraction Strategy

To make future migration to an online database (e.g., Firebase, Supabase) straightforward, repositories are defined as **interfaces**. The current implementation is a `Local` variant backed by Room DAOs.

```
ViewModel → Repository Interface → LocalRepository (Room DAOs)
                                 → RemoteRepository (future swap)
```

- **Each repository is a Kotlin interface** defining the contract (method signatures, return types).
- **Each `Local*Repository` class** implements that interface using Room DAOs.
- **Hilt's `@Binds`** wires the interface to the local implementation.
- **To go online later:** create a `Remote*Repository` implementing the same interface, change the Hilt binding. ViewModels and UI remain untouched.

**Return types convention:**
- Queries returning live data use `Flow<T>` (reactive observation).
- One-shot mutations return `Unit` or a result type (e.g., `Long` for new IDs).
- All suspend functions where Room would require coroutine scope.

### 6.1 UserRepository (interface)
| Method | Description |
|--------|-------------|
| `createUser(username, pin)` | Create user with hashed PIN, starting balance 1000 |
| `verifyPin(userId, pin)` | Verify PIN against stored hash, return Boolean |
| `getUser(userId)` | Get user by ID |
| `getAllUsers()` | Flow of all users |
| `getLeaderboard()` | Flow of users sorted by balance desc |
| `bailout(userId)` | Add 1000, increment bailoutCount, add 1000 to totalDebt |
| `deleteUser(userId)` | Cascade logic: block if ACTIVE bets, snapshot username, null FK |
| `resetPin(userId, newPin)` | Admin resets PIN (no old PIN needed) |
| `updateBalance(userId, amount)` | Adjust balance by delta |
| `setBalance(userId, newBalance)` | Admin sets balance to arbitrary value |
| `updateUsername(userId, newUsername)` | Admin renames user (must remain unique) |

### 6.2 BetRepository (interface)
| Method | Description |
|--------|-------------|
| `createBet(prompt, outcomes)` | Create bet + outcomes, status PENDING_CONFIRMATION |
| `addParticipant(betId, userId, outcomeId, wager)` | Add participant to bet |
| `removeParticipant(participantId)` | Remove participant before lock |
| `getBetWithDetails(betId)` | Flow of BetWithDetails |
| `getActiveBets()` | Flow of ACTIVE bets |
| `getAllBets()` | Flow of all bets |
| `lockBet(betId)` | @Transaction: validate, deduct wagers, set ACTIVE |
| `resolveBet(betId, winningOutcomeId)` | @Transaction: calculate payouts, distribute, update stats, set RESOLVED |
| `cancelBet(betId)` | @Transaction: refund all wagers, set CANCELLED |
| `forceCancelBet(betId)` | @Transaction: same as cancelBet but callable without participant consent (admin-only) |
| `adminResolveBet(betId, winningOutcomeId)` | @Transaction: same as resolveBet but without participant PINs (admin-only) |
| `updateBetPrompt(betId, newPrompt)` | Admin edits bet prompt text |
| `adminRemoveParticipant(betId, participantId)` | @Transaction: refund participant's wager, remove from ACTIVE bet. Blocked if < 2 would remain. |
| `adminEditWager(participantId, newWager)` | @Transaction: adjust wager on ACTIVE bet, refund/deduct balance difference |
| `deleteBet(betId)` | Delete bet + outcomes + participants. Only RESOLVED or CANCELLED bets. |
| `reopenBet(betId)` | @Transaction: reverse payouts/refunds, clear resolution, set ACTIVE. Requires all participant PINs verified beforehand. |
| `repeatBet(betId)` | Creates new bet with same prompt + outcomes, status PENDING_CONFIRMATION, no participants. Returns new betId. |

### 6.3 SettingsRepository (interface)
| Method | Description |
|--------|-------------|
| `getAdminPinHash()` | Get stored admin PIN hash |
| `setAdminPin(pin)` | Set/update admin PIN (hashed) |
| `verifyAdminPin(pin)` | Verify admin PIN against stored hash |
| `isFirstLaunch()` | Check if admin PIN has been set |

---

## 7. Room Transaction Boundaries

These operations must be atomic (`@Transaction`):

| Operation | What's Atomic |
|-----------|---------------|
| `lockBet` | Validate unanimous check → deduct all wagers → set status ACTIVE |
| `resolveBet` | Calculate payouts → credit winners → update W/L stats → set status RESOLVED |
| `cancelBet` | Refund all wagers → set status CANCELLED |
| `forceCancelBet` | Same as cancelBet — admin override, no participant PINs required |
| `adminResolveBet` | Same as resolveBet — admin override, no participant PINs required |
| `adminRemoveParticipant` | Refund participant wager → remove participant row → validate ≥ 2 remain |
| `adminEditWager` | Calculate difference → refund/deduct from user balance → update wager amount |
| `deleteBet` | Delete all BetParticipant rows → delete all BetOutcome rows → delete Bet row |
| `reopenBet` (from RESOLVED) | Deduct payouts from winners → return wagers to losers → revert W/L stats → clear winningOutcomeId/resolvedAt → set ACTIVE |
| `reopenBet` (from CANCELLED) | Re-deduct all wagers from participants → set ACTIVE |
| `deleteUser` | Snapshot username → null out FK → delete user row |

---

## 8. ViewModels

| ViewModel | Screen | Key State |
|-----------|--------|-----------|
| HomeViewModel | Home Screen | Active bet count, user count |
| ManagePlayersViewModel | Manage Players | User list (Flow) |
| PlayerDetailViewModel | Player Detail | User stats, bailout eligibility |
| AdminViewModel | Admin Panel | User list, admin actions |
| CreateBetViewModel | New Bet (Steps A-C) | Prompt, outcomes, mutable participants list (add/remove) |
| BetDetailViewModel | Bet Detail | BetWithDetails (Flow), resolve/cancel actions |
| LeaderboardViewModel | Leaderboard | Sorted user list (Flow) |
| BetHistoryViewModel | Bet History | All bets (Flow), filter state |

---

## 9. Hilt Dependency Injection

### Module Structure
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): BetCoinDatabase
    
    @Provides fun provideUserDao(db: BetCoinDatabase): UserDao
    @Provides fun provideBetDao(db: BetCoinDatabase): BetDao
    @Provides fun provideBetOutcomeDao(db: BetCoinDatabase): BetOutcomeDao
    @Provides fun provideBetParticipantDao(db: BetCoinDatabase): BetParticipantDao
    @Provides fun provideAppSettingsDao(db: BetCoinDatabase): AppSettingsDao
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindUserRepository(impl: LocalUserRepository): UserRepository
    
    @Binds @Singleton
    abstract fun bindBetRepository(impl: LocalBetRepository): BetRepository
    
    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: LocalSettingsRepository): SettingsRepository
}
```

**Migration path:** To swap to an online database, create `RemoteUserRepository`, `RemoteBetRepository`, etc. implementing the same interfaces, then change the `@Binds` targets in `RepositoryModule`. No ViewModel or UI changes needed.

---

## 10. Edge Cases & Validation

| Scenario | Behavior |
|----------|----------|
| Wager exceeds balance | Block — show error, cannot exceed current balance |
| All participants pick same outcome | Block at lock — error message, no wagers deducted |
| User tries to bailout with balance >= 100 | Block — bailout only available when balance < 100 |
| Delete user with ACTIVE bets | Block — must resolve/cancel bets first |
| Same user tries to join bet twice | Block — one participation per user per bet |
| Bet with < 2 participants at lock | Block — minimum 2 participants required |
| Bet with < 2 outcomes | Block — minimum 2 outcomes required |
| Rounding remainder in payouts | Goes to largest-wager winner (tiebreaker: first added) |
| Wrong PIN entered | "Wrong PIN" message, no lockout, can retry |
| App force-closed during bet creation | On relaunch, show Recovery Screen with only option to return to the incomplete bet (see Section 11) |
| Admin sets negative balance | Allow — balance is a Long, admin has full control |
| Admin renames to existing username | Block — username must remain unique |
| Admin removes participant leaving < 2 | Block — must force cancel the bet instead |
| Admin edits wager, user can't cover increase | Block — user must have sufficient balance for the additional amount |
| Admin deletes ACTIVE bet | Block — must cancel or resolve first, then delete |
| Admin edits wager on RESOLVED bet | Block — wager edits only on ACTIVE bets |
| Reopen cancelled bet, participant can't cover wager | Block — all participants must have sufficient balance |
| Reopen resolved bet, winner spent their winnings | Block — winner must have enough balance to return payouts |
| Reopen bet with deleted participant | Block — all original participants must still exist |
| Repeat bet | Creates new independent bet, original unaffected |

---

## 11. Interrupted Bet Recovery

If the app is force-closed (or crashes) while a bet is in PENDING_CONFIRMATION status, there may be an incomplete bet saved in Room with partial participant data.

### On App Launch
1. Query Room for any bets with status = `PENDING_CONFIRMATION`
2. If **one or more found**: show a **Recovery Screen** instead of the normal Home Screen
3. The Recovery Screen displays:
   - Message: "You have an unfinished bet"
   - The bet prompt
   - Number of participants added so far
   - **"Return to Bet"** button — navigates directly to the New Bet flow (Step B or Step C depending on state)
   - **"Discard Bet"** button — deletes the PENDING_CONFIRMATION bet and all its participants/outcomes, then navigates to Home
4. No other navigation is available from this screen — the user must either resume or discard
5. If **no PENDING_CONFIRMATION bets found**: launch normally (First Launch or Home Screen)

### Why This Matters
- Participants in Step B have already entered PINs to join — their intent is recorded
- No wagers have been deducted yet (that happens at lock in Step C)
- Discarding is safe — no money has moved
- But abandoning silently would lose the bet setup work

---

## 12. First Launch Flow

1. App detects no admin PIN set (`isFirstLaunch()` returns true)
2. Prompt user to create a master admin PIN (4-digit)
3. Store hashed PIN in `AppSettings` table
4. Navigate to Home Screen
5. User creates their first player(s) via Manage Players

---

## 13. Project Structure (Planned)

```
app/src/main/java/com/betcoin/
├── di/
│   ├── DatabaseModule.kt
│   └── RepositoryModule.kt
├── data/
│   ├── database/
│   │   ├── BetCoinDatabase.kt
│   │   ├── dao/
│   │   │   ├── UserDao.kt
│   │   │   ├── BetDao.kt
│   │   │   ├── BetOutcomeDao.kt
│   │   │   ├── BetParticipantDao.kt
│   │   │   └── AppSettingsDao.kt
│   │   └── entity/
│   │       ├── User.kt
│   │       ├── Bet.kt
│   │       ├── BetOutcome.kt
│   │       ├── BetParticipant.kt
│   │       └── AppSettings.kt
│   ├── model/
│   │   ├── BetStatus.kt
│   │   ├── BetWithDetails.kt
│   │   └── ParticipantWithUser.kt
│   └── repository/
│       ├── UserRepository.kt          # Interface
│       ├── BetRepository.kt           # Interface
│       ├── SettingsRepository.kt      # Interface
│       └── local/
│           ├── LocalUserRepository.kt      # Room implementation
│           ├── LocalBetRepository.kt       # Room implementation
│           └── LocalSettingsRepository.kt  # Room implementation
├── ui/
│   ├── theme/
│   │   └── Theme.kt
│   ├── components/
│   │   └── PinEntryDialog.kt
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   └── HomeViewModel.kt
│   ├── players/
│   │   ├── ManagePlayersScreen.kt
│   │   ├── ManagePlayersViewModel.kt
│   │   ├── PlayerDetailScreen.kt
│   │   ├── PlayerDetailViewModel.kt
│   │   ├── AdminScreen.kt
│   │   └── AdminViewModel.kt
│   ├── bet/
│   │   ├── CreateBetScreen.kt
│   │   ├── CreateBetViewModel.kt
│   │   ├── BetDetailScreen.kt
│   │   └── BetDetailViewModel.kt
│   ├── leaderboard/
│   │   ├── LeaderboardScreen.kt
│   │   └── LeaderboardViewModel.kt
│   └── history/
│       ├── BetHistoryScreen.kt
│       └── BetHistoryViewModel.kt
├── navigation/
│   └── NavGraph.kt
└── BetCoinApp.kt (Application class, @HiltAndroidApp)
```

---

## 14. Build Flavors (prod / dev)

Two product flavors ensure testing never touches the production database.

### Gradle Configuration
```kotlin
android {
    flavorDimensions += "environment"
    productFlavors {
        create("prod") {
            dimension = "environment"
            applicationIdSuffix = ""     // com.betcoin
            resValue("string", "app_name", "BetCoin")
        }
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev" // com.betcoin.dev
            resValue("string", "app_name", "BetCoin DEV")
        }
    }
}
```

### How It Works
- **Different `applicationId`** — both flavors install side-by-side on the same device
- **Different database name** — `DatabaseModule` uses `BuildConfig.FLAVOR` to name the Room database:
  - `prod` → `"betcoin.db"`
  - `dev`  → `"betcoin_dev.db"`
- **Same DAOs, same schema** — the flavors share all code; only the database file differs
- **Visual distinction** — dev flavor gets `"BetCoin DEV"` as the app name so you can tell them apart in the launcher
- **In-app indicator** — Home Screen shows a persistent "DEV MODE" banner when `BuildConfig.FLAVOR == "dev"`, so you always know which version you're using

### DatabaseModule Change
```kotlin
@Provides @Singleton
fun provideDatabase(@ApplicationContext ctx: Context): BetCoinDatabase {
    val dbName = if (BuildConfig.FLAVOR == "dev") "betcoin_dev.db" else "betcoin.db"
    return Room.databaseBuilder(ctx, BetCoinDatabase::class.java, dbName).build()
}
```

### Usage
- **`./gradlew installProdDebug`** — install production flavor (debug build)
- **`./gradlew installDevDebug`** — install dev/test flavor (debug build)
- Both can be on the device at the same time with completely isolated data
