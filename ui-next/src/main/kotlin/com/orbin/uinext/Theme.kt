package com.orbin.uinext

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

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
 *
 * Every colour here that carries text clears WCAG AA's 4.5:1 against the ground it is drawn on.
 * That is not a free choice of alpha: [muted] and [faint] began at 0.54/0.34 and 0.56/0.34, which
 * measured 3.85:1 and 2.18:1 in light and 2.83:1 for dark's faint — so the timestamps, the reply
 * counts and every read thread were below the floor in all three themes. The alphas are now set
 * from the measurement rather than by eye, keeping a visible step between the three tiers.
 * `PaletteContrastTest` recomputes the ratios from these constants and fails if one drops.
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
    /** True only for the AMOLED ground, so a nested theme inherits that choice with the palette. */
    val amoled: Boolean = false,
)

internal val LightPalette =
    NextPalette(
        background = Color(0xFFFAF8F5),
        raised = Color(0xFFFFFFFF),
        ink = Color(0xFF16141A),
        muted = Color(0xFF16141A).copy(alpha = 0.72f),
        faint = Color(0xFF16141A).copy(alpha = 0.60f),
        hairline = Color(0xFF16141A).copy(alpha = 0.09f),
        accent = Color(0xFFA8431B),
        accentSoft = Color(0xFFA8431B).copy(alpha = 0.10f),
        dark = false,
    )

internal val DarkPalette =
    NextPalette(
        background = Color(0xFF0D0D11),
        raised = Color(0xFF17171D),
        ink = Color(0xFFF1EFF2),
        muted = Color(0xFFF1EFF2).copy(alpha = 0.68f),
        faint = Color(0xFFF1EFF2).copy(alpha = 0.54f),
        hairline = Color(0xFFF1EFF2).copy(alpha = 0.12f),
        accent = Color(0xFFF08A5A),
        accentSoft = Color(0xFFF08A5A).copy(alpha = 0.14f),
        dark = true,
    )

/**
 * The AMOLED ground: true black rather than the dark palette's near-black.
 *
 * Only the two surfaces change. The ink, the accent and the board hues are what the palette *is*,
 * and an OLED screen saving power on the background is not a reason to redraw them.
 */
internal val AmoledPalette =
    DarkPalette.copy(
        background = Color.Black,
        raised = Color(0xFF0A0A0A),
        amoled = true,
    )

val LocalNext = staticCompositionLocalOf { LightPalette }

/**
 * Whether the palette in scope was chosen by an enclosing [NextTheme] or is just [LocalNext]'s
 * default. Without it a nested theme cannot tell "somebody decided this" from "nobody has yet",
 * and every screen here wraps itself in a theme, so that distinction is the whole of nesting.
 */
private val LocalNextThemed = staticCompositionLocalOf { false }

/** The app's own font-size preference, so a nested theme inherits it as it inherits the palette. */
private val LocalNextFontScale = staticCompositionLocalOf { 1f }

/** Shorthand for the palette in scope. */
val next: NextPalette
    @Composable get() = LocalNext.current

/**
 * Every screen wraps itself in this rather than the shell wrapping all of them, so that each one
 * draws correctly wherever it is composed — including in a test that renders it on its own.
 *
 * That makes nesting the normal case rather than the exception, and every parameter here follows
 * the same rule: an explicit value wins, an enclosing theme's is inherited, and failing both there
 * is a default — the system for [darkTheme], off for [amoled], unscaled for [fontScale].
 * [darkTheme] used to be a plain `= false`, which meant an outer choice was overwritten by every
 * screen inside it and the whole app was light whatever the system or the settings said.
 *
 * This is how a reader's theme settings reach this module: the shell states them once, at the top,
 * and the screens below say nothing and inherit. A screen here has no view model and cannot read a
 * setting, for the same reason it takes rows rather than threads.
 *
 * What is deliberately not a parameter is dynamic color and the ported imageboard skins. This
 * module's palette is the argument it makes — warm ink on warm paper, one terracotta accent, a
 * colour per board — and recolouring it from the wallpaper would be the interface it replaced
 * wearing this one's layout. Those two settings still govern the Material surfaces around it: the
 * gallery, the onboarding wizard, dialogs and snackbars.
 */
