package com.orbin.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand color tokens and the static (non-dynamic) Material 3 color schemes. Dynamic color is
 * preferred on Android 12+ when enabled; these schemes are the fallback and the basis for the
 * AMOLED variant.
 */

internal val OrbinPrimary = Color(0xFF4F6BED)
internal val OrbinSecondary = Color(0xFF5B6471)
internal val OrbinTertiary = Color(0xFF7A5CA8)
internal val OrbinError = Color(0xFFBA1A1A)

// Semantic accents used by the post renderer (greentext, quote links, spoilers).
val GreentextColor = Color(0xFF789922)
val QuoteLinkColor = Color(0xFF4F6BED)
val SpoilerBackground = Color(0xFF2B2B2B)
