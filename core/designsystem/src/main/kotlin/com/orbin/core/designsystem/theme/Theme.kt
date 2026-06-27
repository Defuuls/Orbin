package com.orbin.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Whether the app follows the system theme or is forced light/dark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val LightColors =
    lightColorScheme(
        primary = OrbinPrimary,
        secondary = OrbinSecondary,
        tertiary = OrbinTertiary,
        error = OrbinError,
    )

private val DarkColors =
    darkColorScheme(
        primary = OrbinPrimary,
        secondary = OrbinSecondary,
        tertiary = OrbinTertiary,
        error = OrbinError,
    )

/**
 * The Orbin Material 3 theme. On Android 12+ (always true at our min SDK 35) dynamic color is
 * used when [dynamicColor] is on; otherwise the brand schemes apply. When [amoled] is set in dark
 * mode, backgrounds/surfaces collapse to true black to save power on OLED panels.
 *
 * @param fontScale multiplies in-app text sizes (appearance setting), independent of system scale.
 */
@Composable
fun OrbinTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    amoled: Boolean = false,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

    val context = LocalContext.current
    val baseScheme =
        when {
            dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
            dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
            darkTheme -> DarkColors
            else -> LightColors
        }

    val colorScheme =
        if (amoled && darkTheme) {
            baseScheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color(0xFF0A0A0A),
            )
        } else {
            baseScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = orbinTypography(fontScale),
        content = content,
    )
}

/** Convenience for previews: a non-dynamic dark/light themed surface. */
@Composable
fun OrbinPreviewTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = orbinTypography(),
        content = content,
    )
}
