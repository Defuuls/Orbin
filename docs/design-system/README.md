# Orbin Design System

Orbin uses Jetpack Compose Material 3 through `core:designsystem`. App screens should use
`OrbinTheme` and read colors, typography, and shapes from `MaterialTheme` rather than hardcoding
visual tokens locally.

## Color

`Color.kt` defines Orbin's static fallback schemes around the brand color `#4F6BED`. Dynamic color
is still preferred on Android 12+ when enabled by the user. The fallback scheme includes primary,
secondary, tertiary, error, surface, inverse, outline, and surface-container roles so components can
use semantic Material 3 colors consistently.

Post-renderer accents remain centralized:

- `GreentextColor`
- `QuoteLinkColor`
- `SpoilerBackground`

Shared status accents are also available for future component work:

- `SuccessColor`
- `WarningColor`
- `InfoColor`

## Typography

`orbinTypography(fontScale)` scales the full Material 3 type ramp, from display styles down to
labels. Use `MaterialTheme.typography` in UI code so the user's in-app font-size preference applies
consistently.

## Shape

`OrbinShapes` defines the app-wide corner-radius scale:

- extra small: `4.dp`
- small: `8.dp`
- medium: `12.dp`
- large: `16.dp`
- extra large: `24.dp`

Prefer `MaterialTheme.shapes` for cards, dialogs, buttons, and custom surfaces.

## Theme Behavior

`OrbinTheme` preserves the current user-facing theme controls:

- system, light, and dark theme modes
- dynamic color
- AMOLED-black dark theme
- in-app font scaling

The AMOLED variant collapses the background and surface hierarchy toward true black while preserving
readable dark-theme content roles.
