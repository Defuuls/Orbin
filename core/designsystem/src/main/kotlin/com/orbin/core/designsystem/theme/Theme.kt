package com.orbin.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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

enum class ThemePalette { ORBIN, IOS, TOMORROW, YORUBA }

private val OrbinLightColors =
    lightColorScheme(
        primary = OrbinPrimary,
        secondary = OrbinSecondary,
        tertiary = OrbinTertiary,
        error = OrbinError,
    )

private val OrbinDarkColors =
    darkColorScheme(
        primary = OrbinPrimary,
        secondary = OrbinSecondary,
        tertiary = OrbinTertiary,
        error = OrbinError,
    )

private val IosLightColors =
    lightColorScheme(
        primary = Color(0xFF007AFF),
        secondary = Color(0xFF5856D6),
        tertiary = Color(0xFFFF9500),
        background = Color(0xFFF2F2F7),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE5E5EA),
    )

private val IosDarkColors =
    darkColorScheme(
        primary = Color(0xFF0A84FF),
        secondary = Color(0xFF5E5CE6),
        tertiary = Color(0xFFFF9F0A),
        background = Color(0xFF000000),
        surface = Color(0xFF1C1C1E),
        surfaceVariant = Color(0xFF2C2C2E),
    )

private val TomorrowLightColors =
    lightColorScheme(
        primary = Color(0xFF4271AE),
        secondary = Color(0xFF8959A8),
        tertiary = Color(0xFFF5871F),
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFF5F5F5),
        onBackground = Color(0xFF4D4D4C),
        onSurface = Color(0xFF4D4D4C),
    )

private val TomorrowDarkColors =
    darkColorScheme(
        primary = Color(0xFF81A2BE),
        secondary = Color(0xFFB294BB),
        tertiary = Color(0xFFDE935F),
        background = Color(0xFF1D1F21),
        surface = Color(0xFF282A2E),
        onBackground = Color(0xFFC5C8C6),
        onSurface = Color(0xFFC5C8C6),
    )

private val YorubaLightColors =
    lightColorScheme(
        primary = Color(0xFF006B3F),
        secondary = Color(0xFF8B1E3F),
        tertiary = Color(0xFFE3A008),
        background = Color(0xFFFFFBF1),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF3E5C3),
    )

private val YorubaDarkColors =
    darkColorScheme(
        primary = Color(0xFF6BCB99),
        secondary = Color(0xFFFF8FAB),
        tertiary = Color(0xFFFFD166),
        background = Color(0xFF16120A),
        surface = Color(0xFF211A0F),
        surfaceVariant = Color(0xFF3B2F1A),
    )

/**
 * The Orbin Material 3 theme. On Android 12+ (always true at our min SDK 35) dynamic color is
 * used when [dynamicColor] is on and the Orbin palette is selected; named palettes always use
 * their static schemes. When [amoled] is set in dark mode, backgrounds/surfaces collapse to true
 * black to save power on OLED panels.
 *
 * @param fontScale multiplies in-app text sizes (appearance setting), independent of system scale.
 */
@Composable
fun OrbinTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    palette: ThemePalette = ThemePalette.ORBIN,
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
            palette == ThemePalette.ORBIN && dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
            palette == ThemePalette.ORBIN && dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
            darkTheme -> palette.darkScheme()
            else -> palette.lightScheme()
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
    palette: ThemePalette = ThemePalette.ORBIN,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) palette.darkScheme() else palette.lightScheme(),
        typography = orbinTypography(),
        content = content,
    )
}

private fun ThemePalette.lightScheme(): ColorScheme =
    when (this) {
        ThemePalette.ORBIN -> OrbinLightColors
        ThemePalette.IOS -> IosLightColors
        ThemePalette.TOMORROW -> TomorrowLightColors
        ThemePalette.YORUBA -> YorubaLightColors
    }

private fun ThemePalette.darkScheme(): ColorScheme =
    when (this) {
        ThemePalette.ORBIN -> OrbinDarkColors
        ThemePalette.IOS -> IosDarkColors
        ThemePalette.TOMORROW -> TomorrowDarkColors
        ThemePalette.YORUBA -> YorubaDarkColors
    }