@Composable
fun NextTheme(
    darkTheme: Boolean? = null,
    amoled: Boolean? = null,
    fontScale: Float? = null,
    content: @Composable () -> Unit,
) {
    val inherited = LocalNext.current.takeIf { LocalNextThemed.current }
    val dark = darkTheme ?: inherited?.dark ?: isSystemInDarkTheme()
    val black = amoled ?: (inherited?.amoled ?: false)
    val inheritedScale = LocalNextFontScale.current
    val scale = fontScale ?: inheritedScale
    val palette =
        when {
            dark && black -> AmoledPalette
            dark -> DarkPalette
            else -> LightPalette
        }
    val scheme =
        if (dark) {
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
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalNext provides palette,
        LocalNextThemed provides true,
        LocalNextFontScale provides scale,
        // Scaling the density is what makes every literal `sp` in this module obey the setting,
        // rather than threading a factor through each piece of text. Multiplied onto the scale
        // already in force rather than replacing it, so a reader who has enlarged text system-wide
        // does not have that undone by opening this app.
        //
        // Only the *change* is applied. A nested theme that inherits the scale inherits a density
        // the outer one has already scaled, and re-applying the factor there would compound it
        // once per screen — which is exactly what a module where every screen wraps itself in a
        // theme would do.
        LocalDensity provides Density(density.density, density.fontScale * scale / inheritedScale),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** One board colour: the light ground's value and the dark ground's. */
@Immutable
internal data class BoardHue(
    val light: Color,
    val dark: Color,
)

/**
 * The hues a board can be given.
 *
 * Ten rather than five because five was not enough to colour a real install: the first version
 * matched five 4chan board names and returned the accent for everything else, so across two
 * providers and dozens of boards almost every row came out the same terracotta and the premise
 * below quietly stopped holding. The first five are the colours those boards already shipped with.
 *
 * Every value clears 4.5:1 against both grounds, so a board label is legible whichever hue it
 * draws — `PaletteContrastTest` checks all twenty.
 */
internal val BoardHues =
    listOf(
        BoardHue(light = Color(0xFF2C6BC4), dark = Color(0xFF74A9F8)), // blue
        // Darkened from #B07708, which measured 3.61:1 on the light ground — the one hue of the
        // original five that missed the floor the others clear comfortably.
        BoardHue(light = Color(0xFF8F6206), dark = Color(0xFFE9B54C)), // amber
        BoardHue(light = Color(0xFF1B7A55), dark = Color(0xFF5FC79A)), // green
        BoardHue(light = Color(0xFF6D45C0), dark = Color(0xFFB18CF0)), // violet
        BoardHue(light = Color(0xFFB83A6E), dark = Color(0xFFEE87B4)), // magenta
        BoardHue(light = Color(0xFF116C74), dark = Color(0xFF5CC6D0)), // teal
        BoardHue(light = Color(0xFFA6491F), dark = Color(0xFFEE9468)), // rust
        BoardHue(light = Color(0xFF4A54C6), dark = Color(0xFF93A0F5)), // indigo
        BoardHue(light = Color(0xFF5F6F14), dark = Color(0xFFB6CB55)), // olive
        BoardHue(light = Color(0xFF8C3A8C), dark = Color(0xFFD98BD9)), // plum
    )

/**
 * The boards that shipped with a fixed colour, kept on it.
 *
 * Hashing alone would reshuffle these, and a reader who has learned that /g/ is the blue one has
 * earned not having that taken away by an update. New boards hash; these five are pinned.
 */
private val PinnedBoardHues =
    mapOf("/g/" to 0, "/ck/" to 1, "/p/" to 2, "/lit/" to 3, "/aco/" to 4)

/**
 * Which hue a board draws in, as an index into [BoardHues].
 *
 * `String.hashCode` is specified by the JDK rather than left to the implementation, so a board
 * keeps its colour across launches, devices and releases — which is the whole point of colouring
 * by board. `Int.mod` rather than `%` because the remainder of a negative hash is negative, and
 * `Int.MIN_VALUE.absoluteValue` is still negative.
 */
internal fun boardHueIndex(board: String): Int = PinnedBoardHues[board] ?: board.hashCode().mod(BoardHues.size)

/**
 * A board's hue.
 *
 * A merged feed is a pile of unrelated boards, and the only thing distinguishing one row's origin
 * from another's today is four grey characters. A colour per board makes the mix legible at a
 * glance without adding a second line to any row — for every board, not just the five the first
 * version knew by name.
 */
@Composable
fun boardHue(board: String): Color {
    val hue = BoardHues[boardHueIndex(board)]
    return if (next.dark) hue.dark else hue.light
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
