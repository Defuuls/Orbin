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

/** Color scheme variant selection. Entries carrying [ChanThemeSeeds] are ported imageboard
 * skins (e.g. 8chan's themes); the rest use curated Material schemes. */
enum class ColorSchemeVariant(
    /** When non-null, the scheme is built from these imageboard palette seeds; null means a
     * curated Orbin/Tomorrow scheme selected in [OrbinTheme]. */
    val seeds: ChanThemeSeeds? = null,
) {
    ORBIN,
    TOMORROW,
    TOMORROW_NIGHT,
    AVELLANA(
        ChanThemeSeeds(
            dark = true,
            background = Color(0xFF030D13),
            surface = Color(0xFF01080C),
            surfaceAlt = Color(0xFF01080C),
            onSurface = Color(0xFFCCCCCC),
            primary = Color(0xFF00CCE4),
            primaryVariant = Color(0xFFE5F9FD),
            outline = Color(0xFF065E81),
            highlight = Color(0xFF000000),
            subject = Color(0xFF00CCE4),
        ),
    ),
    EVITA(
        ChanThemeSeeds(
            dark = false,
            background = Color(0xFFFFFFFF),
            surface = Color(0xFF74ACDF),
            surfaceAlt = Color(0xFF74ACDF),
            onSurface = Color(0xFF0E1539),
            primary = Color(0xFFFFFF40),
            primaryVariant = Color(0xFFFFFFFF),
            outline = Color(0xFF000000),
            highlight = Color(0xFFFCBF49),
            subject = Color(0xFFFFFF40),
        ),
    ),
    HISPAPERRO(
        ChanThemeSeeds(
            dark = true,
            background = Color(0xFF312901),
            surface = Color(0xFF27170C),
            surfaceAlt = Color(0xFF27170C),
            onSurface = Color(0xFFE32636),
            primary = Color(0xFFFF1414),
            primaryVariant = Color(0xFF6CE077),
            outline = Color(0xFFFF7575),
            highlight = Color(0xFF522200),
            subject = Color(0xFFFF1414),
        ),
    ),
    HISPASEXY(
        ChanThemeSeeds(
            dark = false,
            background = Color(0xFFF7F7F8),
            surface = Color(0xFFFFFFFF),
            surfaceAlt = Color(0xFFFFFFFF),
            onSurface = Color(0xFF000000),
            primary = Color(0xFFD41564),
            primaryVariant = Color(0xFF000000),
            outline = Color(0xFFF08DBD),
            highlight = Color(0xFFFFCCAA),
            subject = Color(0xFFEB176F),
        ),
    ),
    HISPITA(
        ChanThemeSeeds(
            dark = false,
            background = Color(0xFFFFFFEE),
            surface = Color(0xFFF2E1D6),
            surfaceAlt = Color(0xFFF2E1D6),
            onSurface = Color(0xFF800000),
            primary = Color(0xFFE60000),
            primaryVariant = Color(0xFF1F8F2A),
            outline = Color(0xFF880000),
            highlight = Color(0xFFFFCCAA),
            subject = Color(0xFFE60000),
        ),
    ),
    LAIN(
        ChanThemeSeeds(
            dark = true,
            background = Color(0xFF000000),
            surface = Color(0xFF000000),
            surfaceAlt = Color(0xFF000000),
            onSurface = Color(0xFFAC737F),
            primary = Color(0xFF808080),
            primaryVariant = Color(0xFFDD0000),
            outline = Color(0xFFBB7585),
            highlight = Color(0xFF9988EE),
            subject = Color(0xFFFFFFFF),
        ),
    ),
    MIKU(
        ChanThemeSeeds(
            dark = false,
            background = Color(0xFF94D8DA),
            surface = Color(0xFFB6DDDE),
            surfaceAlt = Color(0xFFB3E3E4),
            onSurface = Color(0xFF111111),
            primary = Color(0xFF004758),
            primaryVariant = Color(0xFF007367),
            outline = Color(0xFF78998C),
            highlight = Color(0xFF95D2D3),
            subject = Color(0xFF800000),
        ),
    ),
    MOEOS(
        ChanThemeSeeds(
            dark = false,
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFA9ADFF),
            surfaceAlt = Color(0xFFA9ADFF),
            onSurface = Color(0xFF111111),
            primary = Color(0xFF3F6BD8),
            primaryVariant = Color(0xFFA9ADFF),
            outline = Color(0xFF888888),
            highlight = Color(0xFFFFFFFF),
            subject = Color(0xFFCC1105),
        ),
    ),
    MOEPHEUS(
        ChanThemeSeeds(
            dark = true,
            background = Color(0xFF000000),
            surface = Color(0xFF212820),
            surfaceAlt = Color(0xFF212820),
            onSurface = Color(0xFFACACAC),
            primary = Color(0xFF4DAD2D),
            primaryVariant = Color(0xFFFFFFFF),
            outline = Color(0xFF3A4538),
            highlight = Color(0xFFFFCCAA),
            subject = Color(0xFF4DAD2D),
        ),
    ),
    PENUMBRA(
        ChanThemeSeeds(
            dark = true,
            background = Color(0xFF1D1F21),
            surface = Color(0xFF282A2E),
            surfaceAlt = Color(0xFF1D1F21),
            onSurface = Color(0xFFACACAC),
            primary = Color(0xFFFFB300),
            primaryVariant = Color(0xFFDD0000),
            outline = Color(0xFF117743),
            highlight = Color(0xFF3B162B),
            subject = Color(0xFF34ED3A),
        ),
    ),
    PENUMBRA_CLEAR(
        ChanThemeSeeds(
            dark = false,
            background = Color(0xFFCCCCCC),
            surface = Color(0xFFDDDDDD),
            surfaceAlt = Color(0xFFCCCCCC),
            onSurface = Color(0xFF000000),
            primary = Color(0xFF222222),
            primaryVariant = Color(0xFFDD0000),
            outline = Color(0xFF000000),
            highlight = Color(0xFF868686),
            subject = Color(0xFF0F0C5D),
        ),
    ),
    REDCHANIT(
        ChanThemeSeeds(
            dark = true,
            background = Color(0xFF3B4357),
            surface = Color(0xFF343C4E),
            surfaceAlt = Color(0xFF333333),
            onSurface = Color(0xFFC5C8C6),
            primary = Color(0xFFA266F1),
            primaryVariant = Color(0xFF81A2BE),
            outline = Color(0xFF6F6F6F),
            highlight = Color(0xFF7F8CA8),
            subject = Color(0xFF0F0C5D),
        ),
    ),
    ROYAL(
        ChanThemeSeeds(
            dark = true,
            background = Color(0xFF2B181E),
            surface = Color(0xFF3C212A),
            surfaceAlt = Color(0xFF472732),
            onSurface = Color(0xFFC5B184),
            primary = Color(0xFFF9C440),
            primaryVariant = Color(0xFFF9C440),
            outline = Color(0xFF888888),
            highlight = Color(0xFF3C212A),
            subject = Color(0xFFFFF394),
        ),
    ),
    SONIC3(
        ChanThemeSeeds(
            dark = false,
            background = Color(0xFFF6F6F6),
            surface = Color(0xFFF6F6F6),
            surfaceAlt = Color(0xFFF6F6F6),
            onSurface = Color(0xFF000000),
            primary = Color(0xFF000000),
            primaryVariant = Color(0xFFDD0000),
            outline = Color(0xFF000000),
            highlight = Color(0xFF000000),
            subject = Color(0xFF0F0C5D),
        ),
    ),
    VIVIAN(
        ChanThemeSeeds(
            dark = false,
            background = Color(0xFF84CA50),
            surface = Color(0xFF8872E4),
            surfaceAlt = Color(0xFFB19CD9),
            onSurface = Color(0xFF111111),
            primary = Color(0xFF3F6BD8),
            primaryVariant = Color(0xFF81A2BE),
            outline = Color(0xFF888888),
            highlight = Color(0xFF84CA50),
            subject = Color(0xFFCC1105),
        ),
    ),
    WAROSU(
        ChanThemeSeeds(
            dark = false,
            background = Color(0xFFCFECD1),
            surface = Color(0xFFD6F0DA),
            surfaceAlt = Color(0xFFCFECD1),
            onSurface = Color(0xFF333333),
            primary = Color(0xFF335588),
            primaryVariant = Color(0xFFDD0000),
            outline = Color(0xFF333333),
            highlight = Color(0xFFBBE6C2),
            subject = Color(0xFFCC1105),
        ),
    ),
    WIN95(
        ChanThemeSeeds(
            dark = false,
            background = Color(0xFFC0C0C0),
            surface = Color(0xFFC0C0C0),
            surfaceAlt = Color(0xFFEEF2FF),
            onSurface = Color(0xFF000000),
            primary = Color(0xFF222222),
            primaryVariant = Color(0xFF555555),
            outline = Color(0xFF888888),
            highlight = Color(0xFFC0C0C0),
            subject = Color(0xFF0000FF),
        ),
    ),
    YOTSUBA(
        ChanThemeSeeds(
            dark = false,
            background = Color(0xFFFED6AF),
            surface = Color(0xFFF0E0D6),
            surfaceAlt = Color(0xFFF0E0D6),
            onSurface = Color(0xFF800000),
            primary = Color(0xFF0000EE),
            primaryVariant = Color(0xFFFF0000),
            outline = Color(0xFF880000),
            highlight = Color(0xFFFFCCAA),
            subject = Color(0xFFCC1105),
        ),
    ),
    YOTSUBA_P(
        ChanThemeSeeds(
            dark = false,
            background = Color(0xFFAAA2D8),
            surface = Color(0xFFC9C4EA),
            surfaceAlt = Color(0xFFE4E4F9),
            onSurface = Color(0xFF111111),
            primary = Color(0xFF3F6BD8),
            primaryVariant = Color(0xFF81A2BE),
            outline = Color(0xFF888888),
            highlight = Color(0xFFA590CE),
            subject = Color(0xFF090910),
        ),
    ),
    YUKKURI(
        ChanThemeSeeds(
            dark = false,
            background = Color(0xFFCCFFCC),
            surface = Color(0xFFEFEFEF),
            surfaceAlt = Color(0xFFCCFFCC),
            onSurface = Color(0xFF111111),
            primary = Color(0xFF3F6BD8),
            primaryVariant = Color(0xFF81A2BE),
            outline = Color(0xFF808080),
            highlight = Color(0xFFA590CE),
            subject = Color(0xFFFF0000),
        ),
    ),
}

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
    // Ported imageboard skins define their own light/dark identity, so they override the
    // system/light/dark toggle (AMOLED still applies only when the skin itself is dark).
    val seeds = colorSchemeVariant.seeds
    val darkTheme =
        when {
            seeds != null -> seeds.dark
            themeMode == ThemeMode.SYSTEM -> isSystemInDarkTheme()
            themeMode == ThemeMode.LIGHT -> false
            else -> true
        }

    val context = LocalContext.current
    val baseScheme =
        when {
            seeds != null -> chanColorScheme(seeds)
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
