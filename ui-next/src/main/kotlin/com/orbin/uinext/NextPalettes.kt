package com.orbin.uinext

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.orbin.core.designsystem.theme.ChanThemeSeeds
import com.orbin.core.designsystem.theme.ColorSchemeVariant
import kotlin.math.max
import kotlin.math.min

/**
 * Maps a [ColorSchemeVariant] onto the Next shell palette.
 *
 * The shell always reads the M3 eggplant palette — dynamic color or the static eggplant scheme.
 * Imageboard skin [ChanThemeSeeds] control only content-level surfaces (post renderer, board
 * surfaces); they do not recolor navigation, chrome, or interactive controls.
 *
 * [dark] is the only thing an imageboard skin contributes to the shell: it selects which of the
 * two canonical palettes to load.
 */
fun ColorSchemeVariant.toNextPalette(
    darkPreference: Boolean,
    amoled: Boolean,
): NextPalette {
    val dark =
        when (this) {
            // Skins retain their own light/dark preference as a mode hint only.
            else -> seeds?.dark ?: darkPreference
        }
    return canonicalNextPalette(dark = dark, amoled = amoled)
}

/**
 * Builds a [NextPalette] from the current M3 [MaterialTheme.colorScheme].
 *
 * Called from [NextTheme] after the color scheme is resolved so that dynamic color and the
 * eggplant static scheme both flow through here — one palette builder, no hardcoded hex values
 * at the shell level.
 */
@Composable
internal fun nextPaletteFromM3(dark: Boolean, amoled: Boolean): NextPalette {
    val cs = MaterialTheme.colorScheme
    return NextPalette(
        // Flat matte background — M3 surface/background, no iOS grey
        background = cs.background,
        // Raised card surface — surfaceContainerLow gives a barely-tinted matte card
        raised = cs.surfaceContainerLow,
        // Elevated surface one step above raised — surfaceContainer
        elevated = cs.surfaceContainer,
        // Primary text — onBackground (high contrast on background)
        ink = cs.onBackground,
        // Secondary text — onSurfaceVariant (M3 muted role)
        muted = cs.onSurfaceVariant,
        // Tertiary / hint text — onSurfaceVariant at reduced alpha
        faint = cs.onSurfaceVariant.copy(alpha = if (dark) 0.68f else 0.60f),
        // Separator — outlineVariant (very soft, no strong border)
        hairline = cs.outlineVariant,
        // Accent — M3 primary (eggplant)
        accent = cs.primary,
        // Soft accent fill — primaryContainer tint
        accentSoft = cs.primaryContainer,
        // Text/icons on solid accent fill
        onAccent = cs.onPrimary,
        // Tonal container for selected/active states
        accentContainer = cs.primaryContainer,
        onAccentContainer = cs.onPrimaryContainer,
        dark = dark,
        amoled = amoled,
    )
}

/** The one palette family used by ui-next application chrome. */
fun canonicalNextPalette(dark: Boolean, amoled: Boolean): NextPalette =
    when {
        dark && amoled -> AmoledPalette
        dark -> DarkPalette
        else -> LightPalette
    }

/**
 * Fallback static palette (used when M3 color scheme is not yet available, e.g. preview context).
 * These mirror the eggplant static scheme values from Color.kt.
 */
internal val LightPalette =
    NextPalette(
        background = Color(0xFFFFFBFF),
        raised = Color(0xFFF9F3F9),
        elevated = Color(0xFFF3EDF3),
        ink = Color(0xFF1D1B1E),
        muted = Color(0xFF1D1B1E).copy(alpha = 0.75f),
        faint = Color(0xFF1D1B1E).copy(alpha = 0.62f),
        hairline = Color(0xFFCEC2CF),
        accent = Color(0xFF7B4F8A),
        accentSoft = Color(0xFFF3DAFF),
        onAccent = Color(0xFFFFFFFF),
        accentContainer = Color(0xFFF3DAFF),
        onAccentContainer = Color(0xFF2E0042),
        dark = false,
        amoled = false,
    )

internal val DarkPalette =
    NextPalette(
        background = Color(0xFF000000),
        raised = Color(0xFF1D1B1E),
        elevated = Color(0xFF221F24),
        ink = Color(0xFFE8E0E9),
        muted = Color(0xFFE8E0E9).copy(alpha = 0.75f),
        faint = Color(0xFFE8E0E9).copy(alpha = 0.60f),
        hairline = Color(0xFF4D4050),
        accent = Color(0xFFDFACF0),
        accentSoft = Color(0xFF613472),
        onAccent = Color(0xFF481A5A),
        accentContainer = Color(0xFF613472),
        onAccentContainer = Color(0xFFF3DAFF),
        dark = true,
        amoled = false,
    )

