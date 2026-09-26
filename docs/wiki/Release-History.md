# Release History

Orbin ships signed, tag-driven releases. The **current full-client release is v135 — Rhinovirus**.

For the complete chronological record, use:

- [CHANGELOG.md](https://github.com/Defuuls/Orbin/blob/main/CHANGELOG.md)
- [GitHub Releases](https://github.com/Defuuls/Orbin/releases)

This wiki page is an **era guide and narrative**, not a second changelog. Keeping hundreds of release
entries synchronized in two places creates documentation drift, so the repository CHANGELOG is the
single detailed history.

## Current era: fruits

From **v136**, full Orbin releases are named after **fruits**, listed under `[fruit]` in
[`release/codenames.txt`](https://github.com/Defuuls/Orbin/blob/main/release/codenames.txt). The
contagious-disease era ran **v129 — Measles** through **v135 — Rhinovirus**. The Japanese-name era
ran **v100 — Sakura** through **v128 — Rei**.

Orbin Minimal uses a separate pool (terminal clinical conditions from its own v17). Names must not be
reused across the two applications.

The current release, **v135 — Rhinovirus**, closes the disease-naming era. Changes merged after v135
are documented under the CHANGELOG's Unreleased section until the next release is cut (expected first
fruit-era tag: **v136 — Apple**, unless another unused pool name is chosen).

## Release eras

| Range | Theme | Notes |
| --- | --- | --- |
| v136+ | Fruits | Current naming era; `[fruit]` pool in `release/codenames.txt` |
| v129–v135 | Highly contagious diseases | Closed at v135 — Rhinovirus; shipped names kept under `[disease]` |
| v100–v128 | Popular Japanese female names | Closed at v128 — Rei |
| v91–v99 | Pasta | Short transitional era after the star releases |
| v30–v90 | Stars and related astronomical names | Long-running middle era, with a few historical naming irregularities |
| Earlier | Multiple early themes | See CHANGELOG for the authoritative record |

## How releases work now

Full Orbin releases use tags shaped like `v<number>-<Codename>`. Release metadata is prepared through
`release/next.toml` and the Cut Release workflow, then the signed release workflow publishes the APK,
its SHA-256 checksum, and generated notes. The R8 mapping file is kept as a private workflow
artifact rather than a release download.

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
