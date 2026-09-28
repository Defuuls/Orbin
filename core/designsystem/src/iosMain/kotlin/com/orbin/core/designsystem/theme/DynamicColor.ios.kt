package com.orbin.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * iOS has no Material You / wallpaper-derived dynamic color API equivalent to Android 12+.
 *
 * Always returns null so [OrbinTheme] and the Next shell keep the intentional eggplant brand
 * scheme (and imageboard skin seeds when selected). Do not invent a UIColor-based "dynamic"
 * palette here — brand identity on iOS is static eggplant by design, with native control chrome
 * adapted separately via [com.orbin.uinext.NextPlatform.IOS].
 */
@Composable
internal actual fun dynamicColorScheme(darkTheme: Boolean): ColorScheme? = null
