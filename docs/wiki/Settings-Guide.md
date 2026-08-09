# Settings Guide

A tour of every option in Orbin's Settings, current as of **v78 (Alioth)**. Settings are stored
in an encrypted DataStore and take effect immediately, with one exception: the image cache limit
applies on the next app start.

**Settings is a hub, not one long page** *(v68)*: tapping Settings opens a short list of
categories, and each opens its own screen. This page still groups options the same way, one
`##` heading per category below — each heading names the screen you land on, not a scroll
position on a single page.

## Site

Shown only when more than one provider is installed. Sits directly on the Settings hub, above
the category list — the only setting that does, since one control does not earn a page of its
own.

| Setting | What it does |
| --- | --- |
| Active provider | Chooses which image board engine (provider) the app browses. |

## Content & Feed

| Setting | What it does |
| --- | --- |
| Personalized home feed | Toggles the personalized home feed. |
| Subscriptions | Opens the screen for managing subscribed boards — these power the Feed tab and (since v33) the gallery's board picker. |
| Hidden tags | Comma-separated tags; matching threads are **removed** from feeds. |
| Muted tags | Comma-separated tags; matching threads **stay visible but are de-emphasized** in the feed. |
| Hide NSFW boards | Hides NSFW boards from board lists and pickers (including the gallery's board picker since v33). |
| Hide text-only threads | Hides threads that have no media. |
| Refresh feed on return | How stale the feed may be before returning to it reloads it: **Always**, 1, 5, 15 or 30 minutes, or **Never**. Was an on/off switch before; *Always* and *Never* are what those two positions meant, and an existing preference carries over to the matching end. |
| Threads per board | Caps how many threads each board contributes to the subscribed feed: 6, 12, 18, or all. |
| Run setup again | Re-runs the first-launch wizard (subscriptions, preferences, privacy). |

## Notifications

| Setting | What it does |
| --- | --- |
| Thread watch notifications *(v51)* | "Get notified when watched threads have new replies." On by default. |
| Quiet hours start / end *(v51)* | Suppresses watch notifications between the two times, in 24-hour `HH:MM` format. Leave either empty to disable quiet hours. |

## Appearance

| Setting | What it does |
| --- | --- |
| Color theme | Picks the app color theme. |
| App icon *(v57)* | Picks the launcher icon: Orbital Orb, Nested Rings, Abstract Flow, Minimalist Essence, or Dual Gradient. Applied immediately, without restarting the app. |
| Theme mode | Light, dark, or follow system. |
| Dynamic color | Uses Material You dynamic color from your wallpaper. |
| AMOLED black | Pure-black dark theme for OLED screens. |
| Open threads as | **Page** pushes the thread and takes the feed with it — ordinary Android forward navigation, and the default. **Slide over** lays the thread on top instead, leaving the feed in place underneath so going back reveals it rather than sliding it back. |
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

## Media & Playback

| Setting | What it does |
| --- | --- |
| Autoplay videos | Starts videos automatically. |
| Mute by default | Videos start muted. |
| Fullscreen video | Plays videos edge-to-edge, hiding the system bars and app chrome. |
| Auto-rotate video | Rotates to landscape automatically when a landscape video starts playing. |
| Media scroll in thread *(v55)* | "Swipe to scroll through multiple attachments in thread view." On by default. |
| Media scroll in board *(v55)* | "Swipe to scroll through multiple attachments in board view." Off by default. |
| Autoplay videos in feed *(v74)* | Plays each thread's first video inline and muted (per **Mute by default** above) as its row scrolls into view in the subscribed feed, stopping as it scrolls away — no need to open the thread. Only ever the first attachment; a video reached by swiping (with **Media scroll in board** also on) still needs a tap. Off by default. |
| Preload images | Preloads images in the background. |
| Preload content | What to preload. |
| Preload speed | How aggressively preloading runs. |

## Privacy & Network

| Setting | What it does |
| --- | --- |
| Lock with biometrics | Requires biometric/device-credential unlock; re-arms after backgrounding and keeps content out of the recents preview. While on, a lock icon appears centered in the subscribed feed's top bar — tap it to lock instantly, without waiting for a background/foreground cycle. |
| Save recent searches | **Opt-in** recent-search history; off by default. |
| Internal updater *(v34)* | Shows the **Check for updates** row below it. On by default; turn it off if you prefer to update manually from GitHub Releases. |
| Check for updates | Asks GitHub for the newest published release and compares it with the running build. Only release *metadata* is fetched — Orbin never downloads or installs an APK, so when an update exists the row offers a link to the release page, where the signed APK and its checksum are. Appears only while **Internal updater** is on. |
| Clear local activity | Deletes browsing history, recent searches, and download history stored on this device (with a confirmation dialog). |
| HTTPS only | Always enforced — shown for transparency, not toggleable. |
| DNS over HTTPS | **Always on** — the setting is *which* resolver answers your lookups (Cloudflare, OpenDNS, NextDNS), not whether they are encrypted. There is no off switch. If a network blocks the resolver you pick, Orbin resolves through the system resolver so the app keeps working, and says so beneath the picker rather than downgrading you silently. |

## Advanced *(v60, its own page since v68)*

Network internals. The defaults suit almost everyone; these exist for constrained or unusual
connections. Reached from a row at the bottom of **Privacy & Network** rather than from the
Settings hub directly — one nesting level deeper, since it's the one category most people never
open.

| Setting | What it does |
| --- | --- |
| Custom user agent | Sent with every request. Leave empty to use Orbin's default. Applies immediately — the header is read per request. |
| Connect timeout | How long to wait establishing a connection: 10, 15, 30 or 60 seconds. |
| Read timeout | How long to wait for data once connected: 15, 30, 60 or 120 seconds. |
| Check certificate revocation | Asks each certificate authority whether a site's certificate has been revoked. **Off by default**: the check is slow and many networks block it, which surfaces as sites failing to load rather than as a warning. |

Timeouts and revocation checking are baked into the network client when it is built, so changes
to those three take effect the next time Orbin starts. The user agent is the exception.

## Storage & Backup

| Setting | What it does |
| --- | --- |
| Downloads | Opens the download history screen. |
| Saved media folder | Picks the folder downloads are saved to; defaults to `Downloads/Orbin`. Thread-link exports also go here. |
| Download folder structure *(v78)* | How downloaded media is organized within the saved media folder: **Flat** (everything in one folder, the default), **By board**, **By board, then thread**, or **By thread**. A file already saved keeps its folder even if you change this afterward — only new downloads pick up the new structure. |
| Image cache limit *(v60)* | How much disk the image cache may use: 128, 256, 512 or 1024 MB. Applies on the next app start. |
| Export data *(v60)* | Writes settings, subscribed and favourite boards, bookmarks and saved searches to a file you choose. |
| Import data *(v60)* | Restores a previously exported file. Boards and bookmarks are **added**, never removed, so importing cannot destroy an existing setup. The saved media folder is not restored — re-pick it to re-grant access. |

## Where the rest of your privacy lives

Some protections are structural rather than settings:

- The local database (history, bookmarks, downloads, recent searches) is encrypted with
  SQLCipher, and settings with an encrypted DataStore; both keys are hardware-backed and never
  leave the TEE/StrongBox.
- Cloud backup and device-transfer of local data are disabled at the manifest level, so nothing
  syncs off the device implicitly. **Export data** is the deliberate alternative: it moves the
  same data only when you ask, to a file you choose. That file is plain JSON and is *not*
  encrypted, so keep it somewhere you trust.
- Downloads only accept HTTPS URLs, and remote file names are sanitized before being written.
