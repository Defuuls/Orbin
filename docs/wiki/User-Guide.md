# User Guide

This guide describes the current Orbin interface and day-to-day behavior. For historical changes,
use [[Release History|Release-History]] or the repository CHANGELOG rather than relying on old UI
instructions.

## Getting started

On first launch, Orbin walks through provider/board selection and key appearance, media, and privacy
preferences. Those choices can be changed later in Settings.

Orbin is a **read-only browser**. It can browse, watch, save, download, search, filter, and export,
but it does not post, reply, or create threads.

## Feed

The Feed combines threads from subscribed boards and orders them by activity. Read state, filtering,
provider errors, and refresh behavior are handled consistently with board catalogs.

### Grid-first layout

The normal feed is now an **adaptive grid**. The previous List option is no longer exposed.

On compact phones, cards are prevented from shrinking into tiny columns. The grid prioritizes:

1. the thread subject,
2. board identity,
3. recent activity,
4. reply/media counts and preview imagery.

Subjects use a stronger text hierarchy and can occupy several lines before truncating. Preview
images use a consistent card proportion so text does not get squeezed unpredictably.

The **Images** layout remains available when you want a media-first wall instead of thread cards.
Legacy saved List state is interpreted as Grid, so upgrades do not strand an old preference.

### Feed behavior

- Pull down to refresh immediately.
- Already-read threads are visually de-emphasized without becoming illegible.
- Hidden tags remove matching content; muted content remains visible but quieter.
- The thread-per-board limit controls how much each subscription contributes.
- Optional inline video autoplay starts muted and stops as the active preview leaves view.
- Search filters subscribed content and saved searches can be reused.
- Network/offline state is surfaced explicitly.

## Boards and catalogs

Board catalogs use the same grid-first presentation and readability rules as the subscribed feed.
Sorting and paging remain available. The Images layout is useful when browsing a board primarily by
its OP media.

On screens at least 840dp wide, opening a thread from a catalog can keep the catalog visible in a
two-pane layout. Moving across the width threshold preserves the open thread rather than resetting
your place.

## Thread reader

Orbin parses post markup into native UI rather than showing raw HTML.

Supported reading behavior includes:

- greentext and common inline formatting,
- quote links, quote previews, backlinks, and cross-board references,
- tap-to-reveal spoilers,
- inline images/video and multi-attachment carousels,
- collapsible posts and thread statistics,
- pull-to-refresh for new replies,
- persistent reading history and scroll-position restore,
- watched-thread notifications with quiet hours.

### Files view

Files shows the thread's attachments as a media grid. It is useful for quickly scanning or opening
media without walking the reply tree.

### Save links

Use **Save links** from the thread actions to export the thread's external links. Orbin:

- gathers the links found in thread posts,
- removes duplicates,
- writes one URL per line to a plain-text file,
- saves the file in the configured **Saved media folder**.

The default location is `Downloads/Orbin`. The export is plaintext and may contain sensitive URLs,
so treat it like any other unencrypted note or text file.

### Downloads

Thread media can be downloaded through Orbin's native download path. The saved-media hierarchy can
be configured as flat, by board, by thread, or by board then thread.

## Gallery and media

Opening an attachment launches the media viewer with image zoom, video playback, swipe navigation,
and background preloading. Returning from the viewer keeps the originating grid synchronized with
the media you were actually viewing.

### All media

**All media** sweeps board catalogs and fills a single continuous wall of discovered images and
videos. Failed boards are reported so a partial scan is not mistaken for a complete result.

### Deep scan

Catalogs mostly expose opening-post media. **Deep scan** optionally walks threads to discover reply
attachments too. It is intentionally slower and more network-intensive, so use it when completeness
matters more than speed.

## Search and filtering

Orbin supports feed filtering, board/content search, saved searches, media-type filtering, hidden
and muted tags, and poster-oriented filtering where supported by the current settings.

Recent-search history is local and controlled by its privacy setting.

## Providers

Orbin currently ships Vichan/4chan-compatible and LynxChan providers. BBW Chan uses the LynxChan
implementation.

Provider-specific JSON and URL quirks are normalized before data reaches the rest of the app. For
example, LynxChan handling tolerates absolute/relative media paths, missing paths, inactive boards,
and catalog timestamp variations.

If a provider fails, Orbin attempts to surface a meaningful failure category rather than presenting
an empty catalog as valid data.

## Accessibility and small screens

The interface is tested at normal, large, and maximum text scales. Grid cards are deliberately wider
than older versions allowed, which trades a little density for significantly better subject and
metadata readability on small phones.

Orbin also provides screen-reader semantics for major state and controls, large touch targets, and
contrast-aware themes.

## Privacy at a glance

- Local database content is encrypted with SQLCipher.
- Preferences use encrypted storage protected by Android Keystore material.
- Optional biometric app lock protects access and recents previews.
- Application networking is HTTPS-only.
- DNS uses encrypted resolvers where possible and reports fallback when a network blocks them.
- Android cloud backup/device transfer of private Orbin state is disabled.
- Explicit backup exports and saved-link text files are **not encrypted**.

See [[Settings Guide|Settings-Guide]] for configuration details and [[Troubleshooting]] for common
questions.
