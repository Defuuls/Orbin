# Coral handoff completion tracker

Updated 2026-11-13 for the redesign handoff integration. This file tracks current source separately from validation and distribution. The [design README](docs/design-system/README.md) describes current behavior; the [implementation map](docs/design-system/IMPLEMENTATION.md) links production code and regression coverage.

## Source status

| Handoff step | Source status | Preserved behavior / coverage |
| --- | --- | --- |
| Phase 2 and Phase 4a prerequisite work | Merged before this integration: #832 and #833 | Coral palette, card-stack identity, Boards/catalog/thread work and cleanup waves 0–3 remain the base. |
| Phase 3 foundation | Integrated from `phase3-complete` in `ece89cae4adbfca21fc078cc1cda091a89d827e8` | Onboarding, app lock, safe mode, What's New, update/local-data dialogs, action sheets and snackbars. Provider radio state, single snackbar announcement, title headings and popup focus are covered by added assertions. |
| Search | Implemented | Coral pill query/source controls, retained source during loading, real retry and empty/error states; Search interaction coverage updated. |
| Downloads | Implemented on both hosts | Lazy keyed Coral cards, actual progress/status, accessible large-text Retry, clear confirmation and save-location guidance; shared and host continuity tests added. |
| Watched / Saved / History | Implemented | Coral stored-record cards retain provider-qualified identity, unread metadata and reading anchors. Record presentation/interaction and screenshot fixtures cover them. |
| Media wall | Implemented | Coral album cards, retained filtering/scan states, embedded header handling and explicit selected-file routing. |
| Two-pane layout | Implemented | Coral frame and empty-detail card retain existing breakpoints, width arithmetic, pane visibility and saved state. Tests cover the empty state and lifecycle. |
| Feed and feed states | Implemented on both hosts | Coral cards, inline Grid/List toggle, static previews, separate thumbnail/text routes, explicit subscriptions, retained anchors and long-press options. Missing preference defaults to Grid; stored List survives. |
| Primary navigation | Implemented on both hosts | Boards / Home (Feed) / Downloads / Settings, accessible selected state and real active-download badge. Settings/Downloads replace each other without discarding the reader; existing Back/drawer contracts remain. |
| Settings and sub-pages | Implemented | Coral cards, pill search and category chrome retain real callbacks, settings IDs, persistence, validation and backup rules. |
| Media viewer | Implemented on both hosts | Persistent Download and Close X, double-tap save, guarded horizontal close, retained pinch/pan and paging, 1x / 1.25x / 2x preserving pause/identity, and download of the other files in the current thread. Selection recovery requires a unique thumbnail; missing/ambiguous selections do not silently open the first file. |
| Direct media load continuity | Implemented | Loading and failure keep Close available; Retry reloads; leaving cancels the request; selected attachment routing is covered by host tests. |
| Long-thread fast scroll | Implemented | Shared accessible thumb for long Posts lists, one conflated scroll worker and unchanged ordinary gestures. Short threads and Files omit it. |
| Archive page | Implemented and browser fixture checked | Canonical Coral light/dark roles and native closed spoiler disclosures conceal nested colored text/links. Keyboard/pointer and independent nested reveal passed the local extracted-CSS/tag fixture; all 7 generated-archive Kotlin cases passed in the 16:23 UTC full checkpoint. |
| Final shared token/resource cleanup (Phase 4 waves 4–7) | Implemented | One public OrbinType/OrbinRadius, migrated live legacy resource names, and removed unused legacy font/geometry artifacts. Existing text, Roboto metrics, Dynamic Type and live reader behavior are retained. |
| Window density and narrow large-text controls | Implemented | Exact outer density is retained across dialog/sheet/popup windows. Five dialog/sheet and two preference-popup layout regressions plus the narrow media-toolbar case cover actual text scaling and reachable controls. |
| Native system-bar contrast | Implemented and host checked | Light Coral reader pages use dark icons; black media and dark/AMOLED use light icons, with setup/privacy restoring the selected palette. Native UIKit execution remains a CI receipt. |
| Release metadata | Prepared | 205-Nopales / versionCode 223 is recorded in release/next.toml; preparation is separate from publishing and TestFlight. |
| Documentation | Updated for current source | Current Coral behavior is distinguished from historical compact Clover references. Historical test/screenshot/profile receipts are retained as historical rather than attributed to this diff. |

All requested handoff source is implemented. Local validation also passed; final-head CI and distribution receipts are attached to the integration PR and release workflows after this source snapshot.

## Completed local verification

- **1,443 unit/host tests passed**, with no failures, errors or skips. Coverage, detekt and ktlint passed on the integrated candidate.
- Android debug APKs and instrumentation APKs assembled. Shared code compiled for both iOS arm64 and simulator-arm64 targets.
- Intentional screenshots were recorded and visually reviewed, then passed strict UI/gallery comparison. All 33 thread references remain byte-identical.
- **70 Python cases**, repository consistency, **30-module** architecture validation and all **28 document renders** passed. Archive keyboard/pointer/nested-spoiler browser checks passed.
- Release **205-Nopales / versionCode 223** metadata is prepared and verified. Publishing is distinct from preparation.

## Delivery evidence after this source snapshot

The integration PR supplies exact-head CI, Android device execution, native iOS tests/startup and performance receipts before protected merge. Mandatory eligible-board setup, persisted follow, Start/Done, cold relaunch, native headings and Dynamic Type remain execution contracts. Signed release and TestFlight receipts are supplied by the release workflows only after successful publication/upload. The [implementation map](docs/design-system/IMPLEMENTATION.md) records scope; physical-device observations remain separate in the [owner checklist](docs/ios-reader-checklist.md).

## Delivery constraints

- Orbin remains read-only: no posting, reply or composer UI.
- Preserve the card-stack icon, single-label native Orbin wordmark, bundled Roboto and single system-text scaling path.
- Preserve explicit subscriptions, eligible first-run gating, source-qualified identities, saved positions, playback ownership and real settings/download actions.
- Historical Clover diagrams and measured compact-post metrics remain provenance; current Coral cards/navigation/settings are the live design.
- Use normal merges and review intentional visual changes. Historical green checks do not validate a new integration head.
