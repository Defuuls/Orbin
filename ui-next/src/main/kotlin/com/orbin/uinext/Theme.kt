package com.orbin.uinext

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The palette the proposal is drawn from.
 *
 * The old interface is greyscale Material with the default blue: a white card on a white background
 * separated by a shadow, and every accent the same blue that ships in the template. That reads as
 * unfinished rather than restrained.
 *
 * This is warm ink on warm paper — neither is pure — with a single terracotta accent that belongs to
 * an imageboard rather than to a settings app, and a set of board hues that give a mixed feed some
 * rhythm to scan by. Two greys are all that is left over: [muted] for secondary text, [hairline] for
 * the one separator.
 */
@Immutable
data class NextPalette(
    val background: Color,
    val raised: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val hairline: Color,
    val accent: Color,
    val accentSoft: Color,
    val dark: Boolean,
)

private val LightPalette =
    NextPalette(
        background = Color(0xFFFAF8F5),
        raised = Color(0xFFFFFFFF),
        ink = Color(0xFF16141A),
        muted = Color(0xFF16141A).copy(alpha = 0.54f),
        faint = Color(0xFF16141A).copy(alpha = 0.34f),
        hairline = Color(0xFF16141A).copy(alpha = 0.09f),
        accent = Color(0xFFA8431B),
        accentSoft = Color(0xFFA8431B).copy(alpha = 0.10f),
        dark = false,
    )

private val DarkPalette =
    NextPalette(
        background = Color(0xFF0D0D11),
        raised = Color(0xFF17171D),
        ink = Color(0xFFF1EFF2),
        muted = Color(0xFFF1EFF2).copy(alpha = 0.56f),
        faint = Color(0xFFF1EFF2).copy(alpha = 0.34f),
        hairline = Color(0xFFF1EFF2).copy(alpha = 0.12f),
        accent = Color(0xFFF08A5A),
        accentSoft = Color(0xFFF08A5A).copy(alpha = 0.14f),
        dark = true,
    )

val LocalNext = staticCompositionLocalOf { LightPalette }

/** Shorthand for the palette in scope. */
val next: NextPalette
    @Composable get() = LocalNext.current

@Composable
fun NextTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val scheme =
        if (darkTheme) {
            darkColorScheme(
                background = palette.background,
                onBackground = palette.ink,
                surface = palette.raised,
                onSurface = palette.ink,
                primary = palette.accent,
            )
        } else {
            lightColorScheme(
                background = palette.background,
                onBackground = palette.ink,
                surface = palette.raised,
                onSurface = palette.ink,
                primary = palette.accent,
            )
        }
    CompositionLocalProvider(LocalNext provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * A board's hue.
 *
 * A merged feed is a pile of unrelated boards, and the only thing distinguishing one row's origin
 * from another's today is four grey characters. A colour per board makes the mix legible at a
 * glance without adding a second line to any row.
 */
@Composable
fun boardHue(board: String): Color {
    val dark = next.dark
    return when (board) {
        "/g/" -> if (dark) Color(0xFF74A9F8) else Color(0xFF2C6BC4)
        "/ck/" -> if (dark) Color(0xFFE9B54C) else Color(0xFFB07708)
        "/p/" -> if (dark) Color(0xFF5FC79A) else Color(0xFF1B7A55)
        "/lit/" -> if (dark) Color(0xFFB18CF0) else Color(0xFF6D45C0)
        "/aco/" -> if (dark) Color(0xFFEE87B4) else Color(0xFFB83A6E)
        else -> next.accent
    }
}

/**
 * Stand-in artwork for a thumbnail that has not loaded.
 *
 * A grid of identical grey squares tells you nothing and looks broken; the real screen is full of
 * photographs. These are soft two-stop gradients, varied by position, so the layout can be judged
 * against something with the tonal variety real content has.
 */
@Composable
fun placeholderArt(seed: Int): Brush {
    val dark = next.dark
    val pairs =
        if (dark) {
            listOf(
                Color(0xFF44566B) to Color(0xFF283542),
                Color(0xFF5E4A52) to Color(0xFF382B31),
                Color(0xFF37564C) to Color(0xFF22352F),
                Color(0xFF4F4468) to Color(0xFF2F2940),
                Color(0xFF5C5340) to Color(0xFF373126),
            )
        } else {
            listOf(
                Color(0xFFD6DFEA) to Color(0xFFB9C7D8),
                Color(0xFFEADCD6) to Color(0xFFD6C0B6),
                Color(0xFFD5E6DC) to Color(0xFFB8D2C4),
                Color(0xFFE1DBEC) to Color(0xFFC7BEDC),
                Color(0xFFEDE4D2) to Color(0xFFD8CBB0),
            )
        }
    val (start, end) = pairs[((seed % pairs.size) + pairs.size) % pairs.size]
    return Brush.linearGradient(listOf(start, end))
}
