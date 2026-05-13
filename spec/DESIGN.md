---
name: Electric Social Betting System
colors:
  surface: '#131313'
  surface-dim: '#131313'
  surface-bright: '#393939'
  surface-container-lowest: '#0e0e0e'
  surface-container-low: '#1c1b1b'
  surface-container: '#201f1f'
  surface-container-high: '#2a2a2a'
  surface-container-highest: '#353534'
  on-surface: '#e5e2e1'
  on-surface-variant: '#cfc2d7'
  inverse-surface: '#e5e2e1'
  inverse-on-surface: '#313030'
  outline: '#988ca0'
  outline-variant: '#4c4354'
  surface-tint: '#dcb8ff'
  primary: '#dcb8ff'
  on-primary: '#480081'
  primary-container: '#8a2be2'
  on-primary-container: '#eed9ff'
  inverse-primary: '#8422dc'
  secondary: '#e6feff'
  on-secondary: '#003739'
  secondary-container: '#00f4fe'
  on-secondary-container: '#006c71'
  tertiary: '#ffb1c3'
  on-tertiary: '#66002c'
  tertiary-container: '#c5005d'
  on-tertiary-container: '#ffd7df'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#efdbff'
  primary-fixed-dim: '#dcb8ff'
  on-primary-fixed: '#2c0051'
  on-primary-fixed-variant: '#6700b5'
  secondary-fixed: '#63f7ff'
  secondary-fixed-dim: '#00dce5'
  on-secondary-fixed: '#002021'
  on-secondary-fixed-variant: '#004f53'
  tertiary-fixed: '#ffd9e0'
  tertiary-fixed-dim: '#ffb1c3'
  on-tertiary-fixed: '#3f0019'
  on-tertiary-fixed-variant: '#8f0041'
  background: '#131313'
  on-background: '#e5e2e1'
  surface-variant: '#353534'
typography:
  display-lg:
    fontFamily: Montserrat
    fontSize: 48px
    fontWeight: '900'
    lineHeight: 52px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Montserrat
    fontSize: 32px
    fontWeight: '800'
    lineHeight: 40px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Montserrat
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  body-lg:
    fontFamily: Montserrat
    fontSize: 18px
    fontWeight: '500'
    lineHeight: 28px
  body-md:
    fontFamily: Montserrat
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-lg:
    fontFamily: Montserrat
    fontSize: 14px
    fontWeight: '700'
    lineHeight: 20px
    letterSpacing: 0.05em
  label-sm:
    fontFamily: Montserrat
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  base: 8px
  container-padding: 20px
  gutter: 16px
  stack-sm: 12px
  stack-md: 24px
  stack-lg: 40px
---

## Brand & Style

This design system is built for a high-energy, high-stakes social environment. The brand personality is **Electric, Competitive, and Socially Connected**. It moves away from traditional, heavy gambling aesthetics toward a sleek, "Fintech-meets-Gaming" vibe. 

The visual style is **High-Contrast Modernism with Glassmorphic Accents**. By utilizing a deep dark mode background with a singular, vibrant neon accent, the interface directs focus toward active bets and social interactions. The atmosphere should feel like a premium digital arena—fast-paced and rewarding.

Key visual principles:
- **Kinetic Energy:** Use the primary accent color to highlight movement, such as wins, active streaks, and live odds updates.
- **Anonymized Identity:** Social connection is fostered through typography-driven avatars rather than photography, maintaining a consistent and clean aesthetic.
- **Sleek Tactility:** Elements should feel physically satisfying to interact with, using oversized radii to soften the aggressive high-contrast color palette.

## Colors

The color palette is anchored by a "True Dark" foundation to make the "Electric Purple" accent pop with maximum intensity.

