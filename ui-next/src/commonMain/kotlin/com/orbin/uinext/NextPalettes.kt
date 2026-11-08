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

private val AmoledRaised = Color(0xFF0E1015)
private val AmoledElevated = Color(0xFF151820)
private const val FAINT_ALPHA_DARK = 0.62f
private const val FAINT_ALPHA_LIGHT = 0.66f

private val DestructiveLight = Color(0xFFBA1A1A)
private val OnDestructiveLight = Color(0xFFFFFFFF)
private val DestructiveDark = Color(0xFFFFB4AB)
private val OnDestructiveDark = Color(0xFF690005)

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
        destructive = colorScheme.error,
        onDestructive = colorScheme.onError,
        dark = dark,
        amoled = dark && amoled,
    )

/**
 * OPTIC-26 Light Mode — Raw Titanium & Field Paper.
 * High-contrast dark ink on warm brushed titanium / drafting vellum with International Orange
 * optical shutter accent. Every text tier clears WCAG AA 4.5:1 across all three surface levels.
 */
internal val LightPalette =
    NextPalette(
        background = Color(0xFFF4F2ED),
        raised = Color(0xFFFAF9F6),
        elevated = Color(0xFFEAE7E0),
        ink = Color(0xFF121417),
        muted = Color(0xFF3D424A),
        faint = Color(0xFF535861),
        hairline = Color(0xFFC8C4BA),
        accent = Color(0xFF9E3608),
        accentSoft = Color(0xFFF9D6C4),
        onAccent = Color(0xFFFFFFFF),
        accentContainer = Color(0xFFF9D6C4),
        onAccentContainer = Color(0xFF330E00),
        destructive = DestructiveLight,
        onDestructive = OnDestructiveLight,
        dark = false,
        amoled = false,
    )

/**
 * OPTIC-26 Dark Mode — Obsidian Anodized.
 * Deep anodized carbon-slate chassis with warm optical phosphor ink and International Orange
 * indicator accent. Every text tier clears WCAG AA 4.5:1 across all three surface levels.
 */
internal val DarkPalette =
    NextPalette(
        background = Color(0xFF0A0A0C),
        raised = Color(0xFF12141A),
        elevated = Color(0xFF181B22),
        ink = Color(0xFFE8E4DC),
        muted = Color(0xFFB6B3AC),
        faint = Color(0xFF8E8B85),
        hairline = Color(0xFF282C37),
        accent = Color(0xFFFF7A3D),
        accentSoft = Color(0xFF4A1D06),
        onAccent = Color(0xFF1A0700),
        accentContainer = Color(0xFF4A1D06),
        onAccentContainer = Color(0xFFFFD5C2),
        destructive = DestructiveDark,
        onDestructive = OnDestructiveDark,
        dark = true,
        amoled = false,
    )

/**
 * OPTIC-26 AMOLED Mode — True-Zero Carbon.
 * Pure `#000000` OLED ground with anodized carbon-slate cells and `1dp` structural borders.
 */
internal val AmoledPalette =
    DarkPalette.copy(
        background = Color.Black,
        raised = AmoledRaised,
        elevated = AmoledElevated,
        amoled = true,
    )
