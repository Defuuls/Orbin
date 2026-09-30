package com.orbin.uinext

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

/**
 * Fallback static palette (used when M3 color scheme is not yet available, e.g. preview context).
 * These mirror the eggplant static scheme values from Color.kt.
 */
internal val LightPalette =
    NextPalette(
        background = Color(0xFFFFFBFF),
        raised = Color(0xFFF9F3F9),
        elevated = Color(0xFFF3EDF3),
        ink = Color(0xFF1D1B1E),
        muted = Color(0xFF1D1B1E).copy(alpha = 0.75f),
        faint = Color(0xFF1D1B1E).copy(alpha = 0.62f),
        hairline = Color(0xFFCEC2CF),
        accent = Color(0xFF7B4F8A),
        accentSoft = Color(0xFFF3DAFF),
        onAccent = Color(0xFFFFFFFF),
        accentContainer = Color(0xFFF3DAFF),
        onAccentContainer = Color(0xFF2E0042),
        dark = false,
        amoled = false,
    )

internal val DarkPalette =
    NextPalette(
        background = Color(0xFF000000),
        raised = Color(0xFF1D1B1E),
        elevated = Color(0xFF221F24),
        ink = Color(0xFFE8E0E9),
        muted = Color(0xFFE8E0E9).copy(alpha = 0.75f),
        faint = Color(0xFFE8E0E9).copy(alpha = 0.60f),
        hairline = Color(0xFF4D4050),
        accent = Color(0xFFDFACF0),
        accentSoft = Color(0xFF613472),
        onAccent = Color(0xFF481A5A),
        accentContainer = Color(0xFF613472),
        onAccentContainer = Color(0xFFF3DAFF),
        dark = true,
        amoled = false,
    )

internal val AmoledPalette = DarkPalette.copy(amoled = true)
