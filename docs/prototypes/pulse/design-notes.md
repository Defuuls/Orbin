# Pulse design notes

## Prior art surveyed (structure only, from READMEs and store listings)

| Client | Patterns noted |
| --- | --- |
| Clover / Kuroba / KurobaEx | Thread tabs, bottom navigation bar (KurobaEx), customizable colored themes, media on its own screen, bookmark groups, composite catalogs |
| Dashchan / Overchan | Extension-based multi-site clients, thread watcher, image gallery and player |
| Chanu | Grid of image thumbnails |
| Chance | Tabs, multi-column layout on large screens, gallery, tap-status-bar to scroll |
| Orbin (NekoSurf-inspired) | Bottom bar, List / Grid / Card catalogs, adaptive two-pane |

## Do not resemble

- No bottom icon bar, tab strip, drawer or thread tabs.
- No thumbnails, image grids, cards or post bubbles. Rows carry no imagery at all.
- No colored toolbar and no Material elevation. Square corners, hairline rules.
- No separate screen for a thread: it expands in place inside the stream.

## Pulse's voice: operations log

One continuous stream of typographic lines. Condensed sans titles, mono metadata,
one signal color reserved for "needs attention" (unread). Lenses (Home, Watched, Feeds,
Saved, History, Settings) re-sort the same lines in place. A prompt-style command bar
is the only persistent control.
