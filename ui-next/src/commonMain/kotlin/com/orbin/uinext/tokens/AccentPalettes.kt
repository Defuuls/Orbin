package com.orbin.uinext.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.orbin.uinext.NextAccentTheme

/** Stable user-selected hues; Orbin's default remains the indigo identity. */
private data class AccentSpec(
    val hue: Float,
    val accent: Color,
    val secondary: Color,
)

private val namedAccentSwatches =
    mapOf(
        NextAccentTheme.ORBIN to AccentSpec(230f, Color(0xFFA5B4FC), Color(0xFFA78BFA)),
        NextAccentTheme.CYAN to AccentSpec(191f, Color(0xFF6DD4EC), Color(0xFFA78BFA)),
        NextAccentTheme.BLUE to AccentSpec(213f, Color(0xFF60A5FA), Color(0xFFC084FC)),
        NextAccentTheme.INDIGO to AccentSpec(239f, Color(0xFF818CF8), Color(0xFFE879F9)),
        NextAccentTheme.VIOLET to AccentSpec(258f, Color(0xFFA78BFA), Color(0xFFF472B6)),
        NextAccentTheme.PURPLE to AccentSpec(271f, Color(0xFFC084FC), Color(0xFFFB7185)),
        NextAccentTheme.FUCHSIA to AccentSpec(292f, Color(0xFFE879F9), Color(0xFF818CF8)),
        NextAccentTheme.PINK to AccentSpec(329f, Color(0xFFF472B6), Color(0xFFA78BFA)),
        NextAccentTheme.ROSE to AccentSpec(351f, Color(0xFFFB7185), Color(0xFFC084FC)),
        NextAccentTheme.RED to AccentSpec(0f, Color(0xFFF87171), Color(0xFFFB923C)),
        NextAccentTheme.ORANGE to AccentSpec(27f, Color(0xFFFB923C), Color(0xFFF472B6)),
        NextAccentTheme.AMBER to AccentSpec(45f, Color(0xFFFBBF24), Color(0xFFFB7185)),
        NextAccentTheme.LIME to AccentSpec(83f, Color(0xFFA3E635), Color(0xFF2DD4BF)),
        NextAccentTheme.GREEN to AccentSpec(142f, Color(0xFF4ADE80), Color(0xFF60A5FA)),
        NextAccentTheme.EMERALD to AccentSpec(160f, Color(0xFF34D399), Color(0xFF818CF8)),
        NextAccentTheme.TEAL to AccentSpec(172f, Color(0xFF2DD4BF), Color(0xFFA78BFA)),
    )

private data class AccentTone(
    val saturation: Float,
    val lightness: Float,
)

private val darkPageTone = AccentTone(0.32f, 0.06f)
private val lightPageTone = AccentTone(0.45f, 0.95f)
private val darkCardTone = AccentTone(0.28f, 0.115f)
private val lightCardTone = AccentTone(0.70f, 0.995f)
private val darkSelectedTone = AccentTone(0.24f, 0.135f)
private val lightSelectedTone = AccentTone(0.4f, 0.97f)
private val darkTextTone = AccentTone(0.30f, 0.93f)
private val lightTextTone = AccentTone(0.35f, 0.10f)
private val darkMutedTone = AccentTone(0.16f, 0.64f)
private val lightMutedTone = AccentTone(0.16f, 0.35f)
private const val ACCENT_TEXT_SATURATION = 0.72f
private const val INITIAL_ACCENT_TEXT_LIGHTNESS = 0.36f
private const val MINIMUM_ACCENT_TEXT_LIGHTNESS = 0.1f
private const val ACCENT_LIGHTNESS_STEP = 0.01f
private const val MINIMUM_TEXT_CONTRAST = 4.5f
private const val WCAG_LUMINANCE_OFFSET = 0.05f

private fun accentSpec(theme: NextAccentTheme): AccentSpec = namedAccentSwatches.getValue(theme)

fun accentSecondary(theme: NextAccentTheme): Color = accentSpec(theme).secondary

/** Swatches retain their bright hue even when light-mode text uses its contrast-safe darker ink. */
fun accentSwatch(theme: NextAccentTheme): Color = accentSpec(theme).accent

internal fun accentedReaderColors(
    base: ReaderColors,
    dark: Boolean,
    amoled: Boolean,
    theme: NextAccentTheme,
): ReaderColors {
    if (theme == NextAccentTheme.ORBIN) return base
    val spec = accentSpec(theme)

    fun tint(tone: AccentTone) = Color.hsl(spec.hue, tone.saturation, tone.lightness)
    val page =
        if (amoled) {
            base.background
        } else if (dark) {
            tint(darkPageTone)
        } else {
            tint(lightPageTone)
        }
    val card =
        if (amoled) {
            base.surfaceVariant
        } else if (dark) {
            tint(darkCardTone)
        } else {
            tint(lightCardTone)
        }
    val selected =
        if (amoled) {
            base.selected
        } else if (dark) {
            tint(darkSelectedTone)
        } else {
            tint(lightSelectedTone)
        }
    val text =
        if (amoled) {
            base.ink
        } else if (dark) {
            tint(darkTextTone)
        } else {
            tint(lightTextTone)
        }
    val muted =
        if (amoled) {
            base.inkMuted
        } else if (dark) {
            tint(darkMutedTone)
        } else {
            tint(lightMutedTone)
        }
    var lightness = INITIAL_ACCENT_TEXT_LIGHTNESS
    var ink = Color.hsl(spec.hue, ACCENT_TEXT_SATURATION, lightness)
    while (!dark &&
        listOf(page, card, selected).any { contrast(ink, it) < MINIMUM_TEXT_CONTRAST } &&
        lightness > MINIMUM_ACCENT_TEXT_LIGHTNESS
    ) {
        lightness -= ACCENT_LIGHTNESS_STEP
        ink = Color.hsl(spec.hue, ACCENT_TEXT_SATURATION, lightness)
    }
    val accent = if (dark) spec.accent else ink
    return base.copy(
        appBar = page,
        statusBar = page,
        onAppBar = text,
        background = page,
        surfaceVariant = card,
        selected = selected,
        ink = text,
        inkSecondary = muted,
        inkMuted = muted,
        accent = accent,
        onAccent = if (dark) Color.Black else Color.White,
        author = if (dark) accent else text,
        subject = text,
        quoteLink = accent,
    )
}

private fun contrast(
    a: Color,
    b: Color,
): Float {
    val first = a.luminance()
    val second = b.luminance()
    return (maxOf(first, second) + WCAG_LUMINANCE_OFFSET) / (minOf(first, second) + WCAG_LUMINANCE_OFFSET)
}
