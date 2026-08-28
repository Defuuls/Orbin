# Orbin Design System

There are two layers, and which one applies depends on what you are building.

`core:designsystem` is Jetpack Compose Material 3. Components, the gallery, the onboarding wizard
and Orbin Minimal use `OrbinTheme` and read colors, typography and shapes from `MaterialTheme`
rather than hardcoding visual tokens locally. Everything below this line describes that layer.

`ui-next` is the interface itself — feed, thread reader, board catalog, settings and media wall. It
defines its own palette and type rather than reading `MaterialTheme`, because the visual style is
part of what it replaced: warm ink on warm paper with one terracotta accent, a colour per board, no
elevation and no filled containers. See [the ui-next section](#ui-next) below before changing
anything in that module.

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

Colours come from `next`, not `MaterialTheme`. `boardHue()` gives a board its colour; a merged feed
is otherwise four grey characters per row. `placeholderArt()` stands in for a thumbnail that has
not loaded, as a gradient rather than flat grey, so a layout can be judged against something with
the tonal variety real content has.

Every screen is recorded as a golden under `ui-next/src/test/screenshots`. Re-record with
`./gradlew :ui-next:recordRoborazziDebug` and commit what changes; CI verifies them, and the
Screenshots workflow's path filter includes `ui-next/**` so a change here cannot land unverified.
