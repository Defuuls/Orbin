# <img src="docs/assets/orbin-icon-mark-small.svg" width="28" height="28" alt="Orbin" /> Orbin

Privacy-focused, read-only **Android & iOS imageboard browser** built with Kotlin Multiplatform and Compose Multiplatform.

[Website](https://defuuls.github.io/Orbin/) · [Architecture](docs/architecture/README.md) · [Developer Guide](docs/wiki/Developer-Guide.md) · [iOS Build](docs/ios.md) · [Privacy](PRIVACY.md) · [Security](SECURITY.md) · [License](LICENSE)

**Current release:** [222 — Analcime](https://github.com/Defuuls/Orbin/releases/tag/v222-Analcime)

**What's new in 222:** Links open in Orbin's own browser on Android, iPhone and iPad.

- **Read-only browsing:** Combined multi-site feed, board directories, List/Grid/Card catalogs, and adaptive two-pane layout (`4chan` & `BBWChan`).
- **Thread & media reader:** Posts, Files, and Card views, inline quote expansion, backlinks, Coil 3 images, Media3 / AVPlayer video, and swipe gallery.
- **Privacy & security:** HTTPS-only, DNS-over-HTTPS, SQLCipher/Keystore encryption on Android, Data Protection on iOS, biometric lock, zero telemetry, no cloud backups.
- **Design:** NekoSurf-inspired layouts with Orbin identity, Light/Dark/AMOLED themes, system text sizing and adaptive tablet navigation.

## Quick Start

Requires **JDK 17+** (see [iOS Build](docs/ios.md) for Xcode):

```bash
./gradlew assembleDebug   # Build Android debug APK
bash scripts/check.sh     # Run all quality gates
```
