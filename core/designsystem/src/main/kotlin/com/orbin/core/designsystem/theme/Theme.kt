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

/** Color scheme variant selection. */
enum class ColorSchemeVariant { ORBIN, TOMORROW, TOMORROW_NIGHT }

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

private val TomorrowLightColors =
    lightColorScheme(
        primary = TomorrowPrimary,
        onPrimary = TomorrowOnPrimary,
        primaryContainer = TomorrowPrimaryContainer,
        onPrimaryContainer = TomorrowOnPrimaryContainer,
        secondary = TomorrowSecondary,
        onSecondary = TomorrowOnSecondary,
        secondaryContainer = TomorrowSecondaryContainer,
        onSecondaryContainer = TomorrowOnSecondaryContainer,
        tertiary = TomorrowTertiary,
        onTertiary = TomorrowOnTertiary,
        tertiaryContainer = TomorrowTertiaryContainer,
        onTertiaryContainer = TomorrowOnTertiaryContainer,
        error = TomorrowError,
        onError = TomorrowOnError,
        errorContainer = TomorrowErrorContainer,
        onErrorContainer = TomorrowOnErrorContainer,
        background = TomorrowBackground,
        onBackground = TomorrowOnBackground,
        surface = TomorrowSurface,
        onSurface = TomorrowOnSurface,
        surfaceVariant = TomorrowSurfaceVariant,
        onSurfaceVariant = TomorrowOnSurfaceVariant,
        surfaceTint = TomorrowPrimary,
        inverseSurface = TomorrowInverseSurface,
        inverseOnSurface = TomorrowInverseOnSurface,
        inversePrimary = TomorrowInversePrimary,
        outline = TomorrowOutline,
        outlineVariant = TomorrowOutlineVariant,
        scrim = TomorrowScrim,
        surfaceContainerLowest = TomorrowSurfaceContainerLowest,
        surfaceContainerLow = TomorrowSurfaceContainerLow,
        surfaceContainer = TomorrowSurfaceContainer,
        surfaceContainerHigh = TomorrowSurfaceContainerHigh,
        surfaceContainerHighest = TomorrowSurfaceContainerHighest,
    )

private val TomorrowNightDarkColors =
    darkColorScheme(
        primary = TomorrowNightPrimary,
        onPrimary = TomorrowNightOnPrimary,
        primaryContainer = TomorrowNightPrimaryContainer,
        onPrimaryContainer = TomorrowNightOnPrimaryContainer,
        secondary = TomorrowNightSecondary,
        onSecondary = TomorrowNightOnSecondary,
        secondaryContainer = TomorrowNightSecondaryContainer,
        onSecondaryContainer = TomorrowNightOnSecondaryContainer,
        tertiary = TomorrowNightTertiary,
        onTertiary = TomorrowNightOnTertiary,
        tertiaryContainer = TomorrowNightTertiaryContainer,
        onTertiaryContainer = TomorrowNightOnTertiaryContainer,
        error = TomorrowNightError,
        onError = TomorrowNightOnError,
        errorContainer = TomorrowNightErrorContainer,
        onErrorContainer = TomorrowNightOnErrorContainer,
        background = TomorrowNightBackground,
        onBackground = TomorrowNightOnBackground,
        surface = TomorrowNightSurface,
        onSurface = TomorrowNightOnSurface,
        surfaceVariant = TomorrowNightSurfaceVariant,
        onSurfaceVariant = TomorrowNightOnSurfaceVariant,
        surfaceTint = TomorrowNightPrimary,
        inverseSurface = TomorrowNightInverseSurface,
        inverseOnSurface = TomorrowNightInverseOnSurface,
        inversePrimary = TomorrowNightInversePrimary,
        outline = TomorrowNightOutline,
        outlineVariant = TomorrowNightOutlineVariant,
        scrim = TomorrowScrim,
        surfaceContainerLowest = TomorrowNightSurfaceContainerLowest,
        surfaceContainerLow = TomorrowNightSurfaceContainerLow,
        surfaceContainer = TomorrowNightSurfaceContainer,
        surfaceContainerHigh = TomorrowNightSurfaceContainerHigh,
        surfaceContainerHighest = TomorrowNightSurfaceContainerHighest,
    )

/**
 * The Orbin Material 3 theme. On Android 12+ (always true at our min SDK 35) dynamic color is
 * used when [dynamicColor] is on; otherwise the brand schemes apply. When [amoled] is set in dark
 * mode, backgrounds/surfaces collapse to true black to save power on OLED panels.
 *
 * @param fontScale multiplies in-app text sizes (appearance setting), independent of system scale.
 * @param colorSchemeVariant selects the color palette variant (Orbin, Tomorrow, Tomorrow Night).
 */
@Composable
fun OrbinTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorSchemeVariant: ColorSchemeVariant = ColorSchemeVariant.ORBIN,
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
            colorSchemeVariant == ColorSchemeVariant.TOMORROW_NIGHT -> TomorrowNightDarkColors
            colorSchemeVariant == ColorSchemeVariant.TOMORROW && darkTheme -> TomorrowNightDarkColors
            colorSchemeVariant == ColorSchemeVariant.TOMORROW && !darkTheme -> TomorrowLightColors
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
