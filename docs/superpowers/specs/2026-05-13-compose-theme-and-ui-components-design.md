# KAN-15: Compose Theme and Shared UI Components — Design Spec

## Overview

Implement the Jetpack Compose theme system and shared UI components for BetCoin, following the "Electric Social Betting System" design defined in `spec/DESIGN.md`.

## Design System

### Colors
- **Background:** `#131313` (True Dark)
- **Surface:** `#1E1E1E` (cards), `#2A2A2A` (floating elements)
- **Primary:** `#8A2BE2` (Electric Purple)
- **On Primary:** `#FFFFFF`
- **Secondary:** `#00F5FF` (Electric Cyan)
- **On Secondary:** `#000000`
- **Tertiary:** `#FF007A` (Magenta)
- **On Tertiary:** `#FFFFFF`
- **Success:** `#39FF14` (Radioactive Green)
- **Error:** `#FF3131` (Sharp Red)
- **On Surface:** `#E5E2E1`
- **Outline:** `#988CA0`

### Typography
- **Font Family:** Montserrat
- **Display Large:** 48sp, Weight 900, LineHeight 52sp
- **Headline Large:** 32sp, Weight 800, LineHeight 40sp
- **Headline Medium:** 24sp, Weight 700, LineHeight 32sp
- **Body Large:** 18sp, Weight 500, LineHeight 28sp
- **Body Medium:** 16sp, Weight 400, LineHeight 24sp
- **Label Large:** 14sp, Weight 700, LineHeight 20sp, LetterSpacing 0.05em
- **Label Small:** 12sp, Weight 600, LineHeight 16sp

### Shapes
- **Small:** 8dp (0.5rem)
- **Medium:** 16dp (1rem)
- **Large:** 24dp (1.5rem) — cards, modals
- **Extra Large:** 32dp (2rem)
- **Full:** 9999dp — pills, buttons, inputs, avatars

## Architecture

### Theme Layer (`ui/theme/`)
- `Color.kt` — Color constants and dark ColorScheme
- `Type.kt` — Typography definitions with Montserrat
- `Shape.kt` — Shape definitions
- `Theme.kt` — `BetCoinTheme` composable wrapping Material3 theme

### Component Layer (`ui/components/`)
- `BetCoinButton.kt` — Primary, Secondary, and Betting Action buttons
- `BetCoinCard.kt` — 24dp rounded betting card with press-in animation
- `BetCoinInput.kt` — Dark input field with purple focus glow
- `BetCoinAvatar.kt` — Circular initials avatar with colored backgrounds
- `BetCoinChip.kt` — Selectable chip for odds/categories

## Testing Strategy

All components must be tested with Compose UI tests:
- Verify theme colors are applied correctly
- Verify typography is rendered with correct styles
- Verify each component renders in all states (enabled/disabled, selected/unselected)
- Verify interactive states (focus, press, click)
- Verify accessibility (content descriptions, semantic roles)

## Acceptance Criteria

- [ ] `BetCoinTheme` provides consistent dark theme across the app
- [ ] All shared components match DESIGN.md specifications
- [ ] Components support Material3 theming via `MaterialTheme`
- [ ] Each component has Compose UI tests verifying rendering and interaction
- [ ] MainActivity uses `BetCoinTheme`
- [ ] No default purple/teal Material colors remain in the app
