# Settings Guide

Open Settings from the gear in the Feed header. Changes take effect immediately.
Use **Search settings** to find a label, a choice, an explanation or a section.
Words can match different parts of the same row: “appearance dark” finds Theme.
Clear the search to return to the complete list.

Choices expand below their row. Current values wrap below labels so they remain
readable at large system text sizes. Toggle rows have one accessible action that
announces their current state.

## Appearance

| Setting | What it does |
| --- | --- |
| Theme | Follows the system, or uses Light or Dark. |
| Color theme | Selects Orbin (green), Blue, Red, White, Black, Purple, Orange or Teal independently of brightness. |
| True black | Uses pure black backgrounds in dark mode. |
| Feed columns | Shown on foldables and tablets: 1–3 columns when unfolded, or 1–4 on a tablet. Phones and narrow windows use one column. |
| Catalog columns | Tablets can choose 2–8 columns. Smaller windows adapt to the available width. |

Text size follows Android's system font setting or iOS Dynamic Type.

## Privacy and safety

| Setting | What it does |
| --- | --- |
| Hide NSFW boards | Hides NSFW boards from browsing surfaces. |
| Cover violent media | Uses text and filenames to cover potentially violent media. It cannot identify unlabeled content from pixels. |
| App lock | Requires device authentication before showing content. Importing a backup does not change the existing lock policy. |
| Encrypted DNS | Looks up site names over DNS-over-HTTPS through Cloudflare (the default), Google, OpenDNS or NextDNS, on Android and iOS. |
| Strict encrypted DNS | Stops loading when encrypted DNS is unavailable, instead of falling back to the network's resolver, on Android and iOS. On Android the row warns when fallback is active; iOS cannot observe that signal. |

## Data and storage

| Action | What it does |
| --- | --- |
| Clear local activity | Clears browsing history, download records and cached images; Android also clears recent searches. Tap again within four seconds to confirm. Android reports completion only after clearing finishes. This does not delete saved files from system storage. |
| Clear image cache | Clears cached images, which can download again when needed. Android shows the current cache size. |
| Export data | Exports portable settings and followed boards/bookmarks. Android also includes saved searches. |
| Import data | Restores a compatible backup into this installation. iOS treats restoration as completed setup. Invalid entries are skipped; Android's summary counts accepted saved searches. |

Exported backups are plain JSON files. Keep them in a trusted location. They do
not inherit the encryption of the app's live settings and database.

## Updates and project information

Android's **Updates** section contains **Tell me about new releases** and
**Check for updates**. iOS updates come through the App Store or TestFlight;
its **About** section links to the privacy policy and project repository and
shows version information when available.

## Other destinations

Downloads opens from Media. Thread search opens from Boards. Settings search
only filters preferences and actions; it does not search browsing history,
passwords or text drafts.

## Developer note

Keep this guide aligned with the shipped settings registries and shared screen.
`docs/wiki/` is mirrored to the GitHub Wiki after changes reach `main`.
