# Troubleshooting

This page covers current Orbin behavior and common development failures. If something changed after
a release, also check the repository CHANGELOG and the latest GitHub release notes.

## In-app behavior

### Where did List view go?

List is no longer a selectable feed/catalog layout. Orbin now uses the adaptive **Grid** as the
primary thread presentation, tuned so cards stay readable on compact phones. **Images** remains as
the media-first alternative.

If an older install had List saved, Orbin treats that legacy value as Grid automatically.

### Why are there fewer columns than before?

Intentional. Very narrow cards made thread subjects and metadata difficult to read on small screens.
The current grid maintains a larger minimum cell width and gives subjects more vertical room. The
trade is slightly lower density for substantially better readability.

### Where did Bookmarks go?

Bookmarks live inside Gallery rather than as a separate bottom-navigation destination. Watch state,
unread information, and removal actions remain available there.

### Search only finds some threads

Feed search operates on the content represented by the feed rather than being an unrestricted web
search. Provider/board search capabilities can also differ by engine. Check subscriptions, content
filters, hidden tags, and media filters if expected results are missing.

### A board or catalog is empty

Orbin distinguishes a real empty result from provider/network failures where possible. Check the
offline indicator and retry. For BBW Chan/LynxChan, current builds include tolerant parsing for
inactive boards, media URL forms, missing media paths, and catalog timestamp variations.

### Where do saved thread links go?

Use **Save links** in the thread actions. Orbin deduplicates the thread's external links and writes
them as a plain-text file in the configured **Saved media folder**, defaulting to
`Downloads/Orbin`.

The file is plaintext. If the URLs are sensitive, store or delete it accordingly.

### Where do downloads go?

Downloads use the saved-media location and organization configured in Settings. Folder structure can
be flat or organized by board/thread depending on the selected option.

### Why is All media incomplete?

The normal All media sweep discovers what catalogs expose, which is primarily opening-post media.
Enable **Deep scan** if you also need attachments from replies. Deep scan walks threads and is much
slower by design.

### Why does DNS say it fell back to the system resolver?

Some networks block the configured encrypted DNS endpoint. Orbin falls back so browsing does not
fail completely and reports that privacy degradation. Switching resolver or network may clear it;
the notice clears after encrypted resolution succeeds again.

### How do I update Orbin?

Signed APKs and SHA-256 checksums are published on the repository Releases page. The in-app update
check retrieves release metadata and sends you to the release page; Orbin does not silently install
an APK.

## Privacy questions

### Is local browsing data encrypted?

The main local database is encrypted with SQLCipher and preferences use encrypted storage protected
by Android Keystore material.

### Are backups and saved-link files encrypted?

No. Explicit backup JSON and exported link text files are plaintext because they are designed for
portability. Keep them in a trusted location.

### What does provider diagnostics record?

Operational diagnostics are intentionally limited to provider, operation, duration, and outcome.
They do not record board names, thread IDs, search queries, or requested URLs.

## Building from source

### SDK location not found

Set `ANDROID_HOME`, or create `local.properties` with `sdk.dir=/path/to/Android/sdk`. Install Android
platform API 37. The app targets Android 12+ (`minSdk` 31).

### What Java version is required?

Use JDK 17. Exact Gradle, AGP, Kotlin, KSP, Room, and Hilt versions are pinned in the repository and
summarized in the [[Developer Guide|Developer-Guide]].

### Configuration-cache failure after changing build logic

Try the failing task once with `--no-configuration-cache`. If necessary, clear the local Gradle
configuration-cache directory and rerun.

### KSP/Hilt generated-code errors

Run a clean build. If the problem persists, verify the pinned toolchain rather than independently
upgrading one processor/plugin.

### CI says architecture validation failed

Do not bypass the check. Run `scripts/validate_architecture.py` and inspect the reported dependency,
cycle, or source-boundary violation. The allowed direction is documented in
[[Architecture and Modules|Architecture-and-Modules]] and `docs/architecture/quality-gates.md`.

### Screenshot verification failed after an intentional UI change

Inspect the Roborazzi diff artifact first. If the visual change is intended, record/update the
approved baselines and rerun verification. Do not weaken the screenshot assertion simply to make CI
green.

### A provider contract test failed

Treat this as a normalization bug or a deliberately changed provider invariant. Provider engines are
allowed to be messy; the rest of Orbin should not have to know that. Fix the provider mapper or, if
the contract truly needs to change, update the shared contract and all affected provider fixtures.

### Build fails on warnings

CI can treat warnings as errors. Fix the warning rather than relying on a local configuration that
allows it.

### Release build/signing questions

Local release-shaped tasks can use debug signing for development paths, while publication requires
repository release secrets. The automated cutter and signed release workflow are documented in the
[[Developer Guide|Developer-Guide]].

## Still stuck?

Check, in order:

1. the latest release notes and `CHANGELOG.md`,
2. this wiki,
3. `docs/architecture/` or `docs/provider-api/` for engineering issues,
4. existing GitHub issues,
5. then open a new issue with the provider, Android version, Orbin version, reproduction steps, and
   non-sensitive logs/diagnostics.
