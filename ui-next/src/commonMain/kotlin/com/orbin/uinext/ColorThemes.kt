package com.orbin.uinext

import androidx.compose.ui.graphics.Color
import com.orbin.core.model.AppColorTheme

/** Shared Android/iOS palette registry. Existing forest and AMOLED palettes remain unchanged. */
fun colorThemePalette(
    theme: AppColorTheme,
    dark: Boolean,
    amoled: Boolean = false,
): NextPalette {
    if (theme == AppColorTheme.ORBIN) return canonicalNextPalette(dark, amoled)
    val resolvedDark =
        when (theme) {
            AppColorTheme.WHITE -> false
            AppColorTheme.BLACK -> true
            else -> dark
        }
    val black = resolvedDark && (amoled || theme == AppColorTheme.BLACK)
    val base = if (resolvedDark) NeutralDark else NeutralLight
    val accents = ThemeAccents.getValue(theme)
    val accent = if (resolvedDark) accents.second else accents.first
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
        AppColorTheme.BLUE to (Color(0xFF174EA6) to Color(0xFFA8C7FA)),
        AppColorTheme.RED to (Color(0xFF9C2525) to Color(0xFFFFB4AB)),
        AppColorTheme.PURPLE to (Color(0xFF663399) to Color(0xFFD4BBFF)),
        AppColorTheme.ORANGE to (Color(0xFF944500) to Color(0xFFFFB77D)),
        AppColorTheme.TEAL to (Color(0xFF006B63) to Color(0xFF80D5CD)),
        AppColorTheme.WHITE to MonochromeAccents,
        AppColorTheme.BLACK to MonochromeAccents,
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
