# Security Policy

## Supported versions

Orbin ships one continuous line of signed releases — only the
[latest release](https://github.com/Defuuls/Orbin/releases/latest) is supported. There are no
maintained long-term-support branches.

## Reporting a vulnerability

Please report a vulnerability privately through
[GitHub's private vulnerability reporting](https://github.com/Defuuls/Orbin/security/advisories/new)
rather than a public issue, so a fix can ship before the details are public.

## Platform security

Both apps send their own requests over HTTPS only and resolve names over DNS-over-HTTPS
(Cloudflare by default, or Google, OpenDNS or NextDNS). [PRIVACY.md](PRIVACY.md) lists every host
the apps contact.

**Android.** The local database (history, bookmarks, downloads, saved searches) is encrypted with
SQLCipher, and settings with an encrypted DataStore, both backed by a hardware-backed Android
Keystore key. Privately saved files and crash reports are stored encrypted. If encrypted DNS is
blocked the app falls back to system DNS and says so, unless Strict encrypted DNS is on. The
optional app lock gates a Keystore key with strong biometrics or the device credential. Updates
are installed only after the download matches the release checksum and is a newer Orbin signed
with the installed build's key.

**iOS.** The local database is unencrypted SQLite in Application Support, protected by iOS Data
Protection (`NSFileProtectionCompleteUntilFirstUserAuthentication`) and excluded from iCloud
backup. Settings live in a plaintext DataStore file under the same directory protection. Crash
reports are written to files with `NSFileProtectionComplete`. The app's own requests always
require encrypted DNS, with no fallback. The optional app lock uses Face ID, Touch ID or the
device passcode, and a this-device-only Keychain item whose access control requires user presence.

Neither platform sends telemetry. Explicit backup exports are unencrypted files the user chooses
where to keep.

## Known limits

These are deliberate trade-offs, stated so nobody has to discover them.

- **Traffic outside the app's network stack.** The "All of 4chan" search runs in a system web
  view on both platforms, and iOS plays and previews video, and continues saves in the
  background, with system components. None of these use the app's encrypted DNS, User-Agent or
  cookie handling. Strict encrypted DNS turns all of them off.
- **Encrypted DNS hides lookups, not connections.** The network still sees the addresses
  connected to, and usually the site name in the TLS handshake.
- **App lock is an access gate, not encryption.** The data keys are not bound to user
  authentication, so background work can run. On a rooted or compromised device that is unlocked,
  the lock does not protect stored data.
- **Android's streaming video cache is not encrypted.** It is emptied when leaving the app with
  App lock on, and can be sized to zero.
- **iOS saved files are visible in the Files app** unless private saved files is on.
- **iOS bundles a prebuilt FFmpeg** to convert WebM for Photos. It is pinned by checksum, limited
  to WebM's container and codecs, and reviewed on the schedule in
  `iosApp/Packages/OrbinFFmpegNative/README.md`.

## Verifying a release

Each Android release carries a `.sha256` checksum and a build provenance attestation:

```bash
sha256sum -c orbin-<tag>.apk.sha256
```

```bash
gh attestation verify orbin-<tag>.apk --repo Defuuls/Orbin
```

The first shows the download is intact; the second shows it was built by this repository's
release workflow.

## Scope

In scope: the Orbin Android and iOS apps in this repository and their published releases.
Out of scope: the third-party imageboard services and DNS resolvers Orbin connects to.