internal val AmoledPalette = DarkPalette.copy(amoled = true)

internal val DarkAmoledPalette = AmoledPalette

// ── Legacy contrast utilities (kept for ChanThemeSeeds.toNextPalette) ──────

/** Contrast-safe accent for imageboard skin content surfaces. Not used for shell chrome. */
internal fun ChanThemeSeeds.toNextPalette(amoled: Boolean): NextPalette {
    val bg = if (amoled && dark) Color.Black else background
    val panel = if (amoled && dark) Color(0xFF1C1C1E) else surface
    val body = onSurface
    val accent = ensureAccentWithOnColor(primary, bg, listOf(primaryVariant, subject, body))
    val accentOn = onColorFor(accent)
    val muted = body.copy(alpha = if (dark) 0.72f else 0.68f)
    val faint = body.copy(alpha = if (dark) 0.52f else 0.50f)
    return NextPalette(
        background = bg,
        raised = panel,
        elevated = panel,
        ink = body,
        muted = muted,
        faint = faint,
        hairline = outline.copy(alpha = if (dark) 0.55f else 0.45f),
        accent = accent,
        accentSoft = accent.copy(alpha = if (dark) 0.28f else 0.20f),
        onAccent = accentOn,
        accentContainer = accent.copy(alpha = if (dark) 0.28f else 0.20f),
        onAccentContainer = body,
        dark = dark,
        amoled = amoled && dark,
    )
}

private fun ensureAccentWithOnColor(preferred: Color, background: Color, fallbacks: List<Color>): Color {
    val candidates = listOf(preferred) + fallbacks
    candidates.firstOrNull { color ->
        contrastRatio(color, background) >= AA_NORMAL_TEXT &&
            max(contrastRatio(Color.White, color), contrastRatio(Color.Black, color)) >= AA_NORMAL_TEXT
    }?.let { return it }

    val towardBgOpposite = if (background.luminance() > LUMINANCE_MIDPOINT) Color.Black else Color.White
    var best = preferred
    var bestScore = 0f
    for (step in 1..CONTRAST_BLEND_STEPS) {
        val candidate = preferred.blend(towardBgOpposite, step / CONTRAST_BLEND_DIVISOR)
        val onBg = contrastRatio(candidate, background)
        val onChip = max(contrastRatio(Color.White, candidate), contrastRatio(Color.Black, candidate))
        val score = min(onBg, onChip)
        if (onBg >= AA_NORMAL_TEXT && onChip >= AA_NORMAL_TEXT) return candidate
        if (score > bestScore) { best = candidate; bestScore = score }
    }
    return best
}

private fun onColorFor(accent: Color): Color {
    val white = contrastRatio(Color.White, accent)
    val black = contrastRatio(Color.Black, accent)
    return if (white >= black) Color.White else Color.Black
}

private fun Color.blend(other: Color, fraction: Float): Color =
    Color(
        red = red + (other.red - red) * fraction,
        green = green + (other.green - green) * fraction,
        blue = blue + (other.blue - blue) * fraction,
        alpha = 1f,
    )

private fun contrastRatio(foreground: Color, background: Color): Float {
    val composed = Color(
        red = foreground.red * foreground.alpha + background.red * (1f - foreground.alpha),
        green = foreground.green * foreground.alpha + background.green * (1f - foreground.alpha),
        blue = foreground.blue * foreground.alpha + background.blue * (1f - foreground.alpha),
        alpha = 1f,
    )
    val l1 = composed.luminance()
    val l2 = background.luminance()
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return (lighter + LUMINANCE_OFFSET) / (darker + LUMINANCE_OFFSET)
}

private fun Color.luminance(): Float {
    fun linearize(v: Float) = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).let { it * it * it }
    return 0.2126f * linearize(red) + 0.7152f * linearize(green) + 0.0722f * linearize(blue)
}

private const val AA_NORMAL_TEXT = 4.5f
private const val LUMINANCE_MIDPOINT = 0.5f
private const val LUMINANCE_OFFSET = 0.05f
private const val CONTRAST_BLEND_STEPS = 9
private const val CONTRAST_BLEND_DIVISOR = 10f
