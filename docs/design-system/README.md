# Orbin Design System

There are two layers, and which one applies depends on what you are building.

`core:designsystem` is Jetpack Compose Material 3. Shared Material widgets and palette seeds still
live here; reachable product surfaces no longer mount `OrbinTheme` / `MaterialOrbinTheme` as a
shell (`MaterialOrbinTheme` was removed after Next replaced nested Material chrome). Everything
below this line describes the remaining Material token layer.

`ui-next` is the interface itself — feed, boards, thread reader, board catalog, settings, media
wall, search, downloads, gallery, onboarding, and the startup chrome screens. It defines its own palette and type rather than reading `MaterialTheme`, because the visual
style is part of what it replaced: Apple-inspired calm neutrals with one system-blue accent, OLED
dark surfaces, DestinationPill chrome, and inset grouped Settings. See [the ui-next section](#ui-next)
below before changing anything in that module. Orbin Minimal draws from this layer too: it is the
same screens over the same layers, not a second, smaller interface.

## Color

`core/designsystem/.../theme/Color.kt` defines Orbin's static fallback schemes around the brand color `#4F6BED`. Dynamic color
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

**One vocabulary, in `ui-next/.../Surface.kt` and `tokens/`.** `DestinationPill` is the permanent
chrome for Feed, Media and Boards; every other screen (Settings, threads, catalogs, Search,
Downloads) draws no bottom chrome, because its large title already says where you are. `GroupedSection` draws Settings-style inset cards. Soft `Hairline` / `GroupedDivider`
separators — not Material elevation theatre. `InlineAction` is how an action is drawn — as a word,
with a button role and a 48dp touch target. `ScreenTitle` uses the large-title type ramp and scrolls
away with content. Tokens live in `NextSpace`, `NextRadius`, `NextType`, `NextMaterials`.

**Motion is Next, not Material.** Hierarchical Feed/Board→Thread (and Search / Downloads / Gallery / two-pane detail) pushes use a soft horizontal slide + fade with parallax (`NextMotion` + `NextMotion.Ease`); primary DestinationPill tabs crossfade with a tiny nudge. Press feedback is `NextHighlightIndication` / `nextClickable` / `NextIconAction` (installed by `NextTheme`) — never a Material ripple. Progress uses thin `NextLinearProgress` / `NextCircularProgress` rather than Material bars; density uses `NextSlider`; lists refresh with `NextPullToRefresh`; toasts use `NextSnackbarHost`.


**A screen brings its own theme.** Every one wraps itself in `NextTheme`, so it draws correctly
wherever it is composed, tests included. That makes nesting the normal case, and every parameter
resolves the same way: an explicit value wins, an enclosing theme's is inherited, and failing both
there is a default. Do not give one a hardcoded default again — `darkTheme` had one, and every
screen inside a theme silently overwrote it.

Inheritance is also how a reader's settings reach this module. The shell states them once at the
top — `MainActivity` for the full client, `MinimalActivity` for Orbin Minimal — and the screens
below say nothing. Nested no-arg `NextTheme` calls short-circuit so screens do not re-enter
`MaterialTheme`. Sliders, snackbars, and pull-to-refresh inside Next screens are Next controls
(`NextSlider`, `NextSnackbarHost`, `NextPullToRefresh`); Material `Text` / `Icon` remain as type
primitives under the colorScheme `NextTheme` installs. There is no separate Material shell for
gallery or onboarding anymore. A screen here has no view model and cannot read a
setting, for the same reason it takes rows rather than threads. Three settings arrive that way:

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
this one's layout. Those two still map into Next palettes at the shell; gallery, onboarding, dialogs, and snackbars
draw through Next controls rather than a separate Material destination shell.

Colours come from `next`, not `MaterialTheme`. `boardHue()` gives a board its colour; a merged feed
is otherwise four grey characters per row. `placeholderArt()` stands in for a thumbnail that has
not loaded, as a gradient rather than flat grey, so a layout can be judged against something with
the tonal variety real content has.

Every screen is recorded as a golden under `ui-next/src/androidHostTest/screenshots`. Re-record
with `./gradlew :ui-next:recordRoborazziAndroidHostTest` and commit what changes; CI verifies them, and the
Screenshots workflow's path filter includes `ui-next/**` so a change here cannot land unverified.
