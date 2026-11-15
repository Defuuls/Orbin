# User Guide

Orbin uses a NekoSurf-inspired interface across Android and iOS, with Orbin's name,
mark, brand accents and existing features. For earlier interfaces and shipped
versions, use [[Release History|Release-History]] or the repository CHANGELOG.

Orbin is a **read-only browser**. It browses, watches, saves, downloads, searches,
filters and exports; it does not post, reply or create threads.

## Getting started

A fresh installation opens setup. Choose appearance and privacy preferences, then
follow at least one eligible board and select Start browsing. The board follow
must finish saving before setup can complete. Follow more boards later from
Boards. An update can show the release setup and migration screen; existing
settings and follows remain the source of truth.

The primary bar opens Boards, Home, Downloads and Settings. The retained menu
also opens Watched, Search and Saved. Back returns through the current reader
hierarchy; Settings search and scope pickers close before leaving Settings.

## Home

Home combines threads from explicitly followed boards across the selected source.
A favorite does not create a follow. Sources remain separate when they use the
same board name or thread number.

Choose List, Grid or Card with the layout control. Grid is the default when no
layout has been saved. Card shows one full-width card per row, with a larger
preview; the saved preference is shared with board catalogs and survives a
relaunch. Wide windows adapt their columns, and Card remains one column within
its pane.

Pull to refresh. Existing content stays visible during refresh and when a refresh
fails; Retry repeats the real request. Read threads are de-emphasized, and source
changes retain their reading position.

Tap thread text to open the thread. Tap an attachment preview to open that media;
a spoiler reveals before it opens. Long-press a card or preview for its available
Watch, Save, Share, board and thread-video/comment actions. The chat action opens
thread videos by default; Settings can make it open comments instead.

Home previews remain bounded still thumbnails. Opening an attachment transfers
playback to the media viewer.

## Boards and catalogs

Boards has source selection, search, local board icons and explicit follow
controls. Opening a board and following it are separate actions. Catalogs keep
List, Grid and Card layouts, sort choices and paging. Catalog search filters the
loaded catalog; global Search covers followed boards.

When both window sides reach 600dp and its width reaches 800dp, a catalog can
remain beside the open thread. Resizing preserves the thread and reading state.

## Thread reader

Orbin turns post markup into native UI, including greentext, formatting, quote
links, backlinks, cross-board references and tap-to-reveal spoilers. Posts retain
their source and post identity. Reply nesting, collapse, unread navigation,
refresh and reading-position restoration remain available.

Posts, Files and Card are views of the same thread. Files shows attachments as a
grid; Card gives attachments a larger card presentation. Watch and Save operate
on the actual thread. The download action offers Videos only or Everything.

On a long Posts thread, the scroll thumb moves through the real thread and has
an accessible progress action. Short threads and Files use ordinary scrolling.

## Saved, watched and history

Saved and Watched open the stored thread records with their source, board,
thread number and available unread metadata. History records visited threads
when enabled. These collections keep their own reading anchors and open the
complete stored identity.

## Media and downloads

Opening an attachment launches the viewer at that attachment. Image zoom,
play/pause, mute, seeking, playback speed and file paging retain the selected
media's identity. Playback offers 1x, 1.25x and 2x. Autoplay, initial mute, looping
and next-clip preloading follow the app, site or board scope selected in Settings.

The viewer keeps Close and Download available. Double-tap saves the current
media; a deliberate horizontal swipe closes the viewer. Image pinch/pan and
vertical file paging remain available. Return to the reader without losing the
originating position. Download rest of thread queues the other attachments from
that thread and keeps the queue's deduplication behavior.

Downloads shows real filenames, status, progress and Retry for failed items.
Clearing download history requires confirmation and does not delete files saved
to system storage. The primary bar's badge counts the active queue.

A board's Media wall scans attachments with the existing source, board and media
filters. Deep scan can walk threads to find reply attachments; it takes more
network work than a catalog scan.

## Search and settings

Global Search covers catalogs of followed boards and keeps its selected source
while a request loads. Recent searches can be stored locally. Clear local
activity in Settings removes recorded local activity after confirmation.

Settings is one scrolling page: Orbin identity, Theme, Boards, Threads, Media,
Hidden content, Privacy, History, Storage, Backup and About. Groups appear when
the platform supplies controls. Search settings finds labels, values, choices,
section names and explanations. Changes save immediately. See
[[Settings Guide|Settings-Guide]] for the individual options and platform actions.

## Providers

Orbin ships Vichan and LynxChan engine providers (`:provider:vichan` and
`:provider:lynxchan`). Archived providers such as FoolFuuka / 4plebs are not active
providers. Provider-specific JSON and URL differences are normalized before data
reaches the reader. A provider failure remains an error with a useful reason,
rather than becoming an apparently empty catalog.

## Accessibility and themes

Light, Dark and AMOLED share the same layout and functions. AMOLED keeps black
page grounds with distinguishable card surfaces. iOS uses its native typeface.
Android's system font setting and iOS Dynamic Type apply through one scaling path; large text can wrap or stack
controls. Right-to-left layouts mirror leading content. Controls retain labeled
screen-reader actions, checked/selected state and large touch targets.

The design's source, tests and screenshot evidence are recorded separately in
[Implementation and verification](https://github.com/Defuuls/Orbin/blob/main/docs/design-system/IMPLEMENTATION.md); a design
reference alone does not establish a passing device test.

## Privacy at a glance

- **Android.** The local database uses SQLCipher. Preferences use an encrypted DataStore protected by Android Keystore material.
- **iOS.** The local database is SQLite in Application Support under iOS Data Protection. Settings are a DataStore file in the same directory and are not separately encrypted. Crash reports use complete Data Protection.
- Optional app lock uses Android strong biometrics or the device credential, or iOS Face ID, Touch ID or the passcode with a Keychain item requiring user presence.
- Remote application networking uses HTTPS. Android's video-streaming bridge uses a local loopback HTTP endpoint for the native player.
- DNS uses DNS-over-HTTPS through Cloudflare, Google, OpenDNS or NextDNS. With Strict encrypted DNS off, a lookup can fall back to system DNS. Android reports that fallback; iOS cannot observe it.
- Android cloud backup and device transfer of private Orbin state are disabled. iOS excludes Application Support from iCloud backup.
- Explicit backup exports and saved-link text files are **not encrypted**.

See [[Troubleshooting]] for common problems.
