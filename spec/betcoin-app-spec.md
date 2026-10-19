# BetCoin App — Design Specification

> **Status:** Approved (revised 2026-10-17)  
> **Platform:** Android (Native Kotlin)  
> **Architecture:** MVVM + Jetpack Compose + Room + Hilt  
> **Design system:** [`dark-mode-design.md`](dark-mode-design.md) (Pastel Playroom Dark)  

### Revision Log
- **2026-10-17** — Resolved open questions: Step B participants persisted to Room; exiting New Bet discards the pending bet; resolving to an unpicked outcome refunds everyone; bailout eligibility counts locked wagers; leaderboard ranks by net worth (4 columns); delete/rename moved behind Admin Panel; admin PIN recovery code; profile pictures via an `AvatarStore` abstraction; Settings screen with theme picker; system insets; input limits; destructive migrations until v1.0. Fixed the RESOLVED→ACTIVE reopen reversal (it previously refunded losers, creating coins). The dev build indicator is now one app-wide banner instead of a Home-only one. Added a dev-only Dev Tools screen (seed sample data, wipe database, reset admin PIN) and backup rules (prod backed up, dev excluded). Spelled out why there's no in-app database switch.

---

## 1. Overview

BetCoin is a single-device Android app where friends bet imaginary "BetCoin" currency against each other on made-up bets. The app follows a pass-the-phone model — all data is local (Room database), no networking required.

### Core Flow
1. Someone creates a bet with a prompt and 2+ outcomes
2. Each participant picks an outcome, sets a wager, and confirms with their PIN
3. The bet goes live
4. When the real-world event resolves, participants select the winning outcome
5. Winners split the pot in proportion to their wagers, but nobody wins more than they risked: any unmatched part of the losers' wagers is refunded (§5.3)

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
| PIN hashing | jBCrypt (`org.mindrot:jbcrypt`) |
| Image loading | Coil 3 (`coil-compose`), new dependency for avatars |

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
| username | String | UNIQUE (case-insensitive, `COLLATE NOCASE`), NOT NULL, 1–20 chars, trimmed |
| pinHash | String | NOT NULL |
| balance | Long | NOT NULL, default 1000 |
| totalWins | Int | NOT NULL, default 0 |
| totalLosses | Int | NOT NULL, default 0 |
| totalEarnings | Long | NOT NULL, default 0 |
| totalLost | Long | NOT NULL, default 0 |
| bailoutCount | Int | NOT NULL, default 0 |
| totalDebt | Long | NOT NULL, default 0 |
| avatarRef | String? | NULL until set. Opaque key returned by `AvatarStore` (see §6.4) — not a file path or URI |
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
| payout | Long? | NULL until resolved. Gross amount credited at resolution (§5.3): winners' share of the pot (at most `2 × wagerAmount`); for losers `0`, or their share of the refunded unmatched wagers when the cap applies; `wagerAmount` for everyone when no one picked the winning outcome |
| deletedUsername | String? | Snapshot of username, set when user is deleted |

### 3.5 AppSettings
| Column | Type | Constraints |
|--------|------|-------------|
| id | Int | PK, always 1 (single-row table) |
| adminPinHash | String | NOT NULL |
| recoveryCodeHash | String | NOT NULL. Hash of the admin recovery code (§5.6) |
| themePreference | String | NOT NULL, default `SYSTEM` (enum: SYSTEM, LIGHT, DARK) |

### Data Classes (non-entity)
- **BetWithDetails** — `bet: Bet`, `outcomes: List<BetOutcome>`, `participants: List<ParticipantWithUser>`
- **ParticipantWithUser** — `participant: BetParticipant`, `user: User?`, `deletedUsername: String?`, `outcome: BetOutcome`

### Schema Versioning
- `exportSchema = true`, with schemas checked in under `app/schemas/`.
- **Until the first tagged release (v1.0):** bump the version on every schema change and use `fallbackToDestructiveMigration()`. No real data needs to be kept yet.
- **From v1.0 on:** every schema change ships with a Room `Migration` and a `MigrationTestHelper` test, and the destructive fallback is removed.

---

## 4. Screen Flow & Navigation

### 4.1 Navigation Graph
```
App Launch ── Admin PIN set?
               ├── NO  → Onboarding (admin PIN + recovery code) → Home
               └── YES → PENDING bet exists?
                          ├── YES → Recovery Screen ──┬── Return to Bet → New Bet Step B
                          │                           └── Discard Bet → Home
                          └── NO  → Home

Home ──┬── New Bet ── Step A ── Step B ── Step C (Review & Lock)
       ├── Leaderboard ── Player Detail
       ├── Bet History ── Bet Detail ──┬── Resolve Flow
       │                               ├── Cancel Flow
       │                               ├── Reopen Flow (RESOLVED/CANCELLED → ACTIVE)
       │                               └── Repeat Bet → New Bet Step B (pre-filled)
       ├── Manage Players ── Add Player
       │                   ├── Player Detail ── Bailout / Change Picture
       │                   └── Admin Panel (PIN-gated) ── Player / Bet / App Settings
       └── Settings (gear icon) ──┬── Theme
                                  └── Dev Tools (dev flavor only, §14)
```

### 4.1.1 System Insets
The app runs edge-to-edge (`enableEdgeToEdge()`). Every screen applies `WindowInsets.safeDrawing` (via `Scaffold` content padding or `windowInsetsPadding`) so the status bar, navigation bar and display cutouts never cover interactive elements such as the top-bar Back button. Bottom sheets and dialogs respect `navigationBars` insets.

### 4.2 Screen Descriptions

