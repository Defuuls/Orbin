# Orbin privacy policy

Orbin is a reader for public imageboards. It has no accounts, no analytics, no advertising and no
tracking, and the developer runs no server that receives your data.

## What stays on your device

Your followed boards, bookmarks and watched threads, reading history and read positions, saved
threads and pages, downloads and settings are stored only on your device. The app's database and
settings are encrypted at rest. You can delete them at any time from Settings (Clear local
activity, Clear image cache), or by uninstalling the app.

## Who the app talks to

- **The imageboards you browse.** Opening a board, thread or file requests it from that site, as a
  browser would. Those sites see your IP address and the request, under their own privacy policies.
- **Encrypted DNS.** Site names are looked up over DNS-over-HTTPS (Cloudflare, Google or OpenDNS),
  so your network can't see which sites you open; that resolver sees the names looked up.
- **Thread sync (optional, off unless you set it up).** If you enter a WebDAV folder, your read
  threads, read positions and bookmarks are written as a file to that folder on the server you
  chose, over HTTPS, with the username and password you gave. The password is kept on your device.
- **Updates (Android only).** The Android app checks GitHub for new releases.

Nothing is shared with the developer or any other third party.

## Exports

"Export data" and "Save page" write files you choose where to keep or share. What happens to them
afterwards is up to you.

## Children

Orbin is not directed at children and shows content posted publicly by others on third-party sites.

## Contact

Questions or concerns: open an issue at https://github.com/Defuuls/Orbin/issues.

This policy may change with the app; changes are recorded in this file's history.
