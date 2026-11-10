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
 * Instagram Clean Light Mode — Pure white canvas, crisp dark ink, subtle hairlines, and Royal Blue accent.
 */
internal val LightPalette =
    NextPalette(
        background = Color(0xFFFFFFFF),
        raised = Color(0xFFFAFAFA),
        elevated = Color(0xFFFFFFFF),
        ink = Color(0xFF262626),
        muted = Color(0xFF737373),
        faint = Color(0xFF8E8E8E),
        hairline = Color(0xFFEFEFEF),
        accent = Color(0xFF0095F6),
        accentSoft = Color(0xFFE0F1FF),
        onAccent = Color(0xFFFFFFFF),
        accentContainer = Color(0xFFE0F1FF),
        onAccentContainer = Color(0xFF00376B),
        destructive = Color(0xFFED4956),
        onDestructive = Color(0xFFFFFFFF),
        dark = false,
        amoled = false,
    )

/**
 * Instagram Dark Mode — Pure black ground, crisp light ink, and Royal Blue accent.
 */
internal val DarkPalette =
    NextPalette(
        background = Color(0xFF000000),
        raised = Color(0xFF121212),
        elevated = Color(0xFF1A1A1A),
        ink = Color(0xFFF5F5F5),
        muted = Color(0xFFA8A8A8),
        faint = Color(0xFF737373),
        hairline = Color(0xFF262626),
        accent = Color(0xFF0095F6),
        accentSoft = Color(0xFF002952),
        onAccent = Color(0xFFFFFFFF),
        accentContainer = Color(0xFF002952),
        onAccentContainer = Color(0xFFB3DCFF),
        destructive = Color(0xFFED4956),
        onDestructive = Color(0xFFFFFFFF),
        dark = true,
        amoled = false,
    )

/**
 * Instagram AMOLED Mode — Pure `#000000` ground and elevated bars.
 */
internal val AmoledPalette =
    DarkPalette.copy(
        background = Color.Black,
        raised = Color.Black,
        elevated = Color(0xFF121212),
        amoled = true,
    )
