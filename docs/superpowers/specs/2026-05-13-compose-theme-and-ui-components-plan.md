# KAN-15 Implementation Plan: Compose Theme and Shared UI Components

## Phase 1: Theme Foundation (TDD)

### Task 1.1: Color Scheme
- **Test:** Write Compose test verifying `BetCoinColorScheme` contains all required colors
- **Implement:** `Color.kt` with color constants and `darkColorScheme()`
- **Verify:** Test passes, colors match DESIGN.md exactly

### Task 1.2: Typography
- **Test:** Write Compose test verifying all text styles use Montserrat with correct weights/sizes
- **Implement:** `Type.kt` with `BetCoinTypography`
- **Verify:** Test passes, typography matches DESIGN.md

### Task 1.3: Shapes
- **Test:** Write Compose test verifying shape corner radii
- **Implement:** `Shape.kt` with `BetCoinShapes`
- **Verify:** Test passes

### Task 1.4: Theme Composable
- **Test:** Write Compose test verifying `BetCoinTheme` applies colors, typography, and shapes
- **Implement:** `Theme.kt` with `BetCoinTheme` composable
- **Verify:** Test passes, theme is cohesive

## Phase 2: Shared Components (TDD)

### Task 2.1: BetCoinButton
- **States:** Primary, Secondary, BettingAction
- **Tests:** Render each variant, verify colors/shapes, verify click handling, verify disabled state
- **Implement:** `BetCoinButton.kt`

### Task 2.2: BetCoinCard
- **Features:** 24dp radius, #1E1E1E background, press-in animation (scale 0.98)
- **Tests:** Verify background color, corner radius, press animation
- **Implement:** `BetCoinCard.kt`

### Task 2.3: BetCoinInput
- **Features:** Dark background (#121212), 24dp radius, purple focus glow
- **Tests:** Verify unfocused style, focused style (border + glow)
- **Implement:** `BetCoinInput.kt`

### Task 2.4: BetCoinAvatar
- **Features:** Circular, 2px purple border, initials, rotating background colors
- **Tests:** Verify circle shape, border, initials text, background color rotation
- **Implement:** `BetCoinAvatar.kt`

### Task 2.5: BetCoinChip
- **States:** Selected (purple bg, white text), Unselected (dark gray bg, white text)
- **Tests:** Verify both states render correctly, verify click toggles state
- **Implement:** `BetCoinChip.kt`

## Phase 3: Integration

### Task 3.1: Update MainActivity
- Wrap content in `BetCoinTheme`
- Add preview/demo of components for visual verification

### Task 3.2: Final Verification
- Run all tests (unit + instrumented)
- Verify no compile errors
- Verify no default Material colors in use

## Dependencies
- `androidx.compose.material3:material3`
- `androidx.compose.ui:ui-test-junit4` (for instrumented tests)
- Montserrat font (via Google Fonts / res/font)

## Branch
`feature/kan-15-compose-theme-and-ui-components`

## Estimation
- Theme foundation: 2 hours
- Shared components: 4 hours
- Integration & testing: 1 hour
- Total: ~7 hours
