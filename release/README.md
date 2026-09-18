# Cutting a release

A release is described once, as data, in `release/next.toml`. Merging that file to `main`
is the whole procedure — the **Cut Release** workflow
([`.github/workflows/cut-release.yml`](../.github/workflows/cut-release.yml)) does the rest.

## The manifest

```toml
number       = 136          # release number; the tag becomes v136-<Codename>
codename     = "Apple"      # from release/codenames.txt [fruit] (v136+); never one already tagged
version_code = 154          # Android versionCode; must exceed the current value

# Optional. One line for the README's "What's new in <number>:" highlight.
# Omitting it REMOVES that line rather than leaving the previous release's copy,
# which is how the README came to advertise "What's new in 128" beside a current
# release of 139.
highlight = "Gallery paging survives a mid-scroll cache eviction."

# Optional. The release PR's summary bullets; derived from the version when omitted.
summary = [
  "bump Orbin to 136-Apple / versionCode 154",
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

### `RELEASE_BOT_TOKEN`

The cutter pushes the prep branch and opens the release PR with the `RELEASE_BOT_TOKEN`
secret, falling back to the workflow's own `GITHUB_TOKEN` when it is unset.

The fallback opens a PR that cannot be merged without manual help. GitHub does not start
workflow runs from events raised by `GITHUB_TOKEN` — a guard against a workflow triggering
itself — so the release PR's required checks stay *expected* and never run, and branch
protection refuses the merge with `N of N required status checks are expected`. The way out
is to dispatch CI by hand against the prep branch (`gh workflow run ci.yml --ref
release/prep-v<tag>`), which is what v140-Elderberry needed.

A personal access token with `repo` scope, or a GitHub App installation token, raises those
events as a real actor, so CI starts on its own and the PR merges normally. Set it as the
repository secret `RELEASE_BOT_TOKEN`. The cutter logs a warning on every run where it is
missing.

## Files updated automatically

`scripts/prepare_release.py` is the single implementation of the release metadata update. Every
full Orbin release updates these together:

- `gradle.properties` — `versionCode` and `versionName`
- `CHANGELOG.md` — closes the release section and updates comparison links
- `README.md` — current release label and GitHub Release link, plus the one-line
  `**What's new in <number>:**` highlight when the manifest sets `highlight`
- `docs/wiki/Home.md` — current release row and release date
- `docs/assets/orbin-hero-screenshot.svg` — the hero's machine-readable release marker, visible
  version/codename badge, and accessible description

The hero SVG deliberately carries stable `data-release-version` and `id="release-label"` markers so
the release cutter edits only the release-specific text; the illustrated UI itself remains a normal
design asset and is not regenerated on every release.

The release verifier and `scripts/validate_repo.py` both check that the README, Wiki home, and hero
SVG agree with `gradle.properties`. A stale version badge therefore blocks release publication
instead of silently shipping mismatched documentation. The same now holds for the README highlight:
`validate_repo.py` fails when a `What's new in <number>` line names anything but the current
release, and `prepare_release.py verify` fails when it disagrees with the manifest or lingers after
`highlight` is dropped.

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

- **Codename eras:** diseases for **v129–v135** (closed; kept under `[disease]`), fruits from
  **v136** (current; pick from `[fruit]` in [`release/codenames.txt`](codenames.txt)).
  `prepare_release.py` enforces the pool for the manifest's release number.
- **Codenames are never reused across either product line.** The cutter refuses a codename matching
  any existing tag; check `git tag --list` before writing one down.
- **A multi-word codename is hyphenated in the tag and versionName but displayed with spaces**, so
  `Sirius B` becomes `v48-Sirius-B` while the README/SVG display `48 — Sirius B` / `48 · Sirius B`.
- **The release title is derived from the tag, never typed** — see
  [`retitle-release.yml`](../.github/workflows/retitle-release.yml) for the same derivation applied
  to releases published under an older convention.
