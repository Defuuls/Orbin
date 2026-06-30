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
        onPrimary = OrbinOnPrimary,
        primaryContainer = OrbinPrimaryContainer,
        onPrimaryContainer = OrbinOnPrimaryContainer,
        secondary = OrbinSecondary,
        onSecondary = OrbinOnSecondary,
        secondaryContainer = OrbinSecondaryContainer,
        onSecondaryContainer = OrbinOnSecondaryContainer,
        tertiary = OrbinTertiary,
        onTertiary = OrbinOnTertiary,
        tertiaryContainer = OrbinTertiaryContainer,
        onTertiaryContainer = OrbinOnTertiaryContainer,
        error = OrbinError,
        onError = OrbinOnError,
        errorContainer = OrbinErrorContainer,
        onErrorContainer = OrbinOnErrorContainer,
        background = OrbinBackground,
        onBackground = OrbinOnBackground,
        surface = OrbinSurface,
        onSurface = OrbinOnSurface,
        surfaceVariant = OrbinSurfaceVariant,
        onSurfaceVariant = OrbinOnSurfaceVariant,
        surfaceTint = OrbinPrimary,
        inverseSurface = OrbinInverseSurface,
        inverseOnSurface = OrbinInverseOnSurface,
        inversePrimary = OrbinInversePrimary,
        outline = OrbinOutline,
        outlineVariant = OrbinOutlineVariant,
        scrim = OrbinScrim,
        surfaceContainerLowest = OrbinSurfaceContainerLowest,
        surfaceContainerLow = OrbinSurfaceContainerLow,
        surfaceContainer = OrbinSurfaceContainer,
        surfaceContainerHigh = OrbinSurfaceContainerHigh,
        surfaceContainerHighest = OrbinSurfaceContainerHighest,
    )

private val DarkColors =
    darkColorScheme(
        primary = OrbinDarkPrimary,
        onPrimary = OrbinDarkOnPrimary,
        primaryContainer = OrbinDarkPrimaryContainer,
        onPrimaryContainer = OrbinDarkOnPrimaryContainer,
        secondary = OrbinDarkSecondary,
        onSecondary = OrbinDarkOnSecondary,
        secondaryContainer = OrbinDarkSecondaryContainer,
        onSecondaryContainer = OrbinDarkOnSecondaryContainer,
        tertiary = OrbinDarkTertiary,
        onTertiary = OrbinDarkOnTertiary,
        tertiaryContainer = OrbinDarkTertiaryContainer,
        onTertiaryContainer = OrbinDarkOnTertiaryContainer,
        error = OrbinDarkError,
        onError = OrbinDarkOnError,
        errorContainer = OrbinDarkErrorContainer,
        onErrorContainer = OrbinDarkOnErrorContainer,
        background = OrbinDarkBackground,
        onBackground = OrbinDarkOnBackground,
        surface = OrbinDarkSurface,
        onSurface = OrbinDarkOnSurface,
        surfaceVariant = OrbinDarkSurfaceVariant,
        onSurfaceVariant = OrbinDarkOnSurfaceVariant,
        surfaceTint = OrbinDarkPrimary,
        inverseSurface = OrbinDarkInverseSurface,
        inverseOnSurface = OrbinDarkInverseOnSurface,
        inversePrimary = OrbinDarkInversePrimary,
        outline = OrbinDarkOutline,
        outlineVariant = OrbinDarkOutlineVariant,
        scrim = OrbinScrim,
        surfaceContainerLowest = OrbinDarkSurfaceContainerLowest,
        surfaceContainerLow = OrbinDarkSurfaceContainerLow,
        surfaceContainer = OrbinDarkSurfaceContainer,
        surfaceContainerHigh = OrbinDarkSurfaceContainerHigh,
        surfaceContainerHighest = OrbinDarkSurfaceContainerHighest,
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
                surfaceContainer = Color(0xFF111111),
                surfaceContainerHigh = Color(0xFF1A1A1A),
                surfaceContainerHighest = Color(0xFF222222),
            )
        } else {
            baseScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = orbinTypography(fontScale),
        shapes = OrbinShapes,
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
        shapes = OrbinShapes,
        content = content,
    )
}
