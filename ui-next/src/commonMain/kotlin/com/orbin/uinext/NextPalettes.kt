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

private val AmoledRaised = Color(0xFF111111)
private val AmoledElevated = Color(0xFF1B1B1B)
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

/** Default light reader palette: white surfaces and monochrome controls. */
internal val LightPalette =
    NextPalette(
        background = Color(0xFFFFFFFF),
        raised = Color(0xFFF5F5F5),
        elevated = Color(0xFFEEEEEE),
        ink = Color(0xFF171717),
        muted = Color(0xFF5B5B5B),
        faint = Color(0xFF666666),
        hairline = Color(0xFFE2E2E2),
        accent = Color(0xFF171717),
        accentSoft = Color(0xFFEEEEEE),
        onAccent = Color(0xFFFFFFFF),
        accentContainer = Color(0xFFEEEEEE),
        onAccentContainer = Color(0xFF171717),
        destructive = DestructiveLight,
        onDestructive = OnDestructiveLight,
        dark = false,
        amoled = false,
    )

/** Dark reader palette with the same semantic contrast and hierarchy. */
internal val DarkPalette =
    NextPalette(
        background = Color(0xFF101010),
        raised = Color(0xFF1B1B1B),
        elevated = Color(0xFF242424),
        ink = Color(0xFFF5F5F5),
        muted = Color(0xFFBDBDBD),
        faint = Color(0xFFAAAAAA),
        hairline = Color(0xFF363636),
        accent = Color(0xFFF5F5F5),
        accentSoft = Color(0xFF303030),
        onAccent = Color(0xFF171717),
        accentContainer = Color(0xFF303030),
        onAccentContainer = Color(0xFFF5F5F5),
        destructive = DestructiveDark,
        onDestructive = OnDestructiveDark,
        dark = true,
        amoled = false,
    )

/** True-black variant for OLED displays. */
internal val AmoledPalette =
    DarkPalette.copy(
        background = Color.Black,
        raised = AmoledRaised,
        elevated = AmoledElevated,
        amoled = true,
    )
