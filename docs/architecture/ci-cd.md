# CI/CD

Orbin uses GitHub Actions for continuous integration, automated tag-driven releases, and
publishing its wiki and landing page. All workflows live in
[`.github/workflows/`](https://github.com/Defuuls/Orbin/tree/main/.github/workflows).

## Workflows

### `ci.yml` — on every push to `main` and every PR
Three jobs, the last gated on the first two:
1. **static-analysis** — `ktlintCheck` + `detekt`, uploads reports.
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
Records Roborazzi screenshots (`recordRoborazziDebug`) and uploads them as artifacts so goldens
can be reviewed before committing. Verification (`verifyRoborazziDebug`) runs as part of the
regular unit test suite once goldens are checked in.

### `baseline-profile.yml` — manual (`workflow_dispatch`)
Boots a **rooted** API 35 emulator, records a baseline profile with `:benchmark`, uploads it as
the `baseline-profile` artifact, and then opens a draft PR with the result. Baseline profile
generation needs real (or rooted-emulator) hardware, so it cannot run on every push.

The PR step needs **Settings → Actions → General → "Allow GitHub Actions to create and approve
pull requests"**. Without it that step fails and the run warns saying so; the profile is still
uploaded as an artifact, because the upload deliberately happens first — every run before that
ordering recorded a profile successfully and then threw it away.

### `new-version.yml` — manual (`workflow_dispatch`)
Prepares a release PR from inputs (version name, `versionCode`, codename, base branch, draft
flag), bumping `app/build.gradle.kts` and `CHANGELOG.md`.

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

**From v91 onward, release codenames are types of pasta** — Bucatini, Rigatoni, Orecchiette,
Fusilli, Farfalle, Linguine, Cavatappi, Pappardelle, Conchiglie, Casarecce, Trofie, Paccheri,
Mafaldine, Strozzapreti. This replaces the star scheme, which ran from v30 and ended with
**v90 — Vega**; before that came bear families, mythical cities, rare fish and desserts. Codename
eras change; the tag format `v<number>-<Codename>` does not. See the wiki's
[[Release History|Release-History]] for the full lineage.

Pick a name that is distinctive, short enough for a changelog heading, and — check
`git tag --list 'v*'`, not memory or an existing doc — **not already taken**.

## Cutting a release

```bash
# bump orbin.versionName/orbin.versionCode in gradle.properties, update CHANGELOG.md, commit
git tag -a v67-<Codename> -m "<Codename>"
git push origin v67-<Codename>
```

The tag push triggers `release.yml`; the GitHub Release appears once the job completes. If
pushing a tag directly isn't possible, run `release.yml` via `workflow_dispatch` instead,
supplying `tag` and `tag_message` — it creates and pushes the annotated tag itself. See the
wiki's [[Developer Guide|Developer-Guide]] for the full walkthrough.
