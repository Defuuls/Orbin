# Orbin brand

Orbin's identity is **Midnight Electric**: high-contrast, calm, and night-mode native for imageboard reading on Android and iOS.

Production launcher, splash, notification, and in-app assets use the **Midnight Electric** mark (`>>` quote chevrons + interlocking play spark on `#0B1021` midnight navy) in [`docs/assets/orbin-icon-mark.svg`](assets/orbin-icon-mark.svg) and [`docs/assets/orbin-icon-mark-small.svg`](assets/orbin-icon-mark-small.svg). In-app UI color tokens are the Midnight Electric `ReaderTokens` palette (`#4338CA` light accent, `#00D4FF` dark accent, `#000000` monochrome AMOLED mode).

## Core idea

**Dim the room. Let the board fill the frame.**

Orbin is a privacy-focused, read-only Android & iOS imageboard browser built with Kotlin Multiplatform and Compose Multiplatform. The brand should feel precise, fast, and calm — not social or loud.

## Mark

**Midnight Electric (`>>` + spark).** Two quote chevrons in an electric indigo-to-cyan gradient (`#5B5BFF` → `#229BFF` → `#00D4FF`) paired with an interlocking play spark (`#38BDF8` → `#67E8F9`) on a `#0B1021` midnight navy rounded tile.

- Full mark: [`docs/assets/orbin-icon-mark.svg`](assets/orbin-icon-mark.svg) (`1024×1024`)
- Small / favicon mark: [`docs/assets/orbin-icon-mark-small.svg`](assets/orbin-icon-mark-small.svg)
- Compose vector implementation: `OrbinLogoMark` / `OrbinLogo` in `ui-next/src/commonMain/kotlin/com/orbin/uinext/`
- Android adaptive icon: `app/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, and `@color/ic_launcher_background` (`#0B1021`)
- Android notification silhouette: `data/src/main/res/drawable/ic_stat_notify_thread.xml`
- iOS assets: `iosApp/Orbin/Assets.xcassets/AppIcon.appiconset/AppIcon.png` and `LaunchMark.imageset/`

## Color system

| Mode | Ground | Surface | Primary Text | Accent |
| --- | --- | --- | --- | --- |
| **Light** | `#FFFFFF` | `#FFFFFF` / `#F5F7FB` | `#1A1A1A` | `#4338CA` |
| **Dark** | `#0B1021` | `#141B33` / `#10162E` | `#FFFFFF` | `#00D4FF` |
| **AMOLED** | `#000000` | `#0B0B0B` / `#141414` | `#FFFFFF` | `#FFFFFF` |

## Lockups

Pair `OrbinLogoMark` / `OrbinLogo` with a plain **Orbin** wordmark (`OrbinWordmark`) when space and context need the name. In immersive media chrome, the mark alone is preferred.
