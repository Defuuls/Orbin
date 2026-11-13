package com.orbin.ios

import androidx.compose.runtime.Composable

/**
 * Multiplier for [com.orbin.uinext.NextTheme] fontScale on this platform.
 *
 * Android's shell leaves NextTheme at 1f and relies on Compose [androidx.compose.ui.unit.Density]
 * already reflecting the system font scale. The iOS actual corrects Compose's ambient scale
 * to the intended UIKit content-size category, and refreshes when that category changes.
 */
@Composable
internal expect fun rememberSystemFontScale(): Float