- **Primary Accent:** Electric Purple (#8A2BE2) is used for primary actions, active states, and brand-heavy moments.
- **Base Environment:** A Dark Charcoal (#121212) background reduces eye strain during long sessions and provides a sophisticated backdrop for neon elements.
- **Secondary Highlights:** Use Cyan (#00F5FF) for secondary data points like betting odds and Magenta (#FF007A) for high-urgency notifications or losses.
- **Semantic Colors:** Success states (wins) should utilize a "Radioactive Green" (#39FF14) to provide instant dopamine feedback, while errors or "bet closed" states use a sharp Red (#FF3131).

## Typography

This design system exclusively uses **Montserrat** to achieve a bold, geometric, and modern feel. The hierarchy is heavily weighted toward bold and extra-bold weights to convey confidence and clarity in high-pressure betting scenarios.

- **Headlines:** Should always use "ExtraBold" (800) or "Black" (900) weights. Tighten letter-spacing for large display text to create a more "editorial" and impactful look.
- **Numbers:** Since betting involves heavy data, use "Bold" (700) for all monetary values and odds to ensure they are the most legible elements on the screen.
- **Labels:** Small labels use uppercase with slight letter-spacing to provide a clean, "pro-sport" aesthetic.
- **Body:** Maintain Medium (500) weight for general readability against dark backgrounds, preventing the text from appearing too thin or "washed out" by the dark mode.

## Layout & Spacing

The layout utilizes a **Fluid Mobile Grid** designed for thumb-centric interaction. 

- **Grid System:** A 4-column layout for mobile with 20px side margins. This ensures content feels centered and contained.
- **Safe Zones:** High-action buttons (Place Bet, Join Room) are anchored to the bottom 30% of the screen for ergonomic ease.
- **Vertical Rhythm:** Spacing follows an 8px base unit. Use generous 24px gaps between different betting markets and 12px gaps between items within a single social feed or list.
- **Social Density:** Use tighter spacing for chat components to keep the "social buzz" high, while giving betting cards more breathing room to avoid accidental taps.

## Elevation & Depth

Depth in this design system is achieved through **Tonal Layering** and **Subtle Glows** rather than traditional drop shadows.

- **Surface Tiers:** The background is #121212. Cards and containers use #1E1E1E. Floating elements (like the Bet Slip) use #2A2A2A.
- **The "Electric" Glow:** Instead of black shadows, use a very soft, low-opacity Purple (#8A2BE2) shadow for active elements. This gives the appearance of the button emitting light onto the dark surface.
- **Glassmorphism:** Use a 20px Backdrop Blur with 10% white opacity for top navigation bars and bottom sheets. This maintains a sense of context and depth without cluttering the UI.
- **Borders:** Use 1px "Inner Glow" borders (semi-transparent white or purple) to define the edges of cards against the dark background.

## Shapes

The shape language is defined by **Extra-Large Radii (24px)**, creating a friendly and approachable feel that balances the intense color scheme.

- **Primary Containers:** All main betting cards and modal windows must use a 24px corner radius.
- **Interactive Elements:** Buttons and input fields should be fully pill-shaped (rounded-full) to emphasize their "tap-ability."
- **Initials Avatars:** User representation is strictly circular. No square edges are permitted for social identity components. 
- **Visual Consistency:** Even "inner" elements, like progress bars or selection chips, should maintain high roundedness to match the parent containers.

## Components

### Social Avatars (The "Identity" System)
- **Design:** Perfect circles with a 2px Electric Purple border.
- **Content:** Centered initials using Montserrat Bold.
- **Backgrounds:** Use a rotation of 5 brand-approved colors (Purple, Cyan, Magenta, Slate, and Indigo) to differentiate users in a feed.

### Buttons
- **Primary:** Full Electric Purple background, White text, 24px/Pill height.
- **Secondary:** Transparent background with an Electric Purple 2px stroke.
- **Betting Action:** High-saturation Green (#39FF14) with Black text for "Confirm Bet" to signal the finality of the action.

### Betting Cards
- **Structure:** 24px rounded corners, #1E1E1E background. 
- **Header:** Bold Montserrat title, small label for "Market Type."
- **Interaction:** The entire card should have a subtle "press-in" animation (scale 0.98) when tapped.

### Input Fields
- **Style:** Darker than the card background (#121212) with a 24px radius.
- **Focus State:** 2px Electric Purple border with a faint purple outer glow.

### Chips & Tags
- **Use:** For selecting odds or categories.
- **State:** Unselected tags are dark gray with white text; selected tags flip to Electric Purple with White text.

### The "Bet Slip"
- **Design:** A persistent floating bottom bar or an expandable sheet.
- **Visual:** Uses a heavy backdrop blur (Glassmorphism) to feel layered over the main content.