# Nearby device sync

Open **Settings → Nearby devices** on both devices while connected to the same Wi-Fi.
Enable nearby sync, choose **Add a device** on one, then copy its code and paste it into
**Join another device** on the other. Share the code privately: it grants access to your activity.
It expires after ten minutes and is consumed after one successful enrollment.

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
