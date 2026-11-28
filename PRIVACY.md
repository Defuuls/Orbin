# Orbin privacy policy

Orbin is a reader for public imageboards, on Android and iOS. It has no accounts, no analytics, no
advertising and no tracking, and the developer runs no server that receives your data.

This page says what the app stores, who it contacts, and where its protections stop. Where the two
platforms differ, it says so.

## What stays on your device

Your followed boards, bookmarks and watched threads, reading history and read positions, saved
threads and pages, downloads and settings are stored on your device. You can delete them at any
time from Settings (Clear local activity, Clear image cache), or by uninstalling the app.

Sites you open in the in-app browser (Android) keep their cookies, site storage and cached pages
on your device, so a site you log into stays logged in. They are kept, unencrypted, in the
browser's own WebView directory, separate from the rest of the app, until you clear local
activity.

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

Every host the app can contact is listed here, with one deliberate exception: a page you open
from a link in the in-app browser goes to whatever site that link names, and that page can load
from other sites in turn, as it would in any browser. See "In-app browser" below.

| Host | When | What it receives |
| --- | --- | --- |
| `boards.4chan.org`, `a.4cdn.org`, `i.4cdn.org` | Browsing 4chan | Your IP address and the board, thread or file requested |
| `bbw-chan.link` | Browsing BBWChan | The same |
| `find.4chan.org` | Using "All of 4chan" search | Your IP address and your search text; see below |
| `cloudflare-dns.com`, `dns.google`, `doh.opendns.com`, `dns.nextdns.io` | Looking up site names; only the resolver you chose | The site names looked up |
| `api.github.com`, `github.com` | Android only: checking for and downloading updates | Your IP address and the request |
| `lens.google.com`, `www.bing.com`, `yandex.com`, `saucenao.com` | Only when you choose a reverse image search | The address of that image, opened in Orbin's browser or yours |
| Sites you open from a link | Android only: a link you open in the in-app browser | What any browser sends: your IP address, the page requested and that site's cookies |
| Google Safe Browsing | Android only: while the in-app browser loads a page | Shortened hashes of page addresses, not the addresses themselves |
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
  - **Strict encrypted DNS** additionally stops everything described above from using the
    device's DNS, except the in-app browser (see "In-app browser" below), which always uses it;
    turn off "Open links in the app" to keep links out of the app. The "All of 4chan" search is turned off on both platforms. On iOS, a video is
    downloaded in full by the app itself, over encrypted DNS, and then played from a temporary
    copy that is deleted when you leave it, so playback starts later and a very large video has
    to be saved instead; video previews are off, and saves run only while the app is open.
- **Updates (Android only).** The Android app checks GitHub for new releases once a day unless you
  turn that off, and downloads an update only when you ask. iOS updates come through TestFlight
  or a build you install yourself.
- **Reverse image search.** Choosing one opens the search engine you picked, in Orbin's browser
  or your own (see below), with the image's address. Nothing is sent unless you choose it.
- **In-app browser (Android).** Links open in Orbin's own browser unless you turn off "Open links
  in the app"; then they open in your default browser, as before. The in-app browser is Android's
  system WebView, run in a separate process from the reader:
  - It looks sites up with **the device's DNS, not encrypted DNS, even with Strict encrypted DNS
    on**, and identifies itself as Android WebView rather than as Orbin.
  - It keeps sites' cookies and storage so logins persist, until you clear local activity. It
    refuses cookies from sites other than the one you are on.
  - Pages cannot reach your camera, microphone, location or files, open other apps, download
    files, or load anything over unencrypted http.
  - WebView's Safe Browsing checks pages against Google's list of dangerous sites, sending Google
    shortened hashes of page addresses.

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
