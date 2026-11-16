# Orbin privacy policy

Orbin is a reader for public imageboards, on Android and iOS. It has no accounts, no analytics, no
advertising and no tracking, and the developer runs no server that receives your data.

This page says what the app stores, who it contacts, and where its protections stop. Where the two
platforms differ, it says so.

## What stays on your device

Your followed boards, bookmarks and watched threads, reading history and read positions, saved
threads and pages, downloads and settings are stored on your device. You can delete them at any
time from Settings (Clear local activity, Clear image cache), or by uninstalling the app.

How that data is protected depends on the platform:

- **Android.** The database is encrypted with SQLCipher and settings with an encrypted DataStore,
  both keyed from a hardware-backed Android Keystore key. Files you save privately and crash
  reports are stored encrypted. The **streaming video cache is not encrypted**: recently watched
  videos are kept as ordinary files in the app's private cache (300 MB by default; you can set the
  size, including to zero, in Settings). With App lock on, that cache is emptied each time you
  leave the app, except for a video that is open at that moment.
- **iOS.** The database is ordinary SQLite in Application Support, with iOS Data Protection
  (`CompleteUntilFirstUserAuthentication`) and excluded from iCloud backup. Settings live in a
  DataStore file in the same directory; the app does not encrypt that file itself. Crash reports
  are written to files with complete Data Protection (`NSFileProtectionComplete`) and are also
  excluded from backup. Files you save, and threads saved as pages, are in Orbin's folder in the
  Files app unless you turn on private saved files, so they can be opened there without going
  through App lock.

Optional app lock: Android gates a Keystore key with strong biometrics or the device credential;
iOS asks for Face ID, Touch ID or the passcode and then reads a this-device-only Keychain item
that requires the same user presence. App lock controls who can open the app. It is not a second
layer of encryption: the keys that protect stored data are available to the app without the lock,
so that background work such as watched-thread updates can run.

## Who the app talks to

Every host the app can contact is listed here.

| Host | When | What it receives |
| --- | --- | --- |
| `boards.4chan.org`, `a.4cdn.org`, `i.4cdn.org` | Browsing 4chan | Your IP address and the board, thread or file requested |
| `bbw-chan.link` | Browsing BBWChan | The same |
| `find.4chan.org` | Using "All of 4chan" search | Your IP address and your search text; see below |
| `cloudflare-dns.com`, `dns.google`, `doh.opendns.com`, `dns.nextdns.io` | Looking up site names; only the resolver you chose | The site names looked up |
| `api.github.com`, `github.com` | Android only: checking for and downloading updates | Your IP address and the request |
| `lens.google.com`, `www.bing.com`, `yandex.com`, `saucenao.com` | Only when you choose a reverse image search | The address of that image, opened in your browser |
| Apple iCloud | iOS only, only if you turn on iCloud sync | See "iCloud sync" below |

- **The imageboards you browse.** Opening a board, thread or file requests it from that site, as a
  browser would. Those sites see your IP address and the request, under their own privacy policies.
  Session cookies those sites set are kept in memory on Android and in the system cookie store on
  iOS for the life of the process.
- **"All of 4chan" search.** This search loads `find.4chan.org` in a system web view, because the
  site requires a browser check. That page, and the verification service it embeds (Cloudflare),
  load as they would in a browser: with the web view's own identification rather than Orbin's,
  and with **the device's DNS, not encrypted DNS**. On Android the web view writes cookies and
  site data to disk; Orbin deletes them when the search closes, when you clear local activity,
  and at the next launch. On iOS they are kept in memory only. Searching the boards you follow
  does not use the web view.
- **Encrypted DNS.** The app's own requests look site names up over DNS-over-HTTPS, so the
  network's DNS server is not asked which sites you open; the chosen resolver sees the names
  instead. The default resolver is Cloudflare (`https://cloudflare-dns.com/dns-query`). You can
  also choose Google, OpenDNS or NextDNS. Encrypted DNS hides the lookup only: the network can
  still see the address you connect to, and usually the site name in the connection itself.
  - *Android.* If encrypted lookup fails and **Strict encrypted DNS** is off (the default), the
    app falls back to the system's DNS and Settings shows a warning. With it on, lookups fail
    instead.
  - *iOS.* The app's own requests always require encrypted DNS; there is no fallback, so on a
    network that blocks the resolver the app does not load. iOS plays video (and decodes video
    previews) with system components that use **the device's DNS** and their own identification,
    and so does a save that continues in the background.
  - **Strict encrypted DNS** additionally turns off everything described above that cannot use
    encrypted DNS: the "All of 4chan" search on both platforms and, on iOS, video streaming,
    video previews and background saves (videos you have saved still play, and saves run while
    the app is open).
- **Updates (Android only).** The Android app checks GitHub for new releases once a day unless you
  turn that off, and downloads an update only when you ask. iOS updates come through TestFlight
  or a build you install yourself.
- **Reverse image search.** Choosing one opens the search engine you picked in your browser with
  the image's address. Nothing is sent unless you choose it.

## iCloud sync (iOS, off by default)

If you turn on iCloud sync, your followed boards, bookmarks, filters, reading memory, most
settings and the files you saved are stored in **your own Apple account's private iCloud
database**. Apple operates that storage under its privacy policy; the developer cannot read it.
The fields that say what you follow and save are encrypted on your device before upload, which
makes them end-to-end encrypted when your account uses Advanced Data Protection. App lock
settings, security keys and session-only activity are never synced. Turning sync off leaves the
stored data in place; **Delete iCloud data** removes it.

Apart from the hosts above, and iCloud if you enable it, nothing is shared with the developer or
any other third party.

## Exports

"Export data" and "Save page" write unencrypted files you choose where to keep or share. What
happens to them afterwards is up to you. A saved page shows only the files stored with it; a file
that was not saved appears as a link to the site, which is contacted only if you follow it.

Crash reports stay on the device unless you export them. A report contains the app version, the
OS version, the device make and model and the error's stack trace. Web addresses are removed, but
an error message can still name a board or thread, so read a report before sharing it.

## Children

Orbin is not directed at children and shows content posted publicly by others on third-party sites.

## Contact

Questions or concerns: open an issue at https://github.com/Defuuls/Orbin/issues.

This policy may change with the app; changes are recorded in this file's history.
