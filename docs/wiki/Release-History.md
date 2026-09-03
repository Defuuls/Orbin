# Release History

Orbin ships signed, tag-driven releases. The **current full-client release is v121 — Yuki**.

For the complete chronological record, use:

- [CHANGELOG.md](https://github.com/Defuuls/Orbin/blob/main/CHANGELOG.md)
- [GitHub Releases](https://github.com/Defuuls/Orbin/releases)

This wiki page is an **era guide and narrative**, not a second changelog. Keeping hundreds of release
entries synchronized in two places creates documentation drift, so the repository CHANGELOG is the
single detailed history.

## Current era: Japanese names

From **v100 — Sakura**, full Orbin releases use popular Japanese female names. Orbin Minimal draws
from the same naming pool on its own `minimal-v*` release line, and names should not be reused across
the two applications.

The current release, **v121 — Yuki**, represents the modern architecture/UI generation: modular
Clean Architecture, the app-agnostic `ui-next` seam, encrypted local persistence, provider contracts,
privacy-safe provider diagnostics, comprehensive CI gates, and adaptive Compose UI.

Changes merged after v121, including the grid-only compact-screen readability work, are documented
under the CHANGELOG's unreleased/current-development section until the next release is cut.

## Release eras

| Range | Theme | Notes |
| --- | --- | --- |
| v100+ | Popular Japanese female names | Current naming era |
| v91–v99 | Pasta | Short transitional era after the star releases |
| v30–v90 | Stars and related astronomical names | Long-running middle era, with a few historical naming irregularities |
| Earlier | Multiple early themes | See CHANGELOG for the authoritative record |

## How releases work now

Full Orbin releases use tags shaped like `v<number>-<Codename>`. Release metadata is prepared through
`release/next.toml` and the Cut Release workflow, then the signed release workflow publishes the APK,
R8 mapping file, SHA-256 checksums, and generated notes.

Orbin Minimal is released independently under `minimal-v*` tags. A Minimal release does not imply a
full Orbin release and vice versa.

For the exact current process, see [[Developer Guide|Developer-Guide]].

## Why the detailed history moved to CHANGELOG

The old version of this page contained hand-maintained detail for a subset of releases while newer
releases lived only in the CHANGELOG. That made the wiki look authoritative while silently stopping
well before the current app.

The new rule is:

- **CHANGELOG.md** = complete detailed history
- **GitHub Releases** = signed artifacts and release-specific notes
- **this page** = current release pointer, naming eras, and release-system orientation

That division keeps each source useful without maintaining the same timeline three times.
