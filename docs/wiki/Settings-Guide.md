# Settings Guide

Settings is one list under three headings. Every row changes where it stands: a toggle flips, a
choice opens its options underneath, and an action runs from the row. Settings take effect
immediately, and local preference storage is encrypted.

Preferences that are no longer offered as rows keep their stored values untouched. Removing a row
never resets the preference behind it.

## General

| Setting | What it does |
| --- | --- |
| Hide NSFW boards | Removes NSFW boards from board lists and the subscribed feed. |

## Display & Media

| Setting | What it does |
| --- | --- |
| Theme | Light, dark, or follow the system. |
| Color scheme | Chooses an Orbin or imageboard-inspired palette (Default, Banana, Apple, Yotsuba, Yotsuba B, Warosu, Miku, Penumbra, Royal, Lain, Tomorrow, Tomorrow Dark). |
| True black | Uses pure black surfaces in dark mode. |
| Text size | Changes Orbin's text scale (Small, Default, Large, XL). |
| Mute by default | Sets the initial audio state for video playback in threads. |

## Privacy & Data

| Setting | What it does |
| --- | --- |
| App lock | Requires device authentication before Orbin's content is shown. |
| Clear local activity | Deletes browsing history, recent searches and download history stored on this device. |
| Downloads folder | Chooses where saved media is written. Default: `Downloads/Orbin`. |
| Export data | Writes settings, boards, bookmarks and saved searches to a file you choose. |
| Import data | Merges a backup into the existing setup rather than replacing it. |
| In-app updates | Enables Orbin's release check against GitHub. Installation stays manual. |
| Check for updates | Runs that check now (shown while in-app updates are on). |
| Image cache usage | Shows the image cache size and clears it. Images download again when needed. |
| Downloads | Opens the files you have saved from threads. |
| Search | Opens search across the catalogs of the boards you follow. |

**Important:** exported backup files are plain JSON and are not encrypted. The live app database
and preferences are encrypted, but portable exports are intentionally readable outside Orbin.

Application traffic is HTTPS-only. Private app state is excluded from Android cloud backup and
device transfer.

## Feed behavior

The subscribed feed and board catalogs use a single grid layout. Each card shows the thread's
media uncropped at its own aspect ratio.

## Accessibility behavior

Orbin's interface is tested with increased font scales, screen-reader semantics, and screenshot
baselines. On compact screens, readability takes priority over maximizing the number of grid columns.

## Developer note

If this page and the app disagree, treat the shipped UI and repository source as authoritative and
update this document in the same PR. `docs/wiki/` is mirrored automatically to the public GitHub
Wiki after changes reach `main`.
