package com.orbin.uinext

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/** The one palette family used by ui-next application chrome. */
fun canonicalNextPalette(
    dark: Boolean,
    amoled: Boolean,
): NextPalette =
    when {
        dark && amoled -> AmoledPalette
        dark -> DarkPalette
        else -> LightPalette
    }

private val AmoledRaised = Color(0xFF1D1B1E)
private val AmoledElevated = Color(0xFF221F24)
private const val FAINT_ALPHA_DARK = 0.60f
private const val FAINT_ALPHA_LIGHT = 0.62f

/**
 * Derives a [NextPalette] directly from an active Material 3 [ColorScheme].
 *
 * Preserves AMOLED true-black behavior, WCAG AA contrast floor, and dynamic color.
 */
fun nextPaletteFromColorScheme(
    colorScheme: ColorScheme,
    dark: Boolean,
    amoled: Boolean = false,
): NextPalette =
    NextPalette(
        background = if (dark && amoled) Color.Black else colorScheme.background,
        raised = if (dark && amoled) AmoledRaised else colorScheme.surfaceContainerLow,
        elevated = if (dark && amoled) AmoledElevated else colorScheme.surfaceContainer,
        ink = colorScheme.onSurface,
        muted = colorScheme.onSurfaceVariant,
        faint = colorScheme.onSurface.copy(alpha = if (dark) FAINT_ALPHA_DARK else FAINT_ALPHA_LIGHT),
        hairline = colorScheme.outlineVariant,
        accent = colorScheme.primary,
        accentSoft = colorScheme.primaryContainer,
        onAccent = colorScheme.onPrimary,
        accentContainer = colorScheme.primaryContainer,
        onAccentContainer = colorScheme.onPrimaryContainer,
        dark = dark,
        amoled = dark && amoled,
    )

/**
 * Fallback static palette (used when M3 color scheme is not yet available, e.g. preview context).
 * These mirror the forest green static scheme values from Color.kt.
 */
internal val LightPalette =
    NextPalette(
        background = Color(0xFFF7FBF7),
        raised = Color(0xFFF1F7F1),
        elevated = Color(0xFFECF2EC),
        ink = Color(0xFF191C19),
        muted = Color(0xFF191C19).copy(alpha = 0.75f),
        faint = Color(0xFF191C19).copy(alpha = 0.62f),
        hairline = Color(0xFFC0C9BF),
        accent = Color(0xFF276A3C),
        accentSoft = Color(0xFFC8EAD4),
        onAccent = Color(0xFFFFFFFF),
        accentContainer = Color(0xFFC8EAD4),
        onAccentContainer = Color(0xFF00210E),
        dark = false,
        amoled = false,
    )

internal val DarkPalette =
    NextPalette(
        background = Color(0xFF101A12),
        raised = Color(0xFF182019),
        elevated = Color(0xFF1C271E),
        ink = Color(0xFFE0E8E0),
        muted = Color(0xFFE0E8E0).copy(alpha = 0.75f),
        faint = Color(0xFFE0E8E0).copy(alpha = 0.60f),
        hairline = Color(0xFF404941),
        accent = Color(0xFFA3D4AF),
        accentSoft = Color(0xFF1F5D3A),
        onAccent = Color(0xFF00391A),
        accentContainer = Color(0xFF1F5D3A),
        onAccentContainer = Color(0xFFC8EAD4),
        dark = true,
        amoled = false,
    )

// Dark mode is deep forest; only the AMOLED option keeps a true-black ground.
internal val AmoledPalette =
    DarkPalette.copy(
        background = Color.Black,
        raised = AmoledRaised,
        elevated = AmoledElevated,
        amoled = true,
    )
