# Troubleshooting

Common questions and fixes, split into in-app behavior and build problems. If your issue isn't
here, check the [CHANGELOG](https://github.com/Defuuls/Orbin/blob/main/CHANGELOG.md) — many
"bugs" are intentional changes from a recent release — or open an issue.

## In-app behavior

### "Where did the Bookmarks tab go?"
Moved, not removed. Since **v33**, bookmarks live in a **Bookmarks tab inside the Gallery
view**. The watch toggle, unread badges, and remove actions all carried over.

### "Where did the Search tab go?"
From **v34**, the dedicated Search tab is replaced by a search bar built into the subscribed
feed — type at the top of the Feed tab to filter your subscribed threads.

### "The gallery board picker doesn't show all boards"
Intentional since **v33**: the picker offers only boards you **subscribe to**, matching the
feed. It also honours **Hide NSFW boards** — if a board is missing, check
Settings → Content → Subscriptions and the NSFW setting.

### "Feed search finds nothing"
Feed search filters your **subscribed** threads only — it is not a provider-wide search. If
nothing matches you'll see "No subscribed threads match your search"; clear the query with the
✕ button.

### "Full-screen feed still shows bars / white strips / board headers"
This mode was refined across three releases: v31 fixed the status/navigation bars not hiding
and the white inset strips at the top and bottom; v32 removed the pinned board headers so
nothing stays fixed. If you see any of these, update — you're on v30 or v31.

### "Videos re-download every time I watch them"
Fixed in **v30**: video is now cached through Media3 and static media no longer sends no-cache
headers. Update if you're on an older version.

### "My history/bookmarks/subscriptions disappeared after an update"
The **v24.0** update encrypted all local data at rest, which changed the on-disk format and
performed a **one-time reset** of history, bookmarks, downloads, and settings (including
favorites and subscriptions). This was a one-off migration; later updates keep your data.

### "The biometric unlock prompt hangs"
Fixed in **v23.8**: stale prompts are cancelled on background/destruction, stale callbacks are
ignored, and stuck unlock attempts time out. Also, the notification-permission dialog no longer
races the biometric prompt. If Android's authentication is unavailable entirely, the app fails
closed and offers a recovery path.

### "Boards/threads won't load on 4chan"
Fixed in **v23.1**: the API sometimes sends numeric media IDs (`tim`), which older versions
rejected. Update to v23.1 or later.

### "How do I update the app?"
Signed APKs are published on the
[Releases page](https://github.com/Defuuls/Orbin/releases) with SHA-256 checksums. From v34,
the **Internal updater** setting (on by default) lets Orbin check for updates inside the app;
turn it off under Settings → Network & privacy if you prefer manual updates.

### "Where do downloads and exported thread links go?"
To your **Saved media folder** (Settings → Storage), which defaults to `Downloads/Orbin`.
Since v25.2.1, exported thread links follow the same folder setting.

## Building from source

### `SDK location not found`
Set `ANDROID_HOME`, or create `local.properties` with `sdk.dir=/path/to/Android/sdk`. You need
platform **API 36** installed.

### Configuration-cache errors after editing build logic
Run once with `--no-configuration-cache`, or delete `.gradle/configuration-cache`.

### KSP/Hilt stale generation
`./gradlew clean`, then rebuild.

### Build fails on warnings
CI sets `orbin.warningsAsErrors=true`. Either fix the warning or unset the property locally.

### `assembleRelease` without signing secrets
Works by design: when the `ORBIN_KEYSTORE_*` environment variables are absent, the release
build falls back to the **debug** signing config. Real releases are signed in CI from
repository secrets — see the [[Developer Guide|Developer-Guide]].

### Toolchain mismatches after the AGP 9 upgrade (July 2026)
The build now requires **JDK 17**, **Gradle 9.4.1**, **AGP 9.2.1**, and **Kotlin 2.2.21**.
Older Android Studio versions or a stale Gradle daemon can produce confusing sync errors —
update Studio, then `./gradlew --stop` and re-sync. Component-specific pitfalls (KSP, Room,
Hilt, Roborazzi version floors) are documented in
[`docs/agp-9-upgrade.md`](https://github.com/Defuuls/Orbin/blob/main/docs/agp-9-upgrade.md).
