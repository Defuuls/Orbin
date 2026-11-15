# Settings Guide

Open Settings from primary navigation or the Feed menu. The single scrolling
page follows NekoSurf's grouped layout with Orbin's identity and accent colors.
Changes take effect immediately. Search settings finds labels, values, choices,
explanations and section names; clear the search to return to the complete page.

Text follows Android's system font setting or iOS Dynamic Type. Labels and values
wrap at larger sizes. Toggles announce their checked state and choices announce
selection. Long playback scope lists have their own searchable picker; Back
returns to Settings before leaving the screen.

## Theme and boards

| Setting | What it does |
| --- | --- |
| Theme | Follows the system, or uses Light or Dark. |
| AMOLED black | Uses pure black backgrounds in dark mode. |
| Catalog view | Selects List, Grid or Card for Home and board catalogs. |
| Enabled sites / Followed boards | Opens the board directory to browse sources and manage real board follows. |

Platform-specific controls appear when supplied by that platform. A new setting
remains reachable even when it has not yet been assigned to a named group.

## Threads and media

| Setting | What it does |
| --- | --- |
| Open comments from chat button | Off opens videos from the thread; on opens comments. Applies to all sites. |
| Scope | Selects app defaults, a site or a board for playback preferences. |
| Use app defaults / Use site settings | Inherits playback behavior from the parent scope. |
| Autoplay / Start muted / Loop videos / Preload next clip | Controls playback in the selected scope. |
| Reset playback settings | Removes a scope override and restores inheritance when an override exists. |

## Hidden content and privacy

| Setting | What it does |
| --- | --- |
| Hide NSFW boards | Hides NSFW boards from browsing surfaces. |
| Cover violent media | Uses text and filenames to cover potentially violent media; it cannot identify unlabeled content from pixels. |
| App lock | Requires device authentication before showing content. Importing a backup does not change the lock policy. |
| Screen privacy shield | Hides the app preview in the app switcher. |
| Encrypted DNS | Chooses Cloudflare, Google, OpenDNS or NextDNS on Android and iOS. |
| Strict encrypted DNS | Stops loading when encrypted DNS is unavailable. Android warns when fallback is active; iOS cannot observe that signal. |

## History, storage and backup

| Setting or action | What it does |
| --- | --- |
| Save search history / Save browsing history | Controls whether recent searches and visited threads are recorded locally. |
| View browsing history / Downloads | Opens the existing history or downloads destination. |
| Clear local activity | Clears browsing history, download records and cached images; Android also clears recent searches. Tap again within four seconds to confirm. Saved files in system storage remain. |
| Clear image cache | Clears cached images, which can download again. Android displays the cache size. |
| Video cache size / Clear video cache | Android controls the encrypted video cache and displays its current usage. |
| Export data / Import data | Transfers portable settings, followed/favorite boards, bookmarks, persistent history, reader memory and saved media in a ZIP. Android also includes saved searches. Legacy JSON imports remain supported. |

Exported backups are portable, unencrypted ZIP files and do not inherit the encryption of the
live database. Invalid entries are skipped. iOS treats restoration as completed
setup; Android's summary counts accepted saved searches.

## About

The Orbin identity card shows the installed version. Android offers automatic
release checks and Check for updates. iOS updates through the App Store or
TestFlight. Privacy policy, project and help actions remain available where
supplied by the platform.

## Developer note

Keep this guide aligned with the shipped settings registries and shared screen.
`docs/wiki/` is mirrored to the GitHub Wiki after changes reach `main`.
