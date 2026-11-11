package com.orbin.uinext

import com.orbin.uinext.tokens.ReaderAmoledColors
import com.orbin.uinext.tokens.ReaderColors
import com.orbin.uinext.tokens.ReaderDarkColors
import com.orbin.uinext.tokens.ReaderLightColors

/** The reader design has one palette family, selected only by brightness and AMOLED mode. */
fun canonicalNextPalette(
    dark: Boolean,
    amoled: Boolean,
): NextPalette =
    when {
        dark && amoled -> AmoledPalette
        dark -> DarkPalette
        else -> LightPalette
    }

/**
 * Compatibility roles for screens that still read `next`, mapped only from reader color tokens:
 * background, raised and elevated are the same screen/card/dialog ground; ink is ink;
 * muted and faint are inkMuted; hairline is hairline; accent and onAccent keep their names;
 * accentSoft and accentContainer are selected, with ink as onAccentContainer;
 * destructive is error, with background as its filled-control foreground. The app bar,
 * placeholder, quote, author and reference roles retain their reader names explicitly.
 *
 * AMOLED grounds are black. Placeholder and selection fills retain their distinct token values.
 */
private fun ReaderColors.asNextPalette(
    dark: Boolean,
    amoled: Boolean = false,
): NextPalette =
    NextPalette(
        background = background,
        raised = background,
        elevated = background,
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
    )

internal val LightPalette = ReaderLightColors.asNextPalette(dark = false)
internal val DarkPalette = ReaderDarkColors.asNextPalette(dark = true)
internal val AmoledPalette = ReaderAmoledColors.asNextPalette(dark = true, amoled = true)
