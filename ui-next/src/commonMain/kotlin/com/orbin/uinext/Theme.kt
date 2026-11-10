package com.orbin.uinext

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.orbin.core.designsystem.theme.OrbinShapes
import com.orbin.uinext.tokens.LocalNextFontFamily
import com.orbin.uinext.tokens.NextType
import com.orbin.uinext.tokens.rememberTikTokFontFamily

/**
 * The palette the entire interface is drawn from.
 *
 * Shared semantic colors mapped consistently to Material color roles.
 * Flat, matte: no alpha fills, no frosted chrome, no gradient scrims. Surface hierarchy
 * is expressed through tonal fills (raised/elevated) rather than transparency or shadows.
 *
 * Every color that carries text clears WCAG AA 4.5:1 against its paired background.
 * [accentContainer] / [onAccentContainer] are the M3 primaryContainer pair, used for
 * selected chips, nav indicators, and tonal card highlights.
 */
@Immutable
data class NextPalette(
    /** Page background — M3 `background`. Flat, matte, no gradient. */
    val background: Color,
    /** Card / list-item surface — M3 `surfaceContainerLow`. Subtle tonal lift. */
    val raised: Color,
    /** Sheet / dialog surface — M3 `surfaceContainer`. One step above raised. */
    val elevated: Color,
    /** Primary text — M3 `onBackground`. */
    val ink: Color,
    /** Secondary text — M3 `onSurfaceVariant`. */
    val muted: Color,
    /** Tertiary / hint text — `onSurfaceVariant` at reduced alpha. */
    val faint: Color,
    /** Separator line — M3 `outlineVariant`. Very soft, no hard border. */
    val hairline: Color,
    /** Interactive accent — M3 `primary`. */
    val accent: Color,
    /** Soft accent fill for chips / selected rows — M3 `primaryContainer`. */
    val accentSoft: Color,
    /** Text/icons on solid [accent] fill — M3 `onPrimary`. */
    val onAccent: Color,
    /** Tonal container for selected indicators — M3 `primaryContainer`. */
    val accentContainer: Color,
    /** Text/icons inside [accentContainer] — M3 `onPrimaryContainer`. */
    val onAccentContainer: Color,
    /** Destructive / error color — WCAG AA compliant. */
    val destructive: Color,
    /** Text/icons on solid [destructive] fill — WCAG AA compliant. */
    val onDestructive: Color,
    val dark: Boolean,
    val amoled: Boolean = false,
)

enum class NextPlatform { IOS, ANDROID }

val LocalNextPlatform = staticCompositionLocalOf { NextPlatform.ANDROID }

val LocalNext = staticCompositionLocalOf { LightPalette }

private val LocalNextThemed = staticCompositionLocalOf { false }
private val LocalNextFontScale = staticCompositionLocalOf { 1f }

/** Shorthand for the palette in scope. */
val next: NextPalette
    @Composable get() = LocalNext.current

/**
 * Installs the shared reader palette in [LocalNext] and [MaterialTheme].
 *
 * Platform controls adapt to native press feedback while retaining the same semantic colors.
 * The default palette is monochrome; callers can select an optional accent family.
 *
 * Nesting: screens wrap themselves in a no-arg [NextTheme] so they render correctly in isolation
 * (tests, previews). If an outer shell already installed the theme, the no-arg call short-circuits
 * and inherits — no recomposition cost, no parameter compounding.
 */
@Composable
fun NextTheme(
    darkTheme: Boolean? = null,
    amoled: Boolean? = null,
    fontScale: Float? = null,
    palette: NextPalette? = null,
    platform: NextPlatform? = null,
    content: @Composable () -> Unit,
) {
    val inherited = LocalNext.current.takeIf { LocalNextThemed.current }
    val inheritedScale = LocalNextFontScale.current

    val noOverrides = darkTheme == null && amoled == null && fontScale == null && palette == null && platform == null
    if (inherited != null && noOverrides) {
        content()
        return
    }

    val fontFamily = rememberTikTokFontFamily()
    val dark = darkTheme ?: palette?.dark ?: inherited?.dark ?: isSystemInDarkTheme()
    val black = amoled ?: palette?.amoled ?: inherited?.amoled ?: false
    val scale = fontScale ?: inheritedScale

    val effectivePalette =
        palette ?: inherited?.takeIf { it.dark == dark && it.amoled == (dark && black) }

    val fallbackPalette =
        effectivePalette ?: when {
            dark && black -> AmoledPalette
            dark -> DarkPalette
            else -> LightPalette
        }

    // Every Material role comes from the one palette: roles left unset fell back to Material's
    // baseline purple, which is where stray purple bars and buttons came from.
    val m3Scheme = materialColorScheme(fallbackPalette, dark, black)

    // Explicit palettes must use the same effective ground as Material components. Otherwise
    // an AMOLED override blackens Material backgrounds but screens reading `next` ignore it.
    val resolvedPalette =
        effectivePalette?.copy(
            background = m3Scheme.background,
            dark = dark,
            amoled = dark && black,
        ) ?: fallbackPalette.copy(
            background = m3Scheme.background,
            dark = dark,
            amoled = dark && black,
        )

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalNextFontFamily provides fontFamily,
        LocalNextPlatform provides (platform ?: LocalNextPlatform.current),
        LocalNext provides resolvedPalette,
        LocalNextThemed provides true,
        LocalNextFontScale provides scale,
        LocalDensity provides Density(density.density, density.fontScale * scale / inheritedScale),
    ) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = NextType.material,
            shapes = OrbinShapes,
        ) {
            // Press: highlight flash on iOS (HIG list-row feel); Material ripple on Android.
            val indication =
                if (LocalNextPlatform.current == NextPlatform.IOS) {
                    NextHighlightIndication
                } else {
                    ripple(color = resolvedPalette.accent)
                }
            CompositionLocalProvider(
                LocalIndication provides indication,
            ) {
                content()
            }
        }
    }
}

