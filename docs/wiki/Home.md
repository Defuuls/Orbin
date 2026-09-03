# Orbin

Orbin is a privacy-focused, open-source **Android imageboard browser** built with Kotlin, Jetpack
Compose, and Material 3. It is a **read-only client**: Orbin browses boards, catalogs, threads,
links, and media but does not post, reply, or create threads.

- **Repository:** https://github.com/Defuuls/Orbin
- **Website:** https://defuuls.github.io/Orbin/
- **Releases:** https://github.com/Defuuls/Orbin/releases
- **Changelog:** https://github.com/Defuuls/Orbin/blob/main/CHANGELOG.md
- **License:** AGPLv3

## Current status

| | |
| --- | --- |
| Current release | **v124 — Airi** (2026-09-03) |
| Platform | Android 12+ (`minSdk` 31), compile SDK 37, target SDK 36 |
| Providers | 4chan/Vichan-compatible reference provider; BBW Chan/LynxChan |
| UI | Adaptive grid-first Compose interface for phones, tablets and foldables |
| Storage | SQLCipher database + encrypted preferences |
| Release model | Signed, tag-driven GitHub releases with automated preflight |

## Highlights

- **Grid-first browsing.** Feed and board catalogs now use an adaptive readable grid as the main
  presentation. The old List option has been removed from the UI. Cards keep useful width on
  compact phones and emphasize thread subjects over secondary metadata. An Images view remains
  available for media-first browsing.
- **Multi-provider architecture.** Engine behavior lives behind `ImageBoardProvider`; Vichan and
  LynxChan ship today. Shared provider contracts validate normalized results at the registry
  boundary.
- **BBW Chan compatibility.** The LynxChan provider tolerates real-world variations including
  inactive boards, absolute and relative media paths, missing media paths, and catalog timestamp
  fallbacks.
- **Rich thread reader.** Native formatting, quote links/previews, backlinks, spoilers, inline
  media, collapsible posts, Files view, watch state, and persistent reading position.
- **Save thread links.** External links are deduplicated and exported to a plain-text file in the
  configured saved-media folder, defaulting to `Downloads/Orbin`.
- **All media + deep scan.** Browse media across board catalogs in one wall, with an optional
  thread walk for reply attachments.
- **Wide-screen two-pane layout.** On sufficiently wide displays, catalogs and threads remain
  visible together without losing navigation state.
- **Privacy and security.** Encrypted local data, Android Keystore-backed app lock, HTTPS-only
  application networking, encrypted DNS with fallback reporting, and disabled cloud backup of
  private app state.
- **Engineering guardrails.** Architecture validation, provider contract tests, screenshot tests,
  instrumentation, CodeQL, performance/build-health checks, and release preflight are merge/release
  gates rather than documentation-only promises.
- **Privacy-safe provider diagnostics.** Debug diagnostics capture provider, operation, duration,
  and outcome without recording board names, thread IDs, queries, or URLs.

## Where to start

| Page | What's in it |
| --- | --- |
| [[User Guide\|User-Guide]] | Day-to-day browsing, grid behavior, threads, media, saved links and downloads |
| [[Settings Guide\|Settings-Guide]] | Current settings categories, behavior and defaults |
| [[Troubleshooting]] | User-facing and build troubleshooting |
| [[Release History\|Release-History]] | Release eras and current-release pointer |
| [[Developer Guide\|Developer-Guide]] | Building, testing, CI, quality gates and releases |
| [[Architecture and Modules\|Architecture-and-Modules]] | Layers, `ui-next`, providers and enforced module boundaries |

## Documentation model

The Markdown files under `docs/wiki/` in the main repository are the source of truth. Changes are
reviewed like code, then `wiki-sync.yml` mirrors them to the GitHub Wiki after they reach `main`.
This keeps the public wiki reproducible and prevents the repository docs and wiki from quietly
drifting apart.
