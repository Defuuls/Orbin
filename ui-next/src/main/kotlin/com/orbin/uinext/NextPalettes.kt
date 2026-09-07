package com.orbin.uinext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.orbin.core.designsystem.theme.ChanThemeSeeds
import com.orbin.core.designsystem.theme.ColorSchemeVariant
import kotlin.math.max
import kotlin.math.min

/**
 * Maps a persisted [ColorSchemeVariant] onto the Next shell palette.
 *
 * Default / Tomorrow stay on the curated warm Next grounds. Imageboard skins expand from their
 * [ChanThemeSeeds] so Color theme in settings actually recolors Feed and the rest of ui-next,
 * not only Material-only destinations.
 */
fun ColorSchemeVariant.toNextPalette(
    darkPreference: Boolean,
    amoled: Boolean,
): NextPalette {
    seeds?.let { return it.toNextPalette(amoled = amoled && it.dark) }
    val dark =
        when (this) {
            ColorSchemeVariant.TOMORROW -> false
            ColorSchemeVariant.TOMORROW_NIGHT -> true
            else -> darkPreference
        }
    return when {
        dark && amoled -> AmoledPalette
        dark -> DarkPalette
        else -> LightPalette
    }
}

internal fun ChanThemeSeeds.toNextPalette(amoled: Boolean): NextPalette {
    val bg = if (amoled && dark) Color.Black else background
    val panel = if (amoled && dark) Color(0xFF0A0A0A) else surface
    val body = onSurface
    val accent = ensureContrast(primary, bg, listOf(primaryVariant, subject, body))
    val accentOn =
        if (accent.luminance() > ON_ACCENT_LUMINANCE_THRESHOLD) {
            Color.Black
        } else {
            Color.White
        }
    // Prefer readable tiers over aggressive fade — imageboard seeds often ship mid-grey body text.
    val muted = ensureContrast(body.copy(alpha = if (dark) 0.92f else 0.88f), bg, listOf(body))
    val faint = ensureContrast(body.copy(alpha = if (dark) 0.82f else 0.78f), bg, listOf(muted, body))
    return NextPalette(
        background = bg,
        raised = panel,
        ink = body,
        muted = muted,
        faint = faint,
        hairline = outline.copy(alpha = if (dark) 0.55f else 0.45f),
        accent = accent,
        accentSoft = accent.copy(alpha = if (dark) 0.28f else 0.20f),
        onAccent = accentOn,
        dark = dark,
        amoled = amoled && dark,
    )
}

/** Picks [preferred] when it clears AA on [background]; otherwise the first fallback that does,
 * otherwise blends [preferred] toward black/white until AA is met. */
private fun ensureContrast(
    preferred: Color,
    background: Color,
    fallbacks: List<Color>,
): Color {
    if (contrastRatio(preferred, background) >= AA_NORMAL_TEXT) return preferred
    fallbacks.firstOrNull { contrastRatio(it, background) >= AA_NORMAL_TEXT }?.let { return it }
    val toward = if (background.luminance() > 0.5f) Color.Black else Color.White
    var best = preferred
    var bestRatio = contrastRatio(preferred, background)
    for (step in 1..9) {
        val candidate = preferred.blend(toward, step / 10f)
        val ratio = contrastRatio(candidate, background)
        if (ratio >= AA_NORMAL_TEXT) return candidate
        if (ratio > bestRatio) {
            best = candidate
            bestRatio = ratio
        }
    }
    return best
}

private fun Color.blend(
    other: Color,
    fraction: Float,
): Color =
    Color(
        red = red + (other.red - red) * fraction,
        green = green + (other.green - green) * fraction,
        blue = blue + (other.blue - blue) * fraction,
        alpha = 1f,
    )

private fun contrastRatio(
    foreground: Color,
    background: Color,
): Float {
    val composed =
        Color(
            red = foreground.red * foreground.alpha + background.red * (1f - foreground.alpha),
            green = foreground.green * foreground.alpha + background.green * (1f - foreground.alpha),
            blue = foreground.blue * foreground.alpha + background.blue * (1f - foreground.alpha),
            alpha = 1f,
        )
    val l1 = composed.luminance()
    val l2 = background.luminance()
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return (lighter + 0.05f) / (darker + 0.05f)
}

private const val ON_ACCENT_LUMINANCE_THRESHOLD = 0.5f
private const val AA_NORMAL_TEXT = 4.5f
