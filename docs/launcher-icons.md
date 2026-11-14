# Launcher icons

Android, iOS, the splash/launch mark, the notification icon, and the website favicon all derive from the **Midnight Electric** mark in [`orbin-icon-mark.svg`](assets/orbin-icon-mark.svg) and [`orbin-icon-mark-small.svg`](assets/orbin-icon-mark-small.svg).

The mark consists of two `>>` quote chevrons (`#5B5BFF` → `#229BFF` → `#00D4FF`) and an interlocking play spark (`#38BDF8` → `#67E8F9`) on a `#0B1021` midnight navy background.

## Android

- **Adaptive foreground (`108dp`)**: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- **Monochrome (`108dp`)**: `app/src/main/res/drawable/ic_launcher_monochrome.xml` (white silhouette for Android 13+ themed icons)
- **Adaptive background**: `@color/ic_launcher_background` (`#0B1021` in `app/src/main/res/values/colors.xml`)
- **Splash screen**: `windowSplashScreenAnimatedIcon` uses `@drawable/ic_launcher_foreground` over `@color/splash_background` (`#0B1021`)
- **Legacy PNGs**: `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher{,_round}.png`
- **Notification icon (`24dp`)**: `data/src/main/res/drawable/ic_stat_notify_thread.xml` (white silhouette of the chevrons + spark)

## iOS

- **AppIcon (`1024×1024` RGB, opaque, no alpha)**: `iosApp/Orbin/Assets.xcassets/AppIcon.appiconset/AppIcon.png`
- **LaunchMark (`80 / 160 / 240`)**: `iosApp/Orbin/Assets.xcassets/LaunchMark.imageset/`
- **Launch background**: `iosApp/Orbin/Assets.xcassets/LaunchScreenBackground.colorset/Contents.json` (`#FFFFFF` light / `#0B1021` dark)
