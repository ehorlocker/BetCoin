# BetCoin App — Handoff Context

## What Is This?

BetCoin is a **greenfield Android app** — no code has been written yet. This zip contains the complete design specification and visual companion page produced during brainstorming. The next step is implementation.

## Files Included

| File | Purpose |
|------|---------|
| `betcoin-app-spec.md` | Full design specification (the source of truth) |
| `visual-companion.html` | Interactive HTML page visualizing the spec — open in a browser |
| `HANDOFF.md` | This file — context for the implementing agent |

## Key Design Decisions (for quick context)

1. **Single device, pass-the-phone** — all data local in Room DB, no networking
2. **Multi-outcome bets** (horse race style) — prompt + 2+ outcomes, participants pick and wager
3. **Proportional payout** — winner's share = (their wager / total winning wagers) * total pot
4. **PIN system** — 4-digit numeric, hashed (bcrypt/argon2), required at every sensitive action
5. **Repository interface pattern** — repositories are Kotlin interfaces with `Local*Repository` Room implementations, swappable via Hilt `@Binds` for future online migration
6. **Build flavors** — `prod` and `dev` with separate database files, dev shows "DEV MODE" banner
7. **Interrupted bet recovery** — on launch, check for PENDING_CONFIRMATION bets and show Recovery Screen
8. **Admin panel** — master admin PIN, full CRUD powers to fix any data issue without wiping the DB
9. **Reopen bets** — RESOLVED/CANCELLED → ACTIVE with unanimous PIN consent and financial reversal
10. **Repeat bets** — clone prompt + outcomes into a new bet with no participants

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (full Compose, no XML views)
- **Navigation:** Navigation Compose
- **DI:** Dagger-Hilt (`@HiltViewModel`, `@Binds` for repo interfaces)
- **Database:** Room (Flow queries → StateFlow in ViewModels → Compose)
- **Async:** Kotlin Coroutines + Flow
- **Currency:** BetCoin stored as `Long` (integer, no floating point)

## Implementation Notes

- The spec is organized into 14 sections covering everything from data model to project structure
- Section 6 defines all repository interface methods — this is the API contract
- Section 7 lists `@Transaction` boundaries — critical for data integrity
- Section 10 has comprehensive edge cases — use these as a test checklist
- Section 14 covers build flavor setup (prod/dev)
- The visual companion HTML is standalone (no dependencies) — just open in a browser for a navigable overview

## What to Do Next

1. Read `betcoin-app-spec.md` thoroughly
2. Use the `writing-plans` skill to create an implementation plan from the spec
3. Scaffold the Android project with the structure defined in Section 13
4. Implement bottom-up: entities → DAOs → repositories → ViewModels → UI screens
