# Authentication and recovery

Orbin reads public imageboards without an Orbin account or an imageboard login. Site-issued
POWBlock and terms cookies clear browsing gates; they do not identify an Orbin user. Android
holds these cookies in memory for the process lifetime. iOS delegates cookies to the system
networking stack, so Android's process-only guarantee does not apply there.

## App lock and local encryption

The app lock controls the screen, separately from encryption of local data. Android's unlock
key requires a fresh strong biometric operation; its data-encryption key deliberately does not,
so startup and background workers can run. iOS uses device-owner authentication, including the
device passcode.

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
