# Pulse: a single-stream design prototype

An interactive, dependency-free prototype of a different way to browse an imageboard:
one continuous stream of text lines instead of tabs, cards and a bottom bar. It is a
**design reference**, not part of the Gradle build, and mock data only (no network).

Original work, written from scratch for this repository. No Orbin source was used and
no other client's code was copied (see [DESIGN_NOTES.md](DESIGN_NOTES.md) for what it
deliberately does *not* resemble).

## Ideas worth porting

- **One stream, six lenses.** Home, Watched, Feeds, Saved, History, Settings and Clips
  re-sort the same keyed lines; nothing navigates away.
- **Controls in the middle.** Hold (or right-click, or press `/`) to open a centered
  pop-up: Refresh, Back, every view and a command line. No top or bottom bars.
- **Thread becomes the stream.** Opening a thread swaps the lines for its posts, with
  quote refs, backlinks and per-view scroll memory.
- **Media inline and fullscreen.** Lazy inline viewing, a fullscreen pager with swipe,
  zoom and swipe-down dismiss.
- **Link clipboard.** Clip a thread, or any `https` link in a post, to an on-device list
  and export it as text or Markdown.
- **Pure domain modules.** State in, ordered lines out, with stable ids, so the same
  logic ports to any UI toolkit.

## Run

Needs Node 20+. The browser tests also need Python 3 with Playwright and Chrome.

```bash
npm test                 # unit tests
node build.js            # writes dist/index.html (one self-contained file)
npm run test:browser     # browser suite against src and dist
```

`dist/` is generated and not committed.
