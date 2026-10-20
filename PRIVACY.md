# Orbin privacy policy

Orbin is a reader for public imageboards. It has no accounts, no analytics, no advertising and no
tracking, and the developer runs no server that receives your data.

## What stays on your device

Your followed boards, bookmarks and watched threads, reading history and read positions, saved
threads and pages, downloads and settings are stored on your device unless you enable optional sync below. The app's database and
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
- **Nearby device sync (optional, off by default).** Paired devices exchange reading history,
  read positions, bookmarks and watched threads directly over your local network, encrypted with
  per-pair keys. There is no relay, account or developer server. Both apps must be open and
  unlocked. Automatic transfers require enabling trust on both devices and a current local-subnet
  check; trust is off by default. Pairing codes expire after ten minutes and should be shared
  privately. Removing a device revokes its future access; it does not erase copies it already received. Pair keys are
  kept in encrypted settings on Android and device-only Keychain on iOS. On Android, pairing codes
  can be shown and scanned as QR codes; the camera is only opened when you tap **Scan QR code**
  and frames are decoded on the device and never stored or sent. **Bluetooth (optional, off by
  default)**: with **Also use Bluetooth** on, Orbin advertises a short beacon while it is open and
  unlocked so a paired device can find it. Beacons are derived from your private device id (or an
  invitation's random selector) and rotate every ten minutes; they contain no name or activity.
  Bluetooth scanning is declared `neverForLocation` and is not used to determine location.
- **Sync setup QR code (Android, optional).** **Set up another device** shows your WebDAV folder,
  username and password as a QR code so your other device can copy the setup. It is only drawn on
  your screen; anyone who photographs it gets your WebDAV login.
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
