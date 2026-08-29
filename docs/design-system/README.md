# Orbin Design System

There are two layers, and which one applies depends on what you are building.

`core:designsystem` is Jetpack Compose Material 3. Components, the gallery and the onboarding
wizard use `OrbinTheme` and read colors, typography and shapes from `MaterialTheme` rather than
hardcoding visual tokens locally. Everything below this line describes that layer.

`ui-next` is the interface itself — feed, thread reader, board catalog, board picker, settings and
media wall. It defines its own palette and type rather than reading `MaterialTheme`, because the
visual style is part of what it replaced: warm ink on warm paper with one terracotta accent, a
colour per board, no elevation and no filled containers. See [the ui-next section](#ui-next) below
before changing anything in that module. Orbin Minimal draws from this layer too: it is the same
screens over the same layers, not a second, smaller interface.

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

## ui-next

The screens the app actually shows. Three rules hold it together, and breaking any of them is how
it drifts back into what it replaced.

**It never sees an app type.** A screen takes already-formatted rows and hands back an id; the
feature module does the join. That is why the same feed row serves the subscribed feed and every
board catalog, and why the screens can be screenshot without a view model.

**Anything real arrives through a slot.** Thumbnails, post bodies and video come from the shipped
components — `MediaThumbnail`, `PostCommentText`, `VideoPlayer` — passed in by the connector.
Nothing behavioural is reimplemented here, so spoilers, greentext and quote links keep working
because they are the same code, not because they were copied.

**One vocabulary, in `Surface.kt`.** `ContextRail` is the only permanent chrome. `Hairline` is the
only separator: no cards, no elevation, no filled containers. `InlineAction` is how an action is
drawn — as a word, but with a button role and a 48dp touch target, because setting an action as
text is a look and not a licence. `ScreenTitle` sets a title in the content so it scrolls away,
since a title tells you what you opened and stops being useful once you are reading.

**A screen brings its own theme.** Every one wraps itself in `NextTheme`, so it draws correctly
wherever it is composed, tests included. That makes nesting the normal case, and every parameter
resolves the same way: an explicit value wins, an enclosing theme's is inherited, and failing both
there is a default. Do not give one a hardcoded default again — `darkTheme` had one, and every
screen inside a theme silently overwrote it.

Inheritance is also how a reader's settings reach this module. The shell states them once at the
top — `MainActivity` for the full client, `MinimalActivity` for Orbin Minimal — and the screens
below say nothing. A screen here has no view model and cannot read a setting, for the same reason
it takes rows rather than threads. Three settings arrive that way:

| Setting | Effect here |
| --- | --- |
| Theme (System / Light / Dark) | `darkTheme` — which of the two palettes |
| AMOLED black | `amoled` — true black behind the same ink and accent; dark themes only |
| Font size | `fontScale` — multiplied onto the density, which is what scales the literal `sp` |

`fontScale` multiplies the scale already in force rather than replacing it, and only the *change*
is applied: a nested theme inherits an already-scaled density, so re-applying the factor there
would compound it once per screen.

Dynamic color and the ported imageboard skins deliberately do **not** arrive. This module's palette
is the argument it makes, and recolouring it from the wallpaper would be the old interface wearing
this one's layout. Those two still govern the Material surfaces around it — the gallery, the
onboarding wizard, dialogs and snackbars.

Colours come from `next`, not `MaterialTheme`. `boardHue()` gives a board its colour; a merged feed
is otherwise four grey characters per row. `placeholderArt()` stands in for a thumbnail that has
not loaded, as a gradient rather than flat grey, so a layout can be judged against something with
the tonal variety real content has.

Every screen is recorded as a golden under `ui-next/src/test/screenshots`. Re-record with
`./gradlew :ui-next:recordRoborazziDebug` and commit what changes; CI verifies them, and the
Screenshots workflow's path filter includes `ui-next/**` so a change here cannot land unverified.
