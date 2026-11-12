# Security Policy

## Supported versions

Orbin ships one continuous line of signed releases — only the
[latest release](https://github.com/Defuuls/Orbin/releases/latest) is supported. There are no
maintained long-term-support branches.

## Platform security

Both the Android and iOS apps enforce HTTPS-only networking and DNS-over-HTTPS (Cloudflare by
default, or Google, OpenDNS or NextDNS), with an optional strict mode that refuses system-DNS
fallback.

**Android.** The local database (history, bookmarks, downloads, saved searches) is encrypted with
SQLCipher, and settings with an encrypted DataStore, both backed by a hardware-backed Android
Keystore key. The optional app lock gates a Keystore key with strong biometrics or the device
credential. Crash reports are stored encrypted.

**iOS.** The local database is unencrypted SQLite in Application Support, protected by iOS Data
Protection (`NSFileProtectionCompleteUntilFirstUserAuthentication`) and excluded from iCloud
backup. Settings live in a plaintext DataStore file under the same directory protection. Crash
reports are written to files with `NSFileProtectionComplete`. The optional app lock uses Face ID,
Touch ID or the device passcode, and a this-device-only Keychain item whose access control
requires user presence.

Neither platform sends telemetry. Explicit backup exports are unencrypted files the user chooses
where to keep.

## Scope

In scope: the Orbin Android and iOS apps in this repository and their published releases.
Out of scope: the third-party imageboard services and DNS resolvers Orbin connects to.
