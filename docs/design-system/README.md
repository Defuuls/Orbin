# Orbin Design System

Orbin's Android and iOS screens share the Compose Multiplatform `ui-next`
module. Feature modules and iOS presenters provide formatted rows and callbacks;
the shared UI does not read repositories or app settings directly.

## Visual language

Use `next` for semantic palette roles and `NextType`, `NextSpace`,
`NextRadius` and `NextMotion` for type, spacing, shape and motion.
Tokens live in `ui-next/.../tokens/Tokens.kt`. Material primitives inherit a
complete color scheme installed by `NextTheme`, so their controls match the
custom surfaces.

The default accent is forest green. Eight selectable color themes apply across
Feed, threads, settings and shared chrome: Orbin, Blue, Red, White, Black, Purple,
Orange and Teal. Color choice is independent of System/Light/Dark brightness.
True black applies in dark mode. Reachable shared screens do not substitute
wallpaper colors or historical imageboard skins.

The shared editorial design uses 40 sp heavy titles, a decorative orbit emblem,
a short accent rule, asymmetric 32/14 dp cards, board-color identity rails and
a floating dock with solid accent selection capsules. Decorative artwork drops
out at enlarged text sizes so it never competes with reading space.

Matte tonal fills distinguish background, raised cards and elevated sheets.
Use `GroupedSection` for related settings and `GroupedDivider` or
`Hairline` for soft separation. Avoid adding a second palette or nested
application shell for an individual screen.

## Theme inheritance and text

An explicit `NextTheme` argument wins; otherwise an enclosing theme is
inherited. A nested no-argument theme reuses the installed theme. This keeps
screen previews independent without resetting the reader's palette or
compounding font scaling.

Android uses system text scaling; iOS maps Dynamic Type into the shared
density. There is no in-app font-size row. Layouts must accommodate wrapping
labels and values at 2× text, narrow windows, landscape and tablet widths.
Avoid single-line truncation for values needed to understand or confirm an action.

`core:designsystem` retains shared Material tokens and helpers. It is not
a separate product UI or a source of additional user-facing theme controls.

## Navigation and controls

Feed, Media and Boards use `DestinationPill` chrome. Detail screens such as
threads, settings, Search and Downloads use their own titles. `ScreenTitle`
scrolls with content; `CompactTitleBar` provides the compact title when needed.

Hierarchical transitions use the shared slide/fade motion; primary tabs
crossfade. Press indication adapts by platform. Android switches use Material
controls; iOS uses a native-style track centered in a touch target at least
48 dp high.

A toggle row owns one action and its checked semantics. Its visual switch is
passive. Keep an independent switch action only when it does something
different from opening the containing row, as in the Boards browser.

The dock measures its rendered height so larger labels cannot cover the final
content rows. Compact-title clearance follows actual text metrics.

Feed cards use board-color accent rules and inset metadata footers. Boards have
larger identity badges and followed-state outlines. Threads use individual tonal
post cards and wrapping metadata capsules. Media browsing groups scan and size
controls into a tonal deck; Downloads uses document cards with readable filenames.

Choices expose selected state. Pull-to-refresh exposes a semantic refresh
action as well as gestures. Snackbars announce messages politely and honor
the accessibility service's recommended reading/control timeout.

## Settings

Settings is one searchable screen with inline editing and named sections:

| Section | Content |
| --- | --- |
| Appearance | Brightness, color, True black and relevant wide-screen column choices. |
| Privacy and safety | Content covers, app lock and Android's strict DNS control. |
| Data and storage | Local activity/cache clearing and backup actions. |
| Updates / About | Android update actions; iOS project links and version information. |

Search matches all query words across a row's section, label, hint, current
display value and choices, ignoring case and extra whitespace. It never
inspects secret values or text-field drafts. Empty result groups disappear.

Current values wrap below non-toggle labels. Editors open beneath their row,
and the screen brings the editor into view. Destructive actions use the shared
destructive color and explain what they clear. Activity deletion requires a
second tap within four seconds.

## Real content and verification

Pass production thumbnails, post bodies and players through slots. The shared
screen must not reimplement spoiler, link, media or repository behavior.

Screenshot fixtures live under `ui-next/src/androidHostTest/screenshots`.
They are deterministic component examples, not device or persistence tests.
Pair them with semantic interaction tests and real feature-level journeys.

For a legitimate visual update, record and inspect new fixtures:

```bash
./gradlew recordRoborazziDebug recordRoborazziAndroidHostTest
./gradlew verifyRoborazziDebug verifyRoborazziAndroidHostTest
```

Check normal and 2× text, light/dark/AMOLED, all color themes, and representative
phone/tablet windows. Commit reviewed goldens and retain screenshot verification
as a required review signal.
