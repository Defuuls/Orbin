package com.orbin.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * The platform's wallpaper-derived color scheme, or null where there is none.
 *
 * - **Android 12+**: Material You via `dynamic*ColorScheme` when the user leaves Dynamic color on.
 * - **iOS**: always null — there is no equivalent API, and the eggplant brand (plus optional
 *   imageboard skin seeds) is intentional. See the `iosMain` stub.
 */
@Composable
internal expect fun dynamicColorScheme(darkTheme: Boolean): ColorScheme?
