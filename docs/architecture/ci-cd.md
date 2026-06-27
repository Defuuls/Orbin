# CI/CD

Orbin uses GitHub Actions for continuous integration and automated, tag-driven releases.

## Workflows

### `ci.yml` — on every push to `main` and every PR
Three parallel/gated jobs:
1. **static-analysis** — `ktlintCheck` + `detekt`, uploads reports.
2. **unit-tests** — `./gradlew test`, uploads HTML test reports.
3. **build-debug** — assembles the debug APK (after the first two pass) and uploads it as an
   artifact.

Runs are cancelled when superseded on the same ref (`concurrency`).

### `release.yml` — on every `v*` tag
A single job that produces a complete, verifiable release:
1. Checks out full history (for release-note diffs).
2. Decodes the signing keystore from `RELEASE_KEYSTORE_BASE64`.
3. Builds a **signed** release APK (`assembleRelease`).
4. Stages the APK and the R8 `mapping.txt`, computing **SHA-256** checksums for each.
5. Generates release notes from the commit log since the previous tag.
6. Publishes a GitHub Release with the APK, mapping file, and `.sha256` checksums attached.

## Required repository secrets

| Secret | Purpose |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded release keystore (`base64 -w0 orbin-release.jks`) |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Signing key alias |
| `RELEASE_KEY_PASSWORD` | Key password |

The app's `signingConfigs.release` reads these from environment variables
(`ORBIN_KEYSTORE_FILE`, `ORBIN_KEYSTORE_PASSWORD`, `ORBIN_KEY_ALIAS`, `ORBIN_KEY_PASSWORD`) and
falls back to the debug signing config locally when they are absent, so local `assembleRelease`
works without secrets.

## Cutting a release

```bash
# bump versionName/versionCode in app/build.gradle.kts, update CHANGELOG.md, commit
git tag v1.2.0
git push origin v1.2.0
```

The tag push triggers `release.yml`; the GitHub Release appears once the job completes.
