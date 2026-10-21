# Nearby device sync

Open **Settings → Nearby devices** on both devices while connected to the same Wi-Fi (or with
**Also use Bluetooth** on, see below). Enable nearby sync and choose **Add a device** on one: it
shows the pairing code as a QR code. On the other, choose **Join another device → Scan QR code**
and point the camera at it. The textual code is still listed under the QR row: long-press to copy
it and paste it into **Join with a code instead** (iOS currently joins this way). Share the code
privately: it grants access to your activity. It expires after ten minutes and is consumed after
one successful enrollment.

## Transports

The QR code only bootstraps trust; the activity itself moves over a transport. Every transport
carries the same length-prefixed frames of the same protocol (`NearbyChannel`), so pairing,
merging, key rotation and the authenticated encryption below are identical on each.

| Transport | When | Notes |
| --- | --- | --- |
| Local network (TCP 39157) | Same Wi-Fi/Ethernet, or one phone's hotspot | Pairing, **Sync now**, automatic sync for trusted peers. Hotspot interfaces (`ap*`, `swlan*`) count as local, so two phones on mobile data can sync through one's hotspot without using data. |
| Bluetooth LE (Android) | **Also use Bluetooth** on both devices | Pairing and **Sync now** only, about 10 m. Needs Nearby devices permission (`BLUETOOTH_SCAN` with `neverForLocation`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`). Never used for automatic sync. |
| Internet | Devices far apart / mobile data | Uses the existing WebDAV sync through a folder you control. **Set up another device** shows the folder and login as a QR code for the other device to scan, see below. There is no Orbin relay server. |

A pairing code lists every route the inviter offers: its local address when it has one, and a
Bluetooth flag when Bluetooth sync is on. Wi-Fi-only codes are byte-for-byte the previous format.
A joiner uses Wi-Fi when the inviter's address is on one of its current networks and Bluetooth
otherwise. **Sync now** tries the same order for each saved device and falls back to the other.

### Bluetooth LE details

While listening, the device opens an insecure (unbonded) L2CAP connection-oriented channel and
advertises connectable BLE packets whose service data (UUID `8cd8eea8-5c1c-41dc-8dac-5c7824b81fa4`)
is a six-byte beacon followed by the two-byte PSM. A peer scans for the beacon it expects and
connects to that PSM. Bonding is not needed and no system pairing dialog appears, because every
frame is still authenticated and encrypted with the pair key. Beacons:

- an open invitation advertises `SHA-256("orbin-ble1|invite|" + selector)[0..6]`; only someone with
  the code knows the selector;
- a device advertises `SHA-256("orbin-ble1|device|" + deviceId + "|" + window)[0..6]` with a
  ten-minute window, and scanners also accept the previous window. The device id is only shared
  with paired devices, so an onlooker can't link a device's beacons across windows.

Pairings made over Bluetooth have no saved IP address until the devices also meet on a network;
they are skipped by automatic sync. iOS has no Bluetooth transport yet (`NearbySyncSecrets`
takes an optional `NearbyBluetooth`; iOS passes none) and keeps the Wi-Fi flow.

### Internet sync setup by QR code

Two devices that are never on the same network sync through WebDAV. After entering the folder and
login on one device, **Sync setup by QR code → Set up another device** shows an
`orbin-webdav1:` code (base64url JSON with the https folder, username and password). Scanning it
with **Copy setup from another device** saves the login on the second device and syncs. The QR
code contains the password: show it only to your own device's camera. A relay that works without
your own server would need hosted infrastructure; it is deliberately not part of this change.

Orbin shares reading history, last-read positions, bookmarks and watched threads. It keeps media,
settings and WebDAV credentials on the original device. Existing activity is merged; subsequent
syncs use the previous shared snapshot to propagate deletions. Keep both apps open and unlocked.
Each pairing has a **Trust for automatic sync** switch, off by default. Enable it on both devices
for automatic transfers. Every thirty seconds Orbin checks the current Wi-Fi/Ethernet interface
subnet and tries to reach trusted peers at their saved addresses. Each transfer must authenticate
with the pair key before activity is accepted. No Wi-Fi name, location permission or server is
needed. Disabling trust stops automatic transfers; **Sync now** still works manually.

Choose **Remove device** to revoke future access. Already received copies remain on the other
device. Remove an existing pairing before pairing that same device again. Turning sync off or
backgrounding/locking Orbin closes the listener, cancels transfers and discards temporary codes.

## Network requirements and limits

No account, Internet connection or hosted server is used. Devices connect directly using numeric
local IPv4 addresses on TCP port 39157. Allow Orbin local-network access on iOS. Guest networks,
access-point isolation, VPN routing or firewalls can prevent connections. IPv6-only networks and
automatic address discovery are not supported. If Wi-Fi changes a device's address, remove the
pairing on both devices and pair again. The network check uses interface netmasks, not a Wi-Fi name; reachable devices on routed but
different subnets are skipped for automatic sync. A private address alone never grants trust.
Each device supports eight pairings; each transfer is
limited to 8 MiB and 50,000 activity records.

## Implementation

`NearbySync` in `core/data` owns the foreground listener and pairing state. `NearbySockets` uses
length-prefixed Ktor sockets with bounded frame sizes, three concurrent accepted connections and
thirty-second transfer deadlines. Peers outside local address ranges are rejected. This is a
local-address restriction, not proof that a peer is physically on the same Wi-Fi.

Pairing codes contain a random selector, a 256-bit secret, address and expiry. After enrollment,
the invitation secret rotates to an independent permanent pair key. AES-GCM uses the platform
cryptography provider and fresh library-generated IVs. Fresh challenges from both endpoints plus
frame roles are authenticated as associated data to reject tampering, replay and reflection.
Pair selectors and connection timing are visible to the local network; activity and device
identities are encrypted. Anyone holding an unused pairing code can enroll, so treat it as a secret.

Android stores device identity, peer addresses and pair keys in encrypted preferences. iOS uses a
separate device-only Keychain service. Per-pair agreed activity snapshots use the existing local
preferences store. `ThreadSync` shares its existing merge engine and mutation lock with nearby
sync, avoiding overlapping local progress writes with WebDAV sync. Interrupted transfers may
need a retry; interruption during first enrollment can require removing the partial pairing and
pairing again. There is no remote wipe or recovery service.

## Trust and automatic sync

Update Orbin on both devices before using this version: incoming transfers must explicitly
identify automatic versus manual intent, and older clients without that field are rejected.

Trust is a local, persisted choice for each pairing. Both endpoints must opt in, and newly paired
or previously saved devices default to manual-only. Turning trust off keeps the pairing and its
activity but rejects automatic transfers in both directions. Removing the device also removes its
key. The automatic/manual request flag is encrypted and authenticated. Manual transfers still
require the established pair key or an unused invitation.

Current local subnets are re-read on every automatic round and checked again before the outgoing
connection and before an automatic incoming merge. Private address reuse on another Wi-Fi does
not authenticate a peer: the saved pair key must also match. This check does not discover changed
IP addresses or prove a physical Wi-Fi association. Re-pair if a saved peer address changes.
