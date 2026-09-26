# Settings Guide

Settings opens from the gear in the Feed header. It is deliberately short: a few preferences, the
things you do to your data, and nothing else. Everything else is decided by the app, so there is
nothing to tune before browsing. Settings take effect immediately, and local preference storage is
encrypted.

## Preferences

| Setting | What it does |
| --- | --- |
| Hide NSFW boards | Removes NSFW boards from the board list, the feed and All media. |
| Cover violent media | On by default. Puts a spoiler cover over the images and videos of posts whose subject, text or filename mentions violence, accidents or graphic footage (for example *fight*, *car crash*, *bodycam*, *nsfl*). If a thread's opening post does, every file in the thread is covered. Open the post to see the media. It reads text only, so unlabelled media is not covered. Clearly labelled gore is hidden outright by a filter that cannot be turned off. |
| Theme | Light, dark, or follow the system. |
| True black | Uses pure black surfaces in dark mode. |
| Feed columns | Foldables and tablets only. How many columns the feed shows on a wide screen: 1–3 on an unfolded foldable ("Feed columns when unfolded"), 1–4 on a tablet. Starts at 2. A foldable's front screen, a phone, and any window narrower than 600dp always show one column. |
| App lock | Requires device authentication before Orbin's content is shown. |
| Tell me about new releases | When on (the default), Orbin checks for a new release at most once a day when it opens and offers to install it. |

## Your data

| Setting | What it does |
| --- | --- |
| Clear local activity | Deletes browsing history, recent searches and download history stored on this device. Tap once and the row reads "Tap again to delete"; a second tap within a few seconds clears it. |
| Clear image cache | Shows the image cache size and clears it. Images download again when needed. |
| Check for updates | Asks GitHub whether a newer release exists and, if one does, offers to download and install it from the app. |
| Export data | Writes settings, boards, bookmarks and saved searches to a file you choose. |
| Import data | Merges a backup into the existing setup rather than replacing it. |

**Important:** exported backup files are plain JSON and are not encrypted. The live app database
and preferences are encrypted, but portable exports are intentionally readable outside Orbin.

## Elsewhere

- **Saved files** open from **Saved ›** under the All media title.
- **Search** opens from **Search threads ›** under the Boards title.

## Decided for you

- **Text size** follows your phone's font size.
- **Colours** use Orbin's one palette, in light or dark.
- **Video** starts muted. Unmute one and the next video plays with sound until Orbin closes.
- **Downloads** go to `Downloads/Orbin`, in a folder per board and thread.
- **Networking** is HTTPS only, with DNS over HTTPS. Private app state is excluded from Android
  cloud backup and device transfer.

Preferences that earlier versions offered are ignored when an older backup is imported, so a restore
never fails because of them.

## Developer note

If this page and the app disagree, treat the shipped UI and repository source as authoritative and
update this document in the same PR. `docs/wiki/` is mirrored automatically to the public GitHub
Wiki after changes reach `main`.
