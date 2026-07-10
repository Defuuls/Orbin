# User Guide

This guide covers day-to-day use of Orbin as of **v34 (Dippin)**.

## Getting started

On first launch, a setup wizard walks you through subscribing to boards and choosing appearance,
media, and privacy preferences. Everything you pick there can be changed later in Settings, and
you can re-run the wizard any time via **Settings → Content → Run setup again**.

The bottom navigation bar has two tabs:

- **Feed** — the subscribed feed, a continuous stream of threads from your followed boards.
- **Gallery** — the media gallery, which since v33 also contains your **Bookmarks** tab.

> **Since v34:** earlier versions also had a dedicated **Search** tab in the bottom bar. It was
> removed in favor of the search bar built directly into the subscribed feed.

## The subscribed feed

The Feed tab loads threads from every board you subscribe to, with bounded request concurrency
so large subscription lists stay responsive. Boards are managed under
**Settings → Content → Subscriptions**.

Feed behaviors worth knowing:

- **Tap-to-top (v30):** tapping the top feed, board, or thread bar scrolls back to the top,
  iOS-style.
- **Full-screen feed (v30–v32):** an optional mode (Settings → Appearance → Full-screen feed)
  that hides the board headers, feed bars, and system bars while you scroll, so threads fill the
  entire screen as one uninterrupted list. See [[Settings Guide|Settings-Guide]] for details.
- **Feed search (v34):** a search field at the top of the feed filters your subscribed threads
  as you type. A clear button resets the query, and an inline message tells you when no
  subscribed threads match.
- **Thread limits:** **Settings → Media → Threads per board** caps how many threads each board
  contributes to the feed (6, 12, 18, or all).
- **Hidden and muted tags:** threads with hidden tags are removed from the feed; muted tags stay
  visible but de-emphasized (Settings → Content).

### Tablet layout

On larger screens (introduced in v30) the feed switches to a tablet layout with a floating dock,
combined subscribed-feed controls, auto-hiding chrome, and old-Reddit-style thumbnail-and-text
rows for faster scanning.

## Boards and catalogs

The board gallery presents boards as large, tappable tiles. Favorites stay on the home list, and
subscriptions are managed under Settings. Board catalogs support sorting and use paging, so long
catalogs load smoothly.

## The thread viewer

- **Structured replies:** quote links, quote previews, and backlinks are rendered as a proper
  reply tree — no raw HTML. Greentext and spoilers are styled natively.
- **Inline media:** images and video play inline; replies are collapsible; thread stats are
  shown.
- **Thumbnail grid:** a thumbnail-only grid view shows every attachment in the thread at a
  glance.
- **Reading history:** threads you read are tracked (locally, encrypted) with unread indicators
  and scroll-position restore.

## Gallery and bookmarks

Opening media from a thread launches a pinch-zoom swipe gallery with background preloading.

Since **v33**:

- The gallery's **board picker only offers boards you subscribe to** (and it honours the
  "Hide NSFW boards" setting), matching the feed instead of listing every board on the provider.
- **Bookmarks live in a tab inside the Gallery view.** The former bottom-navigation Bookmarks tab
  is gone, but everything it offered is intact: the watch toggle, unread badges, and remove
  actions.

Watched threads are checked in the background (WorkManager) and can notify you of new replies.

## Media playback and downloads

- Hardware-accelerated image and video rendering, progressive loading, autoplay and mute
  toggles, and background preloading (all configurable in Settings → Media).
- Since v30, video is cached through Media3 and static media no longer sends no-cache request
  headers, so repeated viewing does not re-download from the CDN.
- Downloads go through a native download manager (notifications, resume, retry) with an in-app
  history screen. Media saves to **Downloads/Orbin** by default, or to a custom folder chosen
  under **Settings → Storage → Saved media folder**. Downloads are HTTPS-only and file names are
  sanitized.

## Search

- **Feed search (v34):** filter your subscribed feed directly from the Feed tab.
- **Board search:** search threads with content-type filters for posts, images, videos, audio,
  and URLs, scoped to your subscribed boards. Recent-search history is **opt-in**
  (Settings → Network & privacy → Save recent searches).

## Privacy at a glance

- All local data (history, bookmarks, downloads, recent searches, settings) is **encrypted at
  rest**; the key never leaves the device's TEE/StrongBox.
- Optional **biometric app-lock** keeps the app gated and its content out of the recents screen.
- Networking is **HTTPS-only**, with optional DNS-over-HTTPS.
- Cloud backup and device-transfer of local data are disabled.

See the [[Settings Guide|Settings-Guide]] for every option, and [[Troubleshooting]] if something
doesn't behave as expected.
