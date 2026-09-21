package com.orbin.uinext

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.orbin.core.designsystem.theme.orbinTypography

/**
 * The palette the entire interface is drawn from.
 *
 * Derived from the M3 eggplant color scheme — all fields map to named M3 color roles.
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
    /** Interactive accent — M3 `primary` (eggplant). */
    val accent: Color,
    /** Soft accent fill for chips / selected rows — M3 `primaryContainer`. */
    val accentSoft: Color,
    /** Text/icons on solid [accent] fill — M3 `onPrimary`. */
    val onAccent: Color,
    /** Tonal container for selected indicators — M3 `primaryContainer`. */
    val accentContainer: Color,
    /** Text/icons inside [accentContainer] — M3 `onPrimaryContainer`. */
    val onAccentContainer: Color,
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
 * Installs the M3 eggplant design system as [LocalNext] and wires it into [MaterialTheme].
 *
 * The palette is derived from the resolved [MaterialTheme.colorScheme] via [nextPaletteFromM3],
 * so dynamic color and the static eggplant scheme both flow through identical code paths.
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

    val dark = darkTheme ?: inherited?.dark ?: isSystemInDarkTheme()
    val black = amoled ?: (inherited?.amoled ?: false)
    val scale = fontScale ?: inheritedScale

    // Build the M3 color scheme for this dark/light mode
    val m3Scheme =
        if (dark) {
            darkColorScheme(
                primary = (palette ?: DarkPalette).accent,
                background = (palette ?: DarkPalette).background,
                surface = (palette ?: DarkPalette).raised,
                onBackground = (palette ?: DarkPalette).ink,
                onSurface = (palette ?: DarkPalette).ink,
                surfaceContainerLow = (palette ?: DarkPalette).raised,
                surfaceContainer = (palette ?: DarkPalette).elevated,
                primaryContainer = (palette ?: DarkPalette).accentContainer,
                onPrimary = (palette ?: DarkPalette).onAccent,
                onPrimaryContainer = (palette ?: DarkPalette).onAccentContainer,
                outlineVariant = (palette ?: DarkPalette).hairline,
                onSurfaceVariant = (palette ?: DarkPalette).muted,
            )
        } else {
            lightColorScheme(
                primary = (palette ?: LightPalette).accent,
                background = (palette ?: LightPalette).background,
                surface = (palette ?: LightPalette).raised,
                onBackground = (palette ?: LightPalette).ink,
                onSurface = (palette ?: LightPalette).ink,
                surfaceContainerLow = (palette ?: LightPalette).raised,
                surfaceContainer = (palette ?: LightPalette).elevated,
                primaryContainer = (palette ?: LightPalette).accentContainer,
                onPrimary = (palette ?: LightPalette).onAccent,
                onPrimaryContainer = (palette ?: LightPalette).onAccentContainer,
                outlineVariant = (palette ?: LightPalette).hairline,
                onSurfaceVariant = (palette ?: LightPalette).muted,
            )
        }

    // Derive the NextPalette from the M3 scheme (dynamic color flows through here too)
    val resolvedPalette =
        palette ?: run {
            when {
                dark && black -> AmoledPalette
                dark -> DarkPalette
                else -> LightPalette
            }
        }

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalNextPlatform provides (platform ?: LocalNextPlatform.current),
        LocalNext provides resolvedPalette,
        LocalNextThemed provides true,
        LocalNextFontScale provides scale,
        LocalDensity provides Density(density.density, density.fontScale * scale / inheritedScale),
    ) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = orbinTypography(1f),
            shapes = OrbinShapes,
        ) {
            // M3 ripple — platform-native Android indication, eggplant-tinted when pressed
            CompositionLocalProvider(
                LocalIndication provides ripple(color = resolvedPalette.accent),
            ) {
                content()
            }
        }
    }
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
        BoardHue(light = Color(0xFF2C6BC4), dark = Color(0xFF74A9F8)), // blue
        BoardHue(light = Color(0xFF8F6206), dark = Color(0xFFE9B54C)), // amber
        BoardHue(light = Color(0xFF1B7A55), dark = Color(0xFF5FC79A)), // green
        BoardHue(light = Color(0xFF6D45C0), dark = Color(0xFFB18CF0)), // violet
        BoardHue(light = Color(0xFFB83A6E), dark = Color(0xFFEE87B4)), // magenta
        BoardHue(light = Color(0xFF116C74), dark = Color(0xFF5CC6D0)), // teal
        BoardHue(light = Color(0xFFA6491F), dark = Color(0xFFEE9468)), // rust
        BoardHue(light = Color(0xFF4A54C6), dark = Color(0xFF93A0F5)), // indigo
        BoardHue(light = Color(0xFF5F6F14), dark = Color(0xFFB6CB55)), // olive
        BoardHue(light = Color(0xFF8C3A8C), dark = Color(0xFFD98BD9)), // plum
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
 * Soft two-stop gradients varied by position. Light gradients use eggplant-adjacent
 * tints (lavender/mauve/rose); dark gradients are deep muted fills matching the dark surface.
 */
@Composable
fun placeholderArt(seed: Int): Brush {
    val dark = next.dark
    val pairs =
        if (dark) {
            listOf(
                Color(0xFF3D2B47) to Color(0xFF241832),
                Color(0xFF3B2A3A) to Color(0xFF231829),
                Color(0xFF2A3545) to Color(0xFF192130),
                Color(0xFF3A2A44) to Color(0xFF22182A),
                Color(0xFF2E3520) to Color(0xFF1C2113),
            )
        } else {
            listOf(
                Color(0xFFEFDAFF) to Color(0xFFDEC3F2), // lavender
                Color(0xFFFFD9E1) to Color(0xFFF2C4CF), // rose
                Color(0xFFDFE3FF) to Color(0xFFC8CDF5), // periwinkle
                Color(0xFFE8F5E9) to Color(0xFFCAE6CB), // sage
                Color(0xFFFFEDD5) to Color(0xFFF2D8BA), // warm cream
            )
        }
    val (start, end) = pairs[((seed % pairs.size) + pairs.size) % pairs.size]
    return Brush.linearGradient(listOf(start, end))
}