/** A Material color scheme with every role a component reads taken from [palette]. */
internal fun materialColorScheme(
    palette: NextPalette,
    dark: Boolean,
    black: Boolean,
): ColorScheme {
    val background = if (dark && black) Color.Black else palette.background
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        primaryContainer = palette.accentContainer,
        onPrimaryContainer = palette.onAccentContainer,
        inversePrimary = palette.accentSoft,
        secondary = palette.accent,
        onSecondary = palette.onAccent,
        secondaryContainer = palette.accentContainer,
        onSecondaryContainer = palette.onAccentContainer,
        tertiary = palette.accent,
        onTertiary = palette.onAccent,
        tertiaryContainer = palette.accentContainer,
        onTertiaryContainer = palette.onAccentContainer,
        error = palette.destructive,
        onError = palette.onDestructive,
        background = background,
        onBackground = palette.ink,
        surface = palette.raised,
        onSurface = palette.ink,
        surfaceVariant = palette.elevated,
        onSurfaceVariant = palette.muted,
        surfaceTint = palette.accent,
        surfaceBright = palette.elevated,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = palette.raised,
        surfaceContainer = palette.elevated,
        surfaceContainerHigh = palette.elevated,
        surfaceContainerHighest = palette.elevated,
        outline = palette.muted,
        outlineVariant = palette.hairline,
        inverseSurface = palette.ink,
        inverseOnSurface = background,
        scrim = Color.Black,
    )
}

// ── Board hue system ───────────────────────────────────────────────────────

/** One board colour: light ground and dark ground values. */
@Immutable
internal data class BoardHue(
    val light: Color,
    val dark: Color,
)

/**
 * Ten board hues — all clear 4.5:1 AA contrast on their respective grounds.
 * `PaletteContrastTest` verifies all twenty values.
 */
internal val BoardHues =
    listOf(
        BoardHue(light = Color(0xFF255EB0), dark = Color(0xFF74A9F8)), // cobalt blue
        BoardHue(light = Color(0xFF855A04), dark = Color(0xFFE9B54C)), // signal amber
        BoardHue(light = Color(0xFF166E4C), dark = Color(0xFF5FC79A)), // phosphor green
        BoardHue(light = Color(0xFF6D45C0), dark = Color(0xFFB18CF0)), // ultraviolet
        BoardHue(light = Color(0xFFB83A6E), dark = Color(0xFFEE87B4)), // laser magenta
        BoardHue(light = Color(0xFF116C74), dark = Color(0xFF5CC6D0)), // telemetry cyan
        BoardHue(light = Color(0xFFA6491F), dark = Color(0xFFEE9468)), // international rust
        BoardHue(light = Color(0xFF4A54C6), dark = Color(0xFF93A0F5)), // deep indigo
        BoardHue(light = Color(0xFF5F6F14), dark = Color(0xFFB6CB55)), // tactical olive
        BoardHue(light = Color(0xFF8C3A8C), dark = Color(0xFFD98BD9)), // anodized plum
    )

private val PinnedBoardHues =
    mapOf("/g/" to 0, "/ck/" to 1, "/p/" to 2, "/lit/" to 3, "/aco/" to 4)

internal fun boardHueIndex(board: String): Int = PinnedBoardHues[board] ?: board.hashCode().mod(BoardHues.size)

/** A board's accent color, contrast-safe on the current ground. */
@Composable
fun boardHue(board: String): Color {
    val hue = BoardHues[boardHueIndex(board)]
    return if (next.dark) hue.dark else hue.light
}

/**
 * Placeholder artwork for unloaded thumbnails.
 *
 * Industrial anodized slate and brushed titanium gradients varied by position.
 */
@Composable
fun placeholderArt(seed: Int): Brush {
    val dark = next.dark
    val pairs =
        if (dark) {
            listOf(
                Color(0xFF1C2029) to Color(0xFF12151C),
                Color(0xFF231C1A) to Color(0xFF161211),
                Color(0xFF1A2226) to Color(0xFF11171A),
                Color(0xFF20221C) to Color(0xFF141612),
                Color(0xFF221E26) to Color(0xFF151318),
            )
        } else {
            listOf(
                Color(0xFFE4E1D8) to Color(0xFFD5D1C7),
                Color(0xFFE8DDD5) to Color(0xFFD8C8BD),
                Color(0xFFDCE2E4) to Color(0xFFC8D1D4),
                Color(0xFFE0E2D8) to Color(0xFFCFD2C4),
                Color(0xFFE5DFD5) to Color(0xFFD4CCC0),
            )
        }
    val (start, end) = pairs[((seed % pairs.size) + pairs.size) % pairs.size]
    return Brush.linearGradient(listOf(start, end))
}
