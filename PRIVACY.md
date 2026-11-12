# Orbin privacy policy

Orbin is a reader for public imageboards, on Android and iOS. It has no accounts, no analytics, no
advertising and no tracking, and the developer runs no server that receives your data.

## What stays on your device

Your followed boards, bookmarks and watched threads, reading history and read positions, saved
threads and pages, downloads and settings are stored on your device. You can delete them at any
time from Settings (Clear local activity, Clear image cache), or by uninstalling the app.

How that data is protected depends on the platform:

- **Android.** The database is encrypted with SQLCipher and settings with an encrypted DataStore,
  both keyed from a hardware-backed Android Keystore key. Crash reports are stored encrypted.
- **iOS.** The database is ordinary SQLite in Application Support, with iOS Data Protection
  (`CompleteUntilFirstUserAuthentication`) and excluded from iCloud backup. Settings live in a
  DataStore file in the same directory; the app does not encrypt that file itself. Crash reports
  are written to files with complete Data Protection (`NSFileProtectionComplete`) and are also
  excluded from backup.

Optional app lock: Android gates a Keystore key with strong biometrics or the device credential;
iOS asks for Face ID, Touch ID or the passcode and then reads a this-device-only Keychain item
that requires the same user presence.

## Who the app talks to

- **The imageboards you browse.** Opening a board, thread or file requests it from that site, as a
  browser would. Those sites see your IP address and the request, under their own privacy policies.
  Session cookies those sites set are kept in memory on Android and in the system cookie store on
  iOS for the life of the process.
- **Encrypted DNS.** Site names are looked up over DNS-over-HTTPS so your network can't see which
  sites you open; the chosen resolver sees the names looked up. The default resolver is Cloudflare
  (`https://cloudflare-dns.com/dns-query`). You can also choose Google, OpenDNS or NextDNS. If
  encrypted lookup fails and **Strict encrypted DNS** is off (the default), the app may fall back
  to the system's DNS. With it on, lookups fail instead of falling back. Android can show a
  warning when fallback is in use; iOS cannot observe that signal.
- **Updates (Android only).** The Android app checks GitHub for new releases. iOS updates come
  through TestFlight or a build you install yourself.

Nothing is shared with the developer or any other third party.

## Exports

"Export data" and "Save page" write unencrypted files you choose where to keep or share. What
happens to them afterwards is up to you. Crash reports can be saved the same way; they stay on
the device unless you export them.

## Children

Orbin is not directed at children and shows content posted publicly by others on third-party sites.

## Contact

Questions or concerns: open an issue at https://github.com/Defuuls/Orbin/issues.

This policy may change with the app; changes are recorded in this file's history.
