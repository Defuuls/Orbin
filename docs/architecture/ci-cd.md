# CI/CD

Orbin uses GitHub Actions for continuous integration, automated tag-driven releases, and
publishing its wiki and landing page. All workflows live in
[`.github/workflows/`](https://github.com/Defuuls/Orbin/tree/main/.github/workflows).

## Workflows

### `ci.yml` — on every push to `main` and every PR
Three jobs, the last gated on the first two. JDK 17 + Gradle setup is shared via the composite
action [`.github/actions/setup-jdk-gradle`](../../.github/actions/setup-jdk-gradle/action.yml)
(jobs still set up independently — Actions isolation — but the steps stay DRY and the Gradle
action's cache is shared across jobs on the same runner image).
1. **static-analysis** — architecture/repo checks, `ktlintCheck` + `detekt` + Android Lint, plus
   an advice-only `./gradlew buildHealth` (dependency-analysis; `continue-on-error`).
2. **unit-tests** — `./gradlew test -Porbin.warningsAsErrors=true`, uploads HTML test reports.
3. **build-debug** — assembles the debug APK and compiles (but does not run) instrumentation
   test sources, then uploads the APK as an artifact.

Runs are cancelled when superseded on the same ref (`concurrency`).

### `instrumentation.yml` — on every push to `main` and every PR
Boots API 31 and API 35 emulators (KVM on the GitHub runner) and runs `connectedDebugAndroidTest` for the
modules that have `androidTest` sources, discovered per run. Kept separate from `ci.yml` because
an emulator boot plus a test run is minutes of wall clock.

### `codeql.yml` — scheduled and on push
A manual CodeQL setup that runs a clean Android debug build for Java/Kotlin analysis instead of
GitHub's autobuild, which does not understand this project's Gradle convention plugins.

### `screenshots.yml` — on PRs touching UI modules
Verifies Roborazzi goldens (`verifyRoborazziDebug`) for UI modules. Re-record locally with
`./gradlew recordRoborazziDebug` when intentional UI changes move the goldens; failed runs upload
a diffs artifact.

### `baseline-profile.yml` — manual (`workflow_dispatch`) and monthly
Boots a **rooted** API 35 emulator, records a baseline profile with `:benchmark`, uploads it as
the `baseline-profile` artifact, and then opens a draft PR with the result. Baseline profile
generation needs real (or rooted-emulator) hardware, so it cannot run on every push. A light
monthly schedule keeps the committed profile from rotting between intentional re-records.

The PR step needs **Settings → Actions → General → "Allow GitHub Actions to create and approve
pull requests"**. Without it that step fails and the run warns saying so; the profile is still
uploaded as an artifact, because the upload deliberately happens first — every run before that
ordering recorded a profile successfully and then threw it away.

### `performance.yml` — on PRs touching performance-sensitive paths
Compile/performance gates (including the benchmark compile gate) for changes that can affect
startup or scrolling cost. Kept separate from `ci.yml` so the main suite stays focused.

### `cut-release.yml` — on push to `main` touching `release/next.toml` (or manual)
The release cutter, driven by a manifest rather than by dispatch inputs. `release/next.toml`
describes one release — number, codename, `versionCode`, changelog sections — and merging it
to `main` is the whole procedure; see [`release/README.md`](../../release/README.md).

The workflow picks its phase from the repository state rather than from an input. If
`gradle.properties` does not yet name the manifest's version it **prepares**: applies the
edits via `scripts/prepare_release.py`, runs `scripts/validate_repo.py`, and opens the
`release/prep-v<tag>` pull request. Once that PR merges the same push re-triggers it and it
**publishes**: re-verifies the merged metadata and dispatches `release.yml`. If the tag
already exists it is a no-op.

The edits live in `scripts/prepare_release.py` rather than in the workflow, so they can be
run and reviewed locally, and so there is one implementation of them rather than one per
release. This replaced the retired `new-version.yml` workflow and the per-release `cut-<number>-<codename>.yml`
workflows: each cutter carried its own copy of the same edits, and every past cutter stayed
live on `main`, re-running on each subsequent release and failing its own "tag must not
exist" assertion. `validate_repo.py` fails if a `cut-<number>-*.yml` reappears.

### `release.yml` — on every `v*` tag (or manual dispatch with a tag name)
A single job that produces a complete, verifiable release:
1. Checks out full history (for release-note diffs).
2. On manual dispatch, creates and pushes the annotated tag itself.
3. Decodes the signing keystore from `RELEASE_KEYSTORE_BASE64`.
4. Builds a **signed** release APK (`assembleRelease`).
5. Stages the APK and the R8 `mapping.txt`, computing **SHA-256** checksums for each.
6. Generates release notes from the commit log since the previous tag.
7. Publishes a GitHub Release with the APK, mapping file, and `.sha256` checksums attached.

### `wiki-sync.yml` — on push to `main` touching `docs/wiki/**` (or manual)
Mirrors `docs/wiki/` onto the repository's GitHub wiki with `rsync --delete`. `docs/wiki` is the
source of truth — pages removed there are removed from the wiki too, and the wiki itself is
never edited directly.

### `pages.yml` — on push to `main` touching `site/**` (or manual)
Deploys the static landing page in `site/` to GitHub Pages
(https://defuuls.github.io/Orbin/).

## Required repository secrets

| Secret | Purpose |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded release keystore (`base64 -w0 orbin-release.jks`) |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Signing key alias |
| `RELEASE_KEY_PASSWORD` | Signing key password |

The app's `signingConfigs.release` reads these from environment variables
(`ORBIN_KEYSTORE_FILE`, `ORBIN_KEYSTORE_PASSWORD`, `ORBIN_KEY_ALIAS`, `ORBIN_KEY_PASSWORD`) and
falls back to the debug signing config locally when they are absent, so local `assembleRelease`
works without secrets.

## Release codenames

Codename eras change; the tag format `v<number>-<Codename>` does not:

| Range | Theme |
| --- | --- |
| v100+ | Popular Japanese female names (current) |
| v91–v99 | Pasta |
| v30–v90 | Stars / astronomical names |
| Earlier | Multiple early themes |

Orbin Minimal draws from the same naming pool on its own `minimal-v*` line. Names must not be
reused across either product. See [`docs/wiki/Release-History.md`](../wiki/Release-History.md).

Pick a name that is distinctive, short enough for a changelog heading, and — check
`git tag --list 'v*'`, not memory or an existing doc — **not already taken**.

## Cutting a release

Do **not** hand-edit `gradle.properties` / CHANGELOG and push a tag as the primary path. The
manifest-driven cutter is the source of truth:

1. Open a PR that updates [`release/next.toml`](../../release/next.toml) (number, unused codename,
   `version_code`, changelog sections). See [`release/README.md`](../../release/README.md).
2. Merge it. `cut-release.yml` opens `release/prep-v<tag>`.
3. Merge the prep PR (after `validate_repo.py` / CI). The cutter then dispatches `release.yml`.
4. Confirm the GitHub Release has the signed APK, mapping file, and `.sha256` checksums.

Manual `release.yml` / tag dispatch remains available as a fallback, but normal releases should
go through `release/next.toml`. Developer walkthrough:
[`docs/wiki/Developer-Guide.md`](../wiki/Developer-Guide.md).
