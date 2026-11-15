package com.orbin.uinext

import com.orbin.uinext.tokens.ReaderAmoledColors
import com.orbin.uinext.tokens.ReaderColors
import com.orbin.uinext.tokens.ReaderDarkColors
import com.orbin.uinext.tokens.ReaderLightColors
import com.orbin.uinext.tokens.readerColors

/** The shared rounded-reader palette family, selected by brightness and AMOLED mode. */
fun canonicalNextPalette(
    dark: Boolean,
    amoled: Boolean,
    accentTheme: NextAccentTheme = NextAccentTheme.ORBIN,
): NextPalette =
    if (accentTheme != NextAccentTheme.ORBIN) {
        readerColors(dark, amoled, accentTheme).asNextPalette(dark, dark && amoled, accentTheme)
    } else {
        when {
            dark && amoled -> AmoledPalette
            dark -> DarkPalette
            else -> LightPalette
        }
    }

/** Unknown future preference values retain Orbin's default appearance. */
fun canonicalNextPalette(
    dark: Boolean,
    amoled: Boolean,
    accentTheme: String,
): NextPalette =
    canonicalNextPalette(
        dark,
        amoled,
        NextAccentTheme.entries.firstOrNull { it.name == accentTheme } ?: NextAccentTheme.ORBIN,
    )

/**
 * Compatibility roles for screens that still read `next`, mapped only from reader color tokens:
 * background is the page ground; raised and elevated are the solid card/dialog ground; ink is ink;
 * muted and faint are inkMuted; hairline is hairline; accent and onAccent keep their names;
 * accentSoft and accentContainer are selected, with ink as onAccentContainer;
 * destructive is error, with background as its filled-control foreground. The app bar,
 * placeholder, quote, author and reference roles retain their reader names explicitly.
 *
 * AMOLED page grounds are black, with lifted near-black cards and the Orbin cyan accent.
 */
private fun ReaderColors.asNextPalette(
    dark: Boolean,
    amoled: Boolean = false,
    accentTheme: NextAccentTheme = NextAccentTheme.ORBIN,
): NextPalette =
    NextPalette(
        background = background,
        raised = surfaceVariant,
        elevated = surfaceVariant,
        ink = ink,
        muted = inkMuted,
        faint = inkMuted,
        hairline = hairline,
        accent = accent,
        accentSoft = selected,
        onAccent = onAccent,
        accentContainer = selected,
        onAccentContainer = ink,
        destructive = error,
        onDestructive = background,
        appBar = appBar,
        onAppBar = onAppBar,
        surfaceVariant = surfaceVariant,
        quote = quote,
        author = author,
        quoteLink = quoteLink,
        dark = dark,
        amoled = amoled,
        accentTheme = accentTheme,
    )

internal val LightPalette = ReaderLightColors.asNextPalette(dark = false)
internal val DarkPalette = ReaderDarkColors.asNextPalette(dark = true)
internal val AmoledPalette = ReaderAmoledColors.asNextPalette(dark = true, amoled = true)
