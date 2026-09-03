# Cutting a release

A release is described once, as data, in `release/next.toml`. Merging that file to `main`
is the whole procedure — the **Cut Release** workflow
([`.github/workflows/cut-release.yml`](../.github/workflows/cut-release.yml)) does the rest.

## The manifest

```toml
number       = 121          # release number; the tag becomes v121-Yuki
codename     = "Yuki"       # a popular Japanese female name, never one already tagged
version_code = 139          # Android versionCode; must exceed the current value

# Optional. The release PR's summary bullets; derived from the version when omitted.
summary = [
  "bump Orbin to 121-Yuki / versionCode 139",
  "restore gallery paging on low-memory devices",
]

# Required. Keep a Changelog sections, rendered in the order written here.
[changelog]
Fixed = [
  "Restored gallery paging after the media cache evicts a page mid-scroll.",
]
Reliability = [
  "Added regression coverage for gallery paging across cache eviction.",
]
```

## What happens

The workflow runs on every push to `main` that touches the manifest, `gradle.properties`,
`scripts/prepare_release.py`, or itself, and picks its phase from the repository state:

| State | Phase | Effect |
| --- | --- | --- |
| `gradle.properties` does not name the manifest's version | **prepare** | Applies all synchronized release edits, runs `scripts/validate_repo.py`, and opens the `release/prep-v<tag>` pull request. |
| It does, and the tag does not exist | **publish** | Re-verifies the merged metadata, then dispatches `release.yml` to build, sign and publish. |
| The tag exists | **done** | No-op. The manifest can stay on `main` until the next release replaces it. |

So the sequence is: open a PR adding `release/next.toml` → merge it → the cutter opens the
release PR → merge that → the cutter dispatches the signed build.

## Files updated automatically

`scripts/prepare_release.py` is the single implementation of the release metadata update. Every
full Orbin release updates these together:

- `gradle.properties` — `versionCode` and `versionName`
- `CHANGELOG.md` — closes the release section and updates comparison links
- `README.md` — current release label and GitHub Release link
- `docs/wiki/Home.md` — current release row and release date
- `docs/assets/orbin-hero-screenshot.svg` — the hero's machine-readable release marker, visible
  version/codename badge, and accessible description

The hero SVG deliberately carries stable `data-release-version` and `id="release-label"` markers so
the release cutter edits only the release-specific text; the illustrated UI itself remains a normal
design asset and is not regenerated on every release.

The release verifier and `scripts/validate_repo.py` both check that the README, Wiki home, and hero
SVG agree with `gradle.properties`. A stale version badge therefore blocks release publication
instead of silently shipping mismatched documentation.

Run and review the same logic locally:

```bash
python3 scripts/prepare_release.py plan
python3 scripts/prepare_release.py prepare
python3 scripts/prepare_release.py verify
python3 scripts/validate_repo.py
```

Dispatching **Cut Release** manually with `dry_run` applies the manifest and prints the diff without
pushing a branch, opening a PR, or dispatching a build.

## Notes

- **Codenames are never reused across either product line.** The cutter refuses a codename matching
  any existing tag; check `git tag --list` before writing one down.
- **A multi-word codename is hyphenated in the tag and versionName but displayed with spaces**, so
  `Sirius B` becomes `v48-Sirius-B` while the README/SVG display `48 — Sirius B` / `48 · Sirius B`.
- **The release title is derived from the tag, never typed** — see
  [`retitle-release.yml`](../.github/workflows/retitle-release.yml) for the same derivation applied
  to releases published under an older convention.
