package com.orbin.uinext

import androidx.compose.ui.graphics.Color

/** Presentation-only colors; app roots adapt their persisted preference by stable name. */
enum class NextColorTheme { ORBIN, BLUE, RED, WHITE, BLACK, PURPLE, ORANGE, TEAL }

/**
 * Shared Android/iOS palette registry. Color chooses accents; brightness follows Theme mode.
 * True black only changes dark grounds, leaving raised surfaces and accent choices readable.
 */
fun colorThemePalette(
    theme: NextColorTheme,
    dark: Boolean,
    amoled: Boolean = false,
): NextPalette {
    if (theme == NextColorTheme.ORBIN) return canonicalNextPalette(dark, amoled)
    // Color is independent of brightness: Theme mode and True black own the ground.
    val black = dark && amoled
    val base = if (dark) NeutralDark else NeutralLight
    val accents = ThemeAccents.getValue(theme)
    val accent = if (dark) accents.second else accents.first
    return base.copy(
        background = if (black) Color.Black else base.background,
        accent = accent,
        onAccentContainer = accent,
        amoled = black,
    )
}

private val MonochromeAccents = Color(0xFF303030) to Color(0xFFE2E2E2)
private val ThemeAccents =
    mapOf(
        NextColorTheme.BLUE to (Color(0xFF174EA6) to Color(0xFFA8C7FA)),
        NextColorTheme.RED to (Color(0xFF9C2525) to Color(0xFFFFB4AB)),
        NextColorTheme.PURPLE to (Color(0xFF663399) to Color(0xFFD4BBFF)),
        NextColorTheme.ORANGE to (Color(0xFF944500) to Color(0xFFFFB77D)),
        NextColorTheme.TEAL to (Color(0xFF006B63) to Color(0xFF80D5CD)),
        NextColorTheme.WHITE to MonochromeAccents,
        NextColorTheme.BLACK to MonochromeAccents,
    )

// Neutral surfaces keep board hues, media, error messages and disabled controls readable across
// every accent family. Selected containers use the family's accent as their foreground.
private val NeutralLight =
    LightPalette.copy(
        background = Color.White,
        raised = Color(0xFFF5F5F5),
        elevated = Color(0xFFEEEEEE),
        ink = Color(0xFF191919),
        muted = Color(0xFF555555),
        faint = Color(0xFF666666),
        hairline = Color(0xFFBBBBBB),
        accentSoft = Color(0xFFE4E4E4),
        onAccent = Color.White,
        accentContainer = Color(0xFFE4E4E4),
    )

private val NeutralDark =
    DarkPalette.copy(
        background = Color(0xFF121212),
        raised = Color(0xFF1B1B1B),
        elevated = Color(0xFF242424),
        ink = Color(0xFFF2F2F2),
        muted = Color(0xFFCCCCCC),
        faint = Color(0xFFAAAAAA),
        hairline = Color(0xFF505050),
        accentSoft = Color(0xFF343434),
        onAccent = Color(0xFF151515),
        accentContainer = Color(0xFF343434),
    )