#### Home Screen
- Hub with navigation buttons: **New Bet**, **Leaderboard**, **Bet History**, **Manage Players**
- Gear icon in the top bar → **Settings**
- Summary stats: active bet count and player count
- **New Bet** is disabled (with a hint) when fewer than 2 players exist
- **Dev flavor indicator**: shown here like on every other screen (see §14 "Dev Build Indicator")

#### Manage Players
- Lists all registered users with their profile picture (or default avatar) and username
- **Add Player** button → player creation form (username + 4-digit PIN entered twice to confirm)
- Tap a player → Player Detail screen
- Gear icon → Admin Panel (requires master admin PIN)
- **No destructive or balance actions here.** Delete and rename live only in the Admin Panel; bailout lives only on Player Detail.

#### New Bet Flow (3 steps)

**Step A — Create Bet**
- Text field for bet prompt (e.g., "Who wins the next game of Smash?")
- Dynamic list to add 2–8 outcomes (e.g., "Jay", "Austin", "Mike")
- Must have at least 2 outcomes to proceed (validation rules in §10)
- On **Next**: `createBet(prompt, outcomes)` writes the Bet (status `PENDING_CONFIRMATION`) and its outcomes to Room. Prompt and outcomes are fixed from then on; to change them, discard and start over.

**Step B — Add Participants**
- For each participant:
  - Select user from dropdown (users already in this bet are excluded)
  - Pick an outcome
  - Enter wager amount (1 up to the user's current balance)
  - Enter PIN to confirm
- When the PIN is accepted, the participant is written to Room right away with `addParticipant`. This is what lets §11 Recovery restore them after a crash. No balance changes yet.
- Participant list shows all added participants with an **X button** that removes them (`removeParticipant`) before locking
- Minimum 2 participants required to continue to Step C

**Step C — Review & Lock**
- Summary: bet prompt, outcomes, all participants with their picks and wagers
- **Unanimous pick check**: if all participants picked the same outcome, show error and block confirmation
- Each participant re-enters their PIN to confirm, one after another in list order
- Wagers are re-checked against current balances when locking
- On confirmation: wagers deducted from balances atomically, bet status → ACTIVE
- **Back** from Step C returns to Step B (participants kept)

**Leaving the flow**
- Back from Step A or B, or the top-bar close button, shows a **"Discard this bet?"** dialog. Confirming deletes the pending bet and its outcomes and participants (`discardPendingBet`). No money has moved, so this is always safe.
- Before the bet row is written (Step A, before Next), leaving needs no confirmation unless text has been entered.
- So there is at most one `PENDING_CONFIRMATION` bet at a time. It survives only a crash or force-close, which is what §11 handles.

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
  - Payout amount (if resolved): shows net gain/loss (`payout − wagerAmount`), e.g. `+150` / `−200` / `−100` for a loser with a partial refund / `±0` for a push
- **Pot Summary**: Total pot size, number of participants
- **Resolution Info** (if RESOLVED): Winning outcome name, resolved date. If no one picked the winning outcome, also shows "No winners — all wagers refunded". If the even-money cap applied (§5.3), also shows "Unmatched wagers refunded"
- **Actions** (if ACTIVE): **Resolve** and **Cancel** buttons
- **Actions** (if RESOLVED or CANCELLED): **Reopen Bet** and **Repeat Bet** buttons
- **Cancelled Info** (if CANCELLED): Shows "Cancelled — all wagers refunded"

#### Unanimous Consent (Resolve, Cancel, Reopen)
These flows all need **every participant** to confirm. The shared sequence is:
1. Show the action summary (e.g. "Resolve: *Jay* wins").
2. Prompt each participant's PIN in list order, showing whose turn it is.
3. A wrong PIN shows "Wrong PIN" and that participant can retry. Dismissing the dialog aborts the whole action with nothing changed. PINs already entered are not saved.
4. Once the last PIN is accepted, the repository transaction runs.

If a participant won't confirm, the fallback is the Admin Panel's **Admin Resolve** or **Force Cancel**. Participants deleted from the app can't confirm, so actions on bets that include them go through the admin.

#### Resolve Flow
- Select winning outcome from the bet's outcomes (any outcome, including ones nobody picked)
- Unanimous consent (above)
- Payouts calculated and distributed per §5.3, bet status → RESOLVED

#### Cancel Flow
- Unanimous consent (above)
- All wagers refunded, bet status → CANCELLED

#### Reopen Flow
- Available on **RESOLVED** or **CANCELLED** bets from the Bet Detail screen
- Unanimous consent (above)
- If reopening a **RESOLVED** bet:
  - Every participant's `payout` is deducted from their balance. Winners give back their share of the pot. Losers give back any refund they got from the cap (§5.3); a loser with `payout = 0` is unchanged. Either way each wager ends up locked in the reopened pot again. In a push, everyone's refunded wager is locked again.
  - Each participant must have balance ≥ their `payout`, otherwise the reopen is blocked
  - W/L stats are reverted using §5.8 in reverse (winners: `totalWins −= 1`, `totalEarnings −= payout − wager`; losers: `totalLosses −= 1`, `totalLost −= wager − payout`; a push changes no stats)
  - `payout` is reset to NULL on every participant; `winningOutcomeId` and `resolvedAt` are cleared
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
- The new bet is a normal `PENDING_CONFIRMATION` bet, so the "Leaving the flow" discard rules apply
- Blocked if another `PENDING_CONFIRMATION` bet exists (only reachable after a crash; Recovery handles that first)
- The original bet is unaffected

#### Leaderboard
- Ranked by **net worth** = `balance − totalDebt`, descending, so bailouts count against you. Ties: higher balance first, then username A–Z.
- Coins locked in ACTIVE bets are not counted. Net worth can be negative.
- Columns (4): **Rank, Username, Net Worth, W-L**. Full stats are on Player Detail.
- Tap a row → Player Detail

#### Bet History
- Lists all bets except `PENDING_CONFIRMATION`, with filter tabs: **All / Active / Resolved / Cancelled**
- Sorted newest first (`createdAt` desc)
- Each entry shows: prompt, status, date, participant count, pot size
- Empty state per tab (e.g. "No active bets")
- Tap → Bet Detail

#### Player Detail
- Shows all user stats: balance, net worth, W-L, total earnings, total lost, bailouts, total debt, coins locked in active bets
- **Profile Picture**: displays the player's profile picture (if set) or a default avatar placeholder
- **Change Profile Picture** button (requires that player's PIN) opens a bottom sheet with these options:
  - **Select from Gallery** — Android Photo Picker (`PickVisualMedia`, no storage permission needed)
  - **Take Photo** — `TakePicture` contract into a `FileProvider` cache file (no `CAMERA` permission declared, so none is requested)
  - **Remove Photo** (shown only when one is set) — clears the profile picture and reverts to the default avatar
- **Bailout** button (requires that player's PIN), shown only when the player is eligible per §5.4

#### Admin Panel (PIN-gated)
- Requires master admin PIN to access, every time it's opened (no remembered session)
- "Forgot admin PIN?" on the PIN dialog → enter the recovery code (§5.6)
- Destructive actions (delete player, delete bet, force cancel) show a confirmation dialog
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
  - **Remove Participant from ACTIVE Bet** — refunds that participant's wager, removes them from the bet. Blocked if it would leave fewer than 2 participants, or if everyone remaining picked the same outcome (force cancel instead).
  - **Edit Participant Wager** — change a participant's wager amount on an ACTIVE bet. Adjusts user balance accordingly (refund difference or deduct additional).
  - **Delete Bet** — permanently remove a bet and all associated outcomes/participants from history. Only allowed on RESOLVED or CANCELLED bets.

  **App Settings:**
  - **Change Admin PIN** — enter the new PIN twice (already authenticated by opening the panel)
  - **Regenerate Recovery Code** — show a new recovery code once; the old one stops working

#### Settings (ungated)
- Reached from the gear icon on Home. No PIN needed, since nothing here touches players or bets.
- **Theme**: System default / Light / Dark. Stored in `AppSettings.themePreference` and applied immediately, without a restart. System default follows `isSystemInDarkTheme()`.
- Light palette: a light counterpart of [`dark-mode-design.md`](dark-mode-design.md), using the same color roles.
- App version, plus the build flavor on dev builds.
- **Dev Tools** row, shown on the dev flavor only (§14 "Dev Tools Screen")

---

## 5. Business Logic

### 5.1 Bet Lifecycle
```
PENDING_CONFIRMATION → ACTIVE → RESOLVED
                              → CANCELLED

RESOLVED  → ACTIVE  (via Reopen — unanimous PIN consent, payouts reversed)
CANCELLED → ACTIVE  (via Reopen — unanimous PIN consent, wagers re-deducted)
```
- **PENDING_CONFIRMATION**: Bet created at the end of Step A, participants being added (Step B). Leaving the flow deletes it (`discardPendingBet`); there's no CANCELLED transition from here.
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
A winner can win at most their own wager (even money). Any part of the losers' wagers that the winners can't claim is refunded to the losers.

```
totalPot      = sum of all participants' wagerAmount
winningWagers = sum of wagerAmount for participants who picked the winning outcome
losingWagers  = totalPot − winningWagers

If losingWagers ≤ winningWagers (uncapped: the winners can absorb the whole pot):
  each winner: payout = theirWager × totalPot / winningWagers    (never more than 2 × theirWager)
  each loser:  payout = 0

Otherwise (capped: the losers put in more than the winners can win):
  each winner: payout = 2 × theirWager
  refundPool   = losingWagers − winningWagers
  each loser:  payout = theirWager × refundPool / losingWagers
```
- Every winner's uncapped share is the same multiple of their wager (`totalPot / winningWagers`), so the cap applies to all winners or to none
- Integer division: use `Long` math, multiplying before dividing
- Rounding remainder (`totalPot − sum of payouts`, always fewer coins than recipients): handed out one coin at a time to the recipients with the largest wagers, so nobody gets more than one extra coin. Recipients are the winners if uncapped, the losers if capped (winners' payouts are exact)
- Tiebreaker for equal wagers: the participant added first (lowest `BetParticipant.id`)
- A loser who gets a partial refund still counts as a loss (§5.8)
- **No one picked the winning outcome** (`winningWagers == 0`): the bet still resolves to that outcome, but it's a **push**. Every participant gets `payout = wagerAmount`, so all wagers are refunded, and no W/L stats change. This check comes before the cap.
- Implement as a pure function (`PayoutCalculator`) so it can be unit-tested directly

**Examples**

| Wagers | Winner | Payouts | Net |
|---|---|---|---|
| A 200 on X, B 100 on Y | B | B 200 (capped), A 100 (refund) | B +100, A −100 |
| A 200 on X, B 100 on Y | A | A 300, B 0 (uncapped) | A +100, B −100 |
| A 100, B 100 on X; C 300 on Y | X | A 200, B 200 (capped), C 100 (refund) | A +100, B +100, C −200 |
| A 300 on X; B 100, C 100 on Y | Y | B 200, C 200 (capped), A 100 (refund) | B +100, C +100, A −200 |
| A 100 on X, B 100 on Y | A | A 200, B 0 (boundary, uncapped) | A +100, B −100 |

### 5.4 Bailout System
- Available when a user's **balance + coins locked in ACTIVE bets is less than 100**. Counting locked wagers stops someone from going all-in on a bet and then taking a bailout while the wager is still in play.
- Requires that player's PIN
- Adds **1,000 BetCoin** to balance
- Increments `bailoutCount` by 1
- Adds 1,000 to `totalDebt`
- Bailout count and total debt are shown on Player Detail (shame stats), and debt pulls down the leaderboard's net worth ranking
- Admin **Reset Player Balance** is a forced bailout that skips the eligibility check

### 5.5 PIN System
- **4-digit numeric PIN**
- Stored as a bcrypt hash (jBCrypt, cost 10). PINs are never stored or logged in plain text, including in fakes used for tests. A 4-digit PIN can be brute-forced offline, so hashing only keeps PINs from being read casually off the database. That's acceptable for a party game.
- PINs don't need to be unique across players
- No lockout on failed attempts — just displays "Wrong PIN" error
- Required at:
  - Participant joining a bet (Step B)
  - Bet confirmation (Step C — each participant re-enters)
  - Outcome resolution (each participant confirms)
  - Cancellation (each participant confirms)
  - Reopen (each participant confirms)
  - Bailout and Change Profile Picture (that player)
- **PIN entry UI**: reusable modal dialog composable (not a navigation destination). It shows whose PIN is being asked for, a masked 4-digit input that auto-submits on the 4th digit, inline "Wrong PIN", and Cancel.
- Hashing runs off the main thread (`Dispatchers.Default`)

### 5.6 Master Admin PIN
- Set on **first app launch** (onboarding prompt)
- Stored hashed in `AppSettings` table (single-row, id=1)
- Required to access the Admin Panel
- Can be changed from within the Admin Panel

#### Recovery Code
- During onboarding, once the admin PIN is set, the app generates a **12-character recovery code**. It's uppercase letters and digits with ambiguous characters left out (no 0/O/1/I), shown as `XXXX-XXXX-XXXX`, using `SecureRandom`.
- It's shown **once**, with a "I've written this down" checkbox that has to be ticked before continuing. Only its bcrypt hash is stored (`AppSettings.recoveryCodeHash`).
- "Forgot admin PIN?" → enter the recovery code (case-insensitive, dashes optional) → set a new admin PIN → a **new** recovery code is generated and shown once. The old code stops working.
- The code can also be regenerated from the Admin Panel.
- If both the PIN and the code are lost, clearing app data is the only way out. That's a documented, accepted risk.

### 5.7 User Deletion
- **Blocked** if user has any ACTIVE bets
- For RESOLVED/CANCELLED bets:
  - `BetParticipant` rows are preserved
  - `deletedUsername` field is populated with the user's username (snapshot)
  - `userId` FK is set to `null`
- The user's avatar is deleted through `AvatarStore.delete(avatarRef)`
- This preserves bet history integrity while removing the user account
- Bets that include a deleted participant can't be reopened (their PIN can't be entered). They can still be repeated, and they can still be viewed, edited and deleted by the admin.

### 5.8 Win/Loss Tracking
- On bet resolution:
  - Winners: `totalWins += 1`, `totalEarnings += payout - wager` (net profit)
  - Losers: `totalLosses += 1`, `totalLost += wagerAmount − payout` (net loss; `payout` is the refund from the cap in §5.3, usually `0`)
  - Push (no one picked the winning outcome): no stats change
- Admin Resolve updates stats the same way. Cancel and Force Cancel never touch stats.
- Reopen reverses exactly what resolution applied (§4.2 Reopen Flow)

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
- Rule violations (insufficient balance, unanimous pick, ACTIVE-bet conflicts, duplicate username, and so on) throw a typed `BetCoinException` subclass (e.g. `InsufficientBalanceException(userId, required, available)`). ViewModels map these to user-facing messages. Nothing is written when a check fails.
- **Nothing outside `data/` touches Room, DAOs, `Context.filesDir`, or file paths directly.** That includes the avatar storage below, so the whole persistence layer can be swapped at once.

### Current Implementation Gap
At this revision, `LocalUserRepository` is an in-memory placeholder. It stores PINs in plain text, `verifyPin` always returns `false`, and it isn't backed by Room. `LocalBetRepository` is entirely `TODO()`. Both must be implemented against the DAOs before any PIN-gated or bet flow can work.

### 6.1 UserRepository (interface)
| Method | Description |
|--------|-------------|
| `createUser(username, pin)` | Create user with hashed PIN, starting balance 1000 |
| `verifyPin(userId, pin)` | Verify PIN against stored hash, return Boolean |
| `getUser(userId)` | Get user by ID |
| `getAllUsers()` | Flow of all users |
| `getLeaderboard()` | Flow of users sorted by net worth (`balance − totalDebt`) desc, then balance desc, then username |
| `getLockedBalance(userId)` | Flow of the sum of the user's wagers in ACTIVE bets |
| `bailout(userId)` | Throws if not eligible (§5.4). Otherwise adds 1000, increments bailoutCount, adds 1000 to totalDebt |
| `forceBailout(userId)` | Admin: same as bailout without the eligibility check |
| `deleteUser(userId)` | Cascade logic: block if ACTIVE bets, snapshot username, null FK, delete avatar |
| `resetPin(userId, newPin)` | Admin resets PIN (no old PIN needed) |
| `updateBalance(userId, amount)` | Adjust balance by delta |
| `setBalance(userId, newBalance)` | Admin sets balance to arbitrary value |
| `updateUsername(userId, newUsername)` | Admin renames user (must remain unique) |
| `updateProfilePicture(userId, imageUri)` | Reads the picked or captured image, saves it through `AvatarStore.save`, stores the returned `avatarRef`, and deletes the previous avatar |
| `clearProfilePicture(userId)` | `AvatarStore.delete(avatarRef)`, then sets `avatarRef` to NULL |

### 6.2 BetRepository (interface)
| Method | Description |
|--------|-------------|
| `createBet(prompt, outcomes)` | Create bet + outcomes, status PENDING_CONFIRMATION |
| `addParticipant(betId, userId, outcomeId, wager)` | Add participant to bet |
| `removeParticipant(participantId)` | Remove participant before lock |
| `getBetWithDetails(betId)` | Flow of BetWithDetails |
| `getActiveBets()` | Flow of ACTIVE bets |
| `getAllBets()` | Flow of all non-pending bets, newest first |
| `getPendingBet()` | Flow of the single `PENDING_CONFIRMATION` bet, or null (used by Recovery) |
| `discardPendingBet(betId)` | Delete a `PENDING_CONFIRMATION` bet + outcomes + participants. Throws if the bet isn't pending. |
| `lockBet(betId)` | @Transaction: validate, deduct wagers, set ACTIVE |
| `resolveBet(betId, winningOutcomeId)` | @Transaction: calculate payouts (`PayoutCalculator`, including the push case), distribute, update stats, set RESOLVED |
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
| `regenerateRecoveryCode()` | Generate a new code, store its hash, return the plain code once for display |
| `resetAdminPinWithRecoveryCode(code, newPin)` | Verify the code; if it matches, set the new PIN and return a freshly generated recovery code. Otherwise throw. |
| `getThemePreference()` | Flow of `ThemePreference` (SYSTEM / LIGHT / DARK) |
| `setThemePreference(pref)` | Persist the theme choice |

### 6.4 AvatarStore (interface)
Keeps image storage behind the same swappable boundary as the repositories, so profile pictures can later move to a database or cloud storage without touching the UI or `User` rows. The UI never sees file paths, only `avatarRef`s and a loadable model.

| Method | Description |
|--------|-------------|
| `save(source: Uri): String` | Decode, downscale to at most 512×512 (keeping the aspect ratio, EXIF orientation applied), encode as JPEG at quality 85, store it, and return an opaque `avatarRef` |
| `delete(avatarRef)` | Remove the stored image. A missing image is fine (no-op). |
| `load(avatarRef): Any?` | Returns a model the image loader can display (Coil `ImageRequest` data, e.g. a `File` or URL), or null if the image is missing. The UI falls back to the default avatar. |

- **`LocalAvatarStore`** (current) stores files in `filesDir/avatars/<uuid>.jpg`, and the `avatarRef` is the file name. Using a UUID rather than the user id means an update never shows a stale cached image.
- **Future:** `RemoteAvatarStore` uploads to cloud storage and returns its key/URL, or a `RoomAvatarStore` keeps the bytes in a blob table. Either is swapped in with `@Binds` in `RepositoryModule`.

---

## 7. Room Transaction Boundaries

These operations must be atomic (`@Transaction`):

| Operation | What's Atomic |
|-----------|---------------|
| `lockBet` | Validate ≥ 2 participants, not unanimous, every wager ≤ current balance → deduct all wagers → set status ACTIVE |
| `resolveBet` | Calculate payouts → credit each participant's `payout` (winners, losers' cap refunds, or everyone in a push) → update W/L stats (none for a push) → set status RESOLVED |
| `discardPendingBet` | Delete participant rows → outcome rows → bet row (PENDING_CONFIRMATION only) |
| `cancelBet` | Refund all wagers → set status CANCELLED |
| `forceCancelBet` | Same as cancelBet — admin override, no participant PINs required |
| `adminResolveBet` | Same as resolveBet — admin override, no participant PINs required |
| `adminRemoveParticipant` | Validate ≥ 2 remain and the rest are not all on one outcome → refund participant wager → remove participant row |
| `adminEditWager` | Calculate difference → refund/deduct from user balance → update wager amount |
| `deleteBet` | Delete all BetParticipant rows → delete all BetOutcome rows → delete Bet row |
| `reopenBet` (from RESOLVED) | Validate every participant still exists and has balance ≥ their `payout` → deduct each participant's `payout` (losers' is their cap refund, or 0) → revert W/L stats → null all payouts → clear winningOutcomeId/resolvedAt → set ACTIVE |
| `reopenBet` (from CANCELLED) | Re-deduct all wagers from participants → set ACTIVE |
| `deleteUser` | Validate no ACTIVE bets → snapshot username → null out FK → delete user row. The avatar file is deleted after the transaction commits. |

---

## 8. ViewModels

| ViewModel | Screen | Key State |
|-----------|--------|-----------|
| OnboardingViewModel | First Launch | Admin PIN entry/confirm, recovery code display |
| RecoveryViewModel | Recovery Screen | Pending bet (prompt, participant count), resume/discard |
| HomeViewModel | Home Screen | Active bet count, user count |
| ManagePlayersViewModel | Manage Players | User list (Flow), add-player form. No delete, rename or bailout. |
| PlayerDetailViewModel | Player Detail | User stats, locked balance, bailout eligibility, avatar actions |
| AdminViewModel | Admin Panel | User list, bet list, admin actions, recovery code |
| SettingsViewModel | Settings | Theme preference |
| NewBetViewModel | New Bet (Steps A-C) | Current step, pending betId, prompt/outcomes form, participants (Flow from Room), add/remove, lock, discard |
| BetDetailViewModel | Bet Detail | BetWithDetails (Flow), resolve/cancel/reopen/repeat actions, PIN consent sequence |
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

    @Binds @Singleton
    abstract fun bindAvatarStore(impl: LocalAvatarStore): AvatarStore
}
```

**Migration path:** To swap to an online database, create `RemoteUserRepository`, `RemoteBetRepository`, etc. implementing the same interfaces, then change the `@Binds` targets in `RepositoryModule`. No ViewModel or UI changes needed.

---

## 10. Edge Cases & Validation

| Scenario | Behavior |
|----------|----------|
| Wager exceeds balance | Block — show error, cannot exceed current balance (checked at Step B and again at lock) |
| Wager is 0, negative, or not a whole number | Block — wager must be an integer ≥ 1 |
| User's balance is ≤ 0 (e.g. admin set it negative) | Can't join bets until bailed out or adjusted |
| All participants pick same outcome | Block at lock — error message, no wagers deducted |
| Resolved to an outcome nobody picked | Push — everyone refunded (`payout = wager`), no W/L stats change |
| User tries to bailout when balance + locked wagers ≥ 100 | Block — Bailout button hidden; repository throws if called anyway |
| Fewer than 2 players exist | New Bet disabled on Home with a hint to add players |
| Delete user with ACTIVE bets | Block — must resolve/cancel bets first |
| Same user tries to join bet twice | Block — one participation per user per bet |
| Bet with < 2 participants at lock | Block — minimum 2 participants required |
| Bet with < 2 outcomes | Block — minimum 2 outcomes required |
| Bet with > 8 outcomes | Block — Add Outcome disabled at 8 |
| Blank or duplicate outcome text | Block — each outcome 1–40 chars after trimming, unique ignoring case |
| Blank or overlong prompt | Block — 1–120 chars after trimming |
| Username blank, > 20 chars, or differs from an existing one only in case | Block — 1–20 chars after trimming, unique ignoring case |
| PIN not exactly 4 digits | Block — digits only, exactly 4 |
| User leaves New Bet flow | "Discard this bet?" → deletes the pending bet (§4.2) |
| Winner's share of the pot would exceed their wager in profit | Cap at even money (`payout = 2 × wager`); refund the unmatched part to losers in proportion to their wagers (§5.3) |
| Rounding remainder in payouts | One coin each to the largest-wager recipients (winners if uncapped, losers if capped), so nobody gets more than one extra coin. Tiebreaker: first added |
| Wrong PIN entered | "Wrong PIN" message, no lockout, can retry |
| App force-closed during bet creation | On relaunch, show Recovery Screen with Return to Bet / Discard Bet (see Section 11) |
| Participant dismisses a PIN prompt during a consent sequence | Whole action aborted, nothing changed |
| Admin sets negative balance | Allow — balance is a Long, admin has full control |
| Admin renames to existing username | Block — username must remain unique |
| Admin removes participant leaving < 2 | Block — must force cancel the bet instead |
| Admin removes participant leaving everyone on one outcome | Block — must force cancel the bet instead |
| Admin PIN forgotten | "Forgot admin PIN?" → recovery code (§5.6) |
| Admin edits wager, user can't cover increase | Block — user must have sufficient balance for the additional amount |
| Admin deletes ACTIVE bet | Block — must cancel or resolve first, then delete |
| Admin edits wager on RESOLVED bet | Block — wager edits only on ACTIVE bets |
| Reopen cancelled bet, participant can't cover wager | Block — all participants must have sufficient balance |
| Reopen resolved bet, winner spent their winnings | Block — every participant must have balance ≥ their `payout` |
| Reopen bet with deleted participant | Block — all original participants must still exist |
| Repeat bet | Creates new independent bet, original unaffected |
| Stored avatar missing or unreadable | Gracefully fall back to default avatar placeholder |
| Picked/captured image can't be decoded | Show "Couldn't use that image"; existing avatar unchanged |
| User cancels picker or camera | No change |
| No camera app on the device | Hide **Take Photo** |
| Large image selected for profile picture | `AvatarStore.save` downsamples while decoding (never loads the full bitmap), then saves at ≤ 512px |

---

## 11. Interrupted Bet Recovery

If the app is force-closed (or crashes) while a bet is in PENDING_CONFIRMATION status, there will be an incomplete bet in Room with its outcomes and any participants who already confirmed in Step B. A normal exit from the flow discards the bet (§4.2), so a pending bet at launch always means the app was interrupted.

### On App Launch
1. If `isFirstLaunch()` → Onboarding (§12). Otherwise:
2. Query `getPendingBet()`
3. If **found**: show a **Recovery Screen** instead of the normal Home Screen
4. The Recovery Screen displays:
   - Message: "You have an unfinished bet"
   - The bet prompt
   - Number of participants added so far
   - **"Return to Bet"** button — always opens **Step B** with the saved participants loaded. Step C confirmations are never saved, so there's nothing to resume there; the group taps Continue again from Step B.
   - **"Discard Bet"** button — `discardPendingBet`, then navigates to Home
5. No other navigation is available from this screen. Back exits the app, and the user must either resume or discard.
6. If **more than one** pending bet is found (shouldn't happen; legacy or corrupt data), discard all but the newest before showing the screen
7. If **no pending bet is found**: go to Home

### Why This Matters
- Participants in Step B have already entered PINs to join — their intent is recorded
- No wagers have been deducted yet (that happens at lock in Step C)
- Discarding is safe — no money has moved
- But abandoning silently would lose the bet setup work

---

## 12. First Launch Flow

1. App detects no admin PIN set (`isFirstLaunch()` returns true)
2. Prompt user to create a master admin PIN (4-digit, entered twice)
3. Store hashed PIN in `AppSettings` table
4. Generate and show the **recovery code** once (§5.6); continue only after "I've written this down" is ticked
5. Navigate to Home Screen (Onboarding is popped off the back stack)
6. User creates their first player(s) via Manage Players. Home's New Bet stays disabled until there are 2 players.

---

## 13. Project Structure

✅ = exists today (may still be a placeholder; see §6 "Current Implementation Gap"), 🆕 = still to create.

```
app/src/main/java/com/betcoin/
├── di/
│   ├── DatabaseModule.kt                 ✅
│   └── RepositoryModule.kt               ✅ (+ AvatarStore binding 🆕)
├── data/
│   ├── database/
│   │   ├── BetCoinDatabase.kt            ✅
│   │   ├── converter/BetStatusConverter.kt ✅
│   │   ├── dao/                          ✅ User, Bet, BetOutcome, BetParticipant, AppSettings
│   │   └── entity/                       ✅ User, Bet, BetOutcome, BetParticipant, AppSettings
│   ├── model/
│   │   ├── BetStatus.kt                  ✅
│   │   ├── BetWithDetails.kt             ✅
│   │   ├── ParticipantWithUser.kt        ✅
│   │   ├── ThemePreference.kt            🆕
│   │   └── BetCoinException.kt           🆕 typed rule violations
│   ├── payout/PayoutCalculator.kt        🆕 pure function, unit-tested
│   ├── avatar/
│   │   ├── AvatarStore.kt                🆕 interface
│   │   └── LocalAvatarStore.kt           🆕 filesDir/avatars
│   └── repository/
│       ├── UserRepository.kt             ✅ interface
│       ├── BetRepository.kt              ✅ interface
│       ├── SettingsRepository.kt         ✅ interface
│       └── local/
│           ├── LocalUserRepository.kt    ✅ in-memory placeholder → Room
│           ├── LocalBetRepository.kt     ✅ all TODO() → Room
│           └── LocalSettingsRepository.kt ✅ Room + jBCrypt
├── ui/
│   ├── theme/                            ✅ Color, Shape, Theme, Type (light palette 🆕)
│   ├── components/                       ✅ BetCoinAvatar/Button/Card/Chip/Input, DevModeIndicator (moves to the app root, see §14)
│   │   └── PinEntryDialog.kt             🆕
│   ├── onboarding/                       ✅ OnboardingScreen/ViewModel (+ recovery code 🆕)
│   ├── recovery/                         🆕 RecoveryScreen/ViewModel
│   ├── home/                             ✅ HomeScreen/ViewModel
│   ├── players/                          ✅ ManagePlayers, PlayerDetail
│   ├── admin/                            🆕 AdminScreen/ViewModel
│   ├── newbet/                           ✅ NewBetScreen (placeholder) + NewBetViewModel 🆕
│   ├── betdetail/                        🆕 BetDetailScreen/ViewModel
│   ├── leaderboard/                      ✅ LeaderboardScreen/ViewModel
│   ├── bethistory/                       ✅ BetHistoryScreen (placeholder) + ViewModel 🆕
│   └── settings/                         🆕 SettingsScreen/ViewModel
├── navigation/NavGraph.kt                ✅ (Routes object inside)
├── MainActivity.kt                       ✅
└── BetCoinApp.kt                         ✅ Application class, @HiltAndroidApp
```

Flavor-specific source sets:
```
app/src/dev/
├── AndroidManifest.xml                   🆕 allowBackup=false override
├── java/com/betcoin/devtools/            🆕 DevToolsEntry impl, DevToolsScreen/ViewModel, SampleDataSeeder
└── res/                                  ✅ app_name "BetCoin DEV"; 🆕 DEV launcher icon, no-backup rules
app/src/prod/
└── java/com/betcoin/devtools/            🆕 no-op DevToolsEntry
```

### Testing Expectations
- **Unit tests (JVM):** `PayoutCalculator`, covering proportional split, remainder, tie on largest wager, push, and the even-money cap (two-player both ways, multi-winner and multi-loser with remainders, and the losing = winning boundary). Also every ViewModel, using fake repositories. Fakes must hash or compare PINs the same way the real implementation does and never keep plain-text PINs in shared state.
- **Instrumented (in-memory Room):** every `@Transaction` operation in §7, including its failure cases, which must leave the database unchanged. Also the uniqueness and FK behavior from §3.
- **Compose UI tests:** each screen's states (loading, empty, error) and its main interactions.
- **Emulator check:** verify UI changes on the `BetCoin_Test` emulator per AGENTS.md before marking a ticket done.

---

## 14. Build Flavors (prod / dev)

Two product flavors ensure testing never touches the production database.

### Gradle Configuration
```kotlin
android {
    buildFeatures { buildConfig = true }
    flavorDimensions += "environment"
    productFlavors {
        create("prod") {
            dimension = "environment"
            applicationId = "com.betcoin"
            buildConfigField("String", "DB_NAME", "\"betcoin.db\"")
        }
        create("dev") {
            dimension = "environment"
            applicationId = "com.betcoin.dev"
            buildConfigField("String", "DB_NAME", "\"betcoin_dev.db\"")
        }
    }
}
```
The app name comes from resources: `src/main/res/values/strings.xml` → "BetCoin", overridden by `src/dev/res/values/strings.xml` → "BetCoin DEV".

### How It Works
- **Different `applicationId`**: both flavors install side by side on the same device
- **Different database name**: `DatabaseModule` reads `BuildConfig.DB_NAME`, so `prod` uses `"betcoin.db"` and `dev` uses `"betcoin_dev.db"`
- **Same DAOs, same schema**: the flavors share all code; only the database file differs. Avatars live in each app's own `filesDir`, so they're isolated too.
- **Visual distinction**: the dev flavor is named "BetCoin DEV" in the launcher
- **In-app indicator**: every screen shows the dev banner described below

### Dev Build Indicator
On the `dev` flavor, **every screen, dialog and bottom sheet** must make it obvious this isn't the real app:
- **One banner at the app root, not per screen.** `MainActivity` wraps the `NavGraph` in a root layout. When `BuildConfig.FLAVOR == "dev"`, that layout draws a full-width strip directly under the status bar (inside `WindowInsets.statusBars`) and pushes the content below it. New screens inherit it automatically, and individual screens never place `DevModeIndicator` themselves. The current per-screen calls on Home, Onboarding and Manage Players are removed.
- **Content:** `DEV BUILD · betcoin_dev.db` in `labelSmall`, `onErrorContainer` on `errorContainer`, about 24dp tall. It's visible even when the app is in light theme, and it isn't clickable.
- **Dialogs and sheets** (PIN entry, confirmations, avatar picker) sit above the root layout, so the banner stays visible under the 60% scrim, still readable through it. Full-screen dialogs aren't used.
- **Theme accent:** on dev, the status bar area behind the banner uses the error container color too, so screenshots and the recent-apps view also show it's a dev build.
- **Launcher:** the dev flavor gets "BetCoin DEV" as its name, plus a distinct adaptive icon (same artwork with a coral "DEV" badge), from `src/dev/res`.
- **Prod builds:** nothing is drawn and no space is reserved.
- **Test:** a Compose UI test runs on the dev flavor, goes through each top-level destination, and checks the banner is shown. A JVM test checks the prod flavor's root layout draws nothing.

### DatabaseModule
```kotlin
@Provides @Singleton
fun provideDatabase(@ApplicationContext ctx: Context): BetCoinDatabase =
    Room.databaseBuilder(ctx, BetCoinDatabase::class.java, BuildConfig.DB_NAME)
        .fallbackToDestructiveMigration(dropAllTables = true) // pre-v1.0 only, see §3 Schema Versioning
        .build()
