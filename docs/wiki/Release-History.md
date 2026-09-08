# Release History

Orbin ships signed, tag-driven releases. The **current full-client release is v128 — Rei**.

For the complete chronological record, use:

- [CHANGELOG.md](https://github.com/Defuuls/Orbin/blob/main/CHANGELOG.md)
- [GitHub Releases](https://github.com/Defuuls/Orbin/releases)

This wiki page is an **era guide and narrative**, not a second changelog. Keeping hundreds of release
entries synchronized in two places creates documentation drift, so the repository CHANGELOG is the
single detailed history.

## Current era: contagious diseases

From **v129**, full Orbin releases are named after **highly contagious diseases**, hardcoded in
[`release/codenames.txt`](https://github.com/Defuuls/Orbin/blob/main/release/codenames.txt). The
Japanese-name era ran **v100 — Sakura** through **v128 — Rei**.

Orbin Minimal uses a separate pool (terminal clinical conditions from its own v17). Names must not be
reused across the two applications.

The current release, **v128 — Rei**, closes the Japanese-name era on the modern architecture/UI
generation: modular Clean Architecture, the app-agnostic `ui-next` seam, encrypted local persistence,
provider contracts, privacy-safe provider diagnostics, comprehensive CI gates, and adaptive Compose
UI. Recent ships expanded Color theme onto the Next shell (Yotsuba, Warosu, Miku, Penumbra, Royal,
Lain, and friends), widened Mild→Wild media sizing, and polished Feed / All Media / Command chrome.

Changes merged after v128 are documented under the CHANGELOG's Unreleased section until the next
release is cut (expected first disease-era tag: **v129 — Measles**, unless another unused pool name
is chosen).

## Release eras

| Range | Theme | Notes |
| --- | --- | --- |
| v129+ | Highly contagious diseases | Current naming era; pool in `release/codenames.txt` |
| v100–v128 | Popular Japanese female names | Closed at v128 — Rei |
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
