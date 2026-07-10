# Settings Guide

A tour of every section of Orbin's Settings screen, current as of **v35 (Proxima Centauri)**. Settings
are stored in an encrypted DataStore and take effect immediately — no restart required.

## Site

Shown only when more than one provider is installed.

| Setting | What it does |
| --- | --- |
| Active provider | Chooses which image board engine (provider) the app browses. |

## Content

| Setting | What it does |
| --- | --- |
| Personalized home feed | Toggles the personalized home feed. |
| Subscriptions | Opens the screen for managing subscribed boards — these power the Feed tab and (since v33) the gallery's board picker. |
| Hidden tags | Comma-separated tags; matching threads are **removed** from feeds. |
| Muted tags | Comma-separated tags; matching threads **stay visible but are de-emphasized** in the feed. |
| Hide NSFW boards | Hides NSFW boards from board lists and pickers (including the gallery's board picker since v33). |
| Hide text-only threads | Hides threads that have no media. |
| Run setup again | Re-runs the first-launch wizard (subscriptions, preferences, privacy). |

## Appearance

| Setting | What it does |
| --- | --- |
| Color theme | Picks the app color theme. |
| App icon | Picks an app icon variant. |
| Theme mode | Light, dark, or follow system. |
| Dynamic color | Uses Material You dynamic color from your wallpaper. |
| AMOLED black | Pure-black dark theme for OLED screens. |
| Full-screen feed | Hides the board headers, feed bars, and system bars so the feed fills the whole screen (see below). |
| Font size | Global font scale. |
| Thumbnail size | Thumbnail size used in feeds and catalogs. |

### Full-screen feed, in detail

Introduced in v30 and refined through v32:

- **v30:** the subscribed feed hides its top and bottom bars while scrolling for more reading
  space.
- **v31:** the option became *truly* full screen — the status and navigation bars hide together
  with the feed chrome, and the duplicated window-inset padding that left white strips at the
  top and bottom of the feed was removed.
- **v32:** the pinned board headers are dropped entirely while the option is on — nothing stays
  fixed at the top and boards are no longer listed between threads, so the feed is a total
  full-screen view.

## Media

| Setting | What it does |
| --- | --- |
| Autoplay videos | Starts videos automatically. |
| Mute by default | Videos start muted. |
| Preload images | Preloads images in the background. |
| Preload content | What to preload. |
| Preload speed | How aggressively preloading runs. |
| Threads per board | Caps how many threads each board contributes to the subscribed feed: 6, 12, 18, or all. |

## Network & privacy

| Setting | What it does |
| --- | --- |
| Lock with biometrics | Requires biometric/device-credential unlock; re-arms after backgrounding and keeps content out of the recents preview. |
| Save recent searches | **Opt-in** recent-search history; off by default. |
| Internal updater *(v34)* | "Check for Orbin updates inside the app." On by default; turn it off if you prefer to update manually from GitHub Releases. |
| Clear local activity | Deletes browsing history, recent searches, and download history stored on this device (with a confirmation dialog). |
| HTTPS only | Always enforced — shown for transparency, not toggleable. |
| DNS over HTTPS | Resolves DNS over HTTPS; when enabled, a **DNS provider** picker appears (Cloudflare, OpenDNS, NextDNS). |

## Storage

| Setting | What it does |
| --- | --- |
| Downloads | Opens the download history screen. |
| Saved media folder | Picks the folder downloads are saved to; defaults to `Downloads/Orbin`. Thread-link exports also go here. |

## Where the rest of your privacy lives

Some protections are structural rather than settings:

- The local database (history, bookmarks, downloads, recent searches) is encrypted with
  SQLCipher, and settings with an encrypted DataStore; both keys are hardware-backed and never
  leave the TEE/StrongBox.
- Cloud backup and device-transfer of local data are disabled at the manifest level.
- Downloads only accept HTTPS URLs, and remote file names are sanitized before being written.