```

### Why Two Apps (and no in-app database switch)
Separate flavors with separate databases are deliberate. There's **no** in-app way to switch databases:
- An in-app switch would ship test tooling and a way to reach test data inside the real app. One wrong tap during game night would mix fake balances into real standings.
- With two apps, it's always obvious which one is open (launcher name and icon, plus the app-wide banner), and each has its own data.
- Anything that should behave differently on dev (tools, backup, migrations) belongs in the `dev` source set or in flavor-specific resources, never behind a runtime toggle in `main`.

### Dev Tools Screen (dev flavor only)
A screen for setting up and resetting test states quickly. **It isn't compiled into prod.**
- **Where:** a **Dev Tools** row at the bottom of Settings, shown only on dev. It isn't PIN-gated, since the whole app is a test build.
- **How it's kept out of prod:** `main` declares a small `DevToolsEntry` interface (a `NavGraphBuilder` extension plus a Settings row composable). `src/dev` provides the real implementation; `src/prod` provides a no-op that registers no route and draws no row. All Dev Tools code, including the sample-data seeder, lives in `src/dev/java/com/betcoin/devtools/`.
- Every action shows a confirmation dialog first, and a snackbar when it's done.

| Action | What it does |
|--------|--------------|
| **Seed sample data** | Replaces the dev database with a fixed sample set. Admin PIN `0000` (recovery code shown once, as usual). Five players: Austin, Sam, Jay, Eric, Mike, with PINs `1111` `2222` `3333` `4444` `5555`. Mike has 2 bailouts and a balance under 100, so he's eligible for another. There's at least one ACTIVE bet with 3 outcomes where one outcome nobody picked (so the push can be tested), one RESOLVED, one CANCELLED, and one RESOLVED bet that includes a deleted player. Starts a fresh dev database each time, so the result is always the same. |
| **Seed pending bet** | Adds one `PENDING_CONFIRMATION` bet with 2 participants and then closes the app, so the next launch shows the Recovery screen |
| **Wipe dev database** | `clearAllTables()`, deletes `filesDir/avatars/`, then restarts at Onboarding (first launch) |
| **Reset admin PIN** | Clears the admin PIN and recovery code but keeps players and bets. Next launch goes to Onboarding to set a new PIN. |
| **Info** | Database file name, schema version, row counts per table, app version/build |

- The seeder writes through the repositories (not raw DAO inserts), so sample data follows the same rules and hashing as real data. Bets are created with `createBet` → `addParticipant` → `lockBet` → `resolveBet` / `cancelBet`.
- These sample PINs exist only in the dev seeder. Prod never contains them.

### Backup Rules
- **Prod:** Android Auto Backup is on. `data_extraction_rules.xml` (API 31+) and `backup_rules.xml` (API 24–30) include the Room database (`betcoin.db` plus its `-wal`/`-shm` files) and `filesDir/avatars/` for both `cloud-backup` and `device-transfer`. Then switching phones keeps players, balances, bet history and pictures.
- **Dev:** no backup. `src/dev/AndroidManifest.xml` overrides `android:allowBackup="false"` (with `tools:replace`), and dev-flavor copies of both rule files exclude everything, in case the manifest override is ever dropped.
- A restored prod database can come from an older app version. Before v1.0, the destructive migration fallback would wipe it on restore. That's accepted, since there's no real data yet. After v1.0, real migrations handle it.
- **Test:** check the prod rules with `adb shell bmgr backupnow com.betcoin` on the emulator, then uninstall, reinstall and restore. A JVM or lint check confirms the dev merged manifest has `allowBackup=false`.

### Usage
- **`./gradlew installProdDebug`** — install production flavor (debug build)
- **`./gradlew installDevDebug`** — install dev/test flavor (debug build)
- Both can be on the device at the same time with completely isolated data
