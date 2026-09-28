package com.orbin.ios

import androidx.compose.runtime.Composable

/**
 * Multiplier for [com.orbin.uinext.NextTheme] fontScale on this platform.
 *
 * Android's shell leaves NextTheme at 1f and relies on Compose [androidx.compose.ui.unit.Density]
 * already reflecting the system font scale. iOS Compose Multiplatform density stays at 1f, so the
 * iOS actual maps UIKit content-size categories into this value (and refreshes when it changes).
 */
@Composable
internal expect fun rememberSystemFontScale(): Float
