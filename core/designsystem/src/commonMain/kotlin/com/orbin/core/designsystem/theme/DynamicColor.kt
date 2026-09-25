package com.orbin.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * The platform's wallpaper-derived color scheme, or null where there is none. Android 12+ has
 * Material You; iOS has no equivalent, so the static schemes apply there.
 */
@Composable
internal expect fun dynamicColorScheme(darkTheme: Boolean): ColorScheme?
