# Authentication and recovery

Orbin reads public imageboards without an Orbin account or an imageboard login. Site-issued
POWBlock and terms cookies clear browsing gates; they do not identify an Orbin user. Android
holds these cookies in memory for the process lifetime. iOS delegates cookies to the system
networking stack, so Android's process-only guarantee does not apply there.

## Optional WebDAV sync

Sync sends HTTP Basic authentication over HTTPS to the folder the reader configures. The server
validates the username and password. There are no access tokens, refresh tokens or token refresh
endpoints. Prefer a dedicated app password with access only to the sync folder. To revoke a
password, revoke it at the server; clearing it in Orbin only stops this installation using it.

Android stores the login in its Keystore-encrypted preferences. iOS stores the password in
Keychain with `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`: it can support background sync
after the first device unlock and does not migrate to another device. The folder and username
remain in preferences. During upgrade, the legacy preference password is removed only after a
successful Keychain write. Failed reads and writes surface an error rather than falling back to
the old copy. An empty password disables sync, including after an interrupted migration.

The sync document contains reading history and bookmarks as JSON. HTTPS protects transport;
the server operator and anyone granted access to that file can read it. It is not end-to-end
encrypted. Changing a local password does not remove existing server files or other devices' copies.

## App lock and local encryption

The app lock controls the screen, separately from encryption of local data. Android's unlock
key requires a fresh strong biometric operation; its data-encryption key deliberately does not,
so startup and background workers can run. iOS uses device-owner authentication, including the
device passcode. Locking Orbin does not revoke WebDAV credentials or stop background sync.

Android loads settings and readiness together before showing content. Unreadable or empty
encrypted settings recover with biometric lock enabled and onboarding complete, so neither a
default lock setting nor onboarding can bypass authentication. A settings-specific Keystore
marker also makes a missing settings file recover locked after initialization. Healthy legacy
settings establish that marker on upgrade; an existing app-lock key also identifies a locked
legacy installation. The database key alone is not used as a marker, because creating a fresh
database must not lock a new installation before onboarding.

If settings cannot be recovered and strong biometrics are unavailable, there is no unauthenticated
continue action. Clearing the app's storage through Android settings resets the installation and
deletes its private local data. Keep any plaintext exports somewhere trusted. This app lock does
not protect against an attacker who controls the running process or the device OS.

## Historical database keys

Version codes 102/103 could create a SQLCipher database with a zeroed passphrase. Current code
rekeys those databases to a random passphrase when opening them and preserves their contents.
The old key must fail after repair. Updating cannot repair historical database copies or backups
that remain elsewhere; treat those copies as exposed and delete them where possible.
