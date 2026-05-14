---
name: Pastel Playroom Dark
colors:
  surface: '#111316'
  surface-dim: '#111316'
  surface-bright: '#37393d'
  surface-container-lowest: '#0c0e11'
  surface-container-low: '#1a1c1f'
  surface-container: '#1e2023'
  surface-container-high: '#282a2d'
  surface-container-highest: '#333538'
  on-surface: '#e2e2e6'
  on-surface-variant: '#bccac4'
  inverse-surface: '#e2e2e6'
  inverse-on-surface: '#2f3034'
  outline: '#86948e'
  outline-variant: '#3d4945'
  surface-tint: '#65dabe'
  primary: '#eafff7'
  on-primary: '#00382d'
  primary-container: '#7ef2d5'
  on-primary-container: '#006e5c'
  inverse-primary: '#006b59'
  secondary: '#ffb2b7'
  on-secondary: '#5b1822'
  secondary-container: '#7b3039'
  on-secondary-container: '#ff9da5'
  tertiary: '#f4fcff'
  on-tertiary: '#003642'
  tertiary-container: '#a4e8ff'
  on-tertiary-container: '#166a7f'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#83f7da'
  primary-fixed-dim: '#65dabe'
  on-primary-fixed: '#002019'
  on-primary-fixed-variant: '#005142'
  secondary-fixed: '#ffdadb'
  secondary-fixed-dim: '#ffb2b7'
  on-secondary-fixed: '#3f020f'
  on-secondary-fixed-variant: '#782e37'
  tertiary-fixed: '#b3ebff'
  tertiary-fixed-dim: '#8ad1e8'
  on-tertiary-fixed: '#001f27'
  on-tertiary-fixed-variant: '#004e5f'
  background: '#111316'
  on-background: '#e2e2e6'
  surface-variant: '#333538'
typography:
  headline-xl:
    fontFamily: Quicksand
    fontSize: 40px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Quicksand
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.2'
  headline-lg-mobile:
    fontFamily: Quicksand
    fontSize: 28px
    fontWeight: '700'
    lineHeight: '1.2'
  headline-md:
    fontFamily: Quicksand
    fontSize: 24px
    fontWeight: '600'
    lineHeight: '1.3'
  body-lg:
    fontFamily: Quicksand
    fontSize: 18px
    fontWeight: '500'
    lineHeight: '1.5'
  body-md:
    fontFamily: Quicksand
    fontSize: 16px
    fontWeight: '500'
    lineHeight: '1.5'
  label-md:
    fontFamily: Quicksand
    fontSize: 14px
    fontWeight: '600'
    lineHeight: '1.4'
    letterSpacing: 0.01em
  label-sm:
    fontFamily: Quicksand
    fontSize: 12px
    fontWeight: '700'
    lineHeight: '1.4'
    letterSpacing: 0.03em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  base: 8px
  container-padding: 24px
  gutter: 16px
  section-gap: 48px
---

## Brand & Style
The design system evolves into a "Midnight Playground" aesthetic. It maintains a whimsical, toy-like personality but pivots toward a magical, neon-inflected nighttime environment. The target audience remains users seeking a friendly, low-stress, and tactile experience, now optimized for low-light environments.

The style is a hybrid of **Soft Minimalism** and **Glassmorphism**. By combining maximum roundedness with deep surfaces and glowing accents, the UI feels like a collection of illuminated, frosted acrylic toys floating in a void. The emotional response is one of calm wonder—approachable and playful, yet easy on the eyes.

## Colors
The palette shifts from matte pastels to "Illuminated Pastels." The background is a deep, near-black charcoal (`#121417`) to provide maximum contrast for the glow effects.

- **Primary (Mint Glow):** `#7EF2D5` - Used for main actions and success states.
- **Secondary (Coral Glow):** `#FF9AA2` - Used for highlights, secondary actions, and playful accents.
- **Tertiary (Sky Glow):** `#A0E7FF` - Used for information, links, and decorative elements.
- **Neutral:** A scale of deep charcoals and cool greys replaces the warm whites.

Interactive elements use these colors with an added outer glow (drop-shadow) to simulate a light-emitting property.

## Typography
The design system utilizes **Quicksand** exclusively to maintain its soft, rounded geometry. 

In this dark mode variant, font weights are slightly increased (from 400 to 500 for body text) to compensate for the "thinning" visual effect of light text on dark backgrounds. Headlines remain bold and punchy. All text colors utilize high-opacity whites (87-100%) or the primary/secondary glow colors to ensure WCAG AA accessibility standards are met.

## Layout & Spacing
The layout follows a **Fluid Grid** model with generous white space (or "dark space") to allow glowing components room to breathe. 

- **Desktop:** 12-column grid, 24px gutters, max-width 1200px.
- **Tablet:** 8-column grid, 16px gutters.
- **Mobile:** 4-column grid, 16px gutters, 20px side margins.

Spacing is based on an 8px linear scale. Components use large internal padding to reinforce the "squishy" and toy-like feel. Containers should never feel cramped; margin and padding should err on the side of "loose."

## Elevation & Depth
Depth is created through **Glassmorphism** and **Luminescence** rather than traditional shadows.

1.  **Surfaces:** Cards and modals use a semi-transparent fill (`rgba(255, 255, 255, 0.05)`) with a high-density `backdrop-filter: blur(20px)`.
2.  **Borders:** Each glass container features a thin, 1px inner border (`rgba(255, 255, 255, 0.1)`) on the top and left to simulate a "highlight" on the edge of the plastic.
3.  **Glows:** Higher elevation levels are indicated by a subtle outer glow using the primary or secondary color at 10-15% opacity, creating a "hovering light" effect.

## Shapes
Maximum roundness is mandatory. This design system uses a `Pill-shaped` philosophy. 

- **Small Components (Buttons, Inputs):** 1rem (16px) radius or fully rounded caps.
- **Medium Components (Cards, Modals):** 2rem (32px) radius.
- **Large Components (Sections, Hero containers):** 3rem (48px) radius.

Avoid sharp corners entirely. Even "square" images should feature a minimum of 16px corner radius.

## Components
- **Buttons:** Fully pill-shaped. Primary buttons use a solid Mint Green gradient with a soft shadow-glow. Ghost buttons use a 2px stroke of the primary color.
- **Inputs:** Darker than the background (`#0A0C0E`) with a 2px Sky Blue focus ring. Labels always sit above the field in bold Quicksand.
- **Cards:** The hallmark of the system. Frosted glass appearance with a subtle 1px border. Content inside should be padded by at least 24px.
- **Chips:** Small, pill-shaped tags with low-opacity background tints (e.g., 10% Coral) and high-intensity text colors.
- **Progress Bars:** Thick (12px+), fully rounded tracks with glowing fills. 
- **Modals:** Centered glass containers with a backdrop overlay of `#000000` at 60% opacity to pull focus to the "toy" in the center.