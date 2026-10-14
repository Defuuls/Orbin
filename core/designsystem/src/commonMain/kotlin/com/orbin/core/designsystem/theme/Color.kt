package com.orbin.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/*
 * M3 tonal palette seeded from forest green (#228B22 / deeper #1F5D3A).
 *
 * Generated in the Material HCT (Hue-Chroma-Tone) structure, matching Material Theme Builder
 * output for a forest-green hue at medium chroma. Dynamic color (Android 12+)
 * is preferred when enabled; these tokens feed the static fallback schemes.
 *
 * Light scheme — 40/90/80 tone convention for primary/container/on-container.
 */

internal val ForestPrimary = Color(0xFF276A3C)
internal val ForestOnPrimary = Color(0xFFFFFFFF)
internal val ForestPrimaryContainer = Color(0xFFC8EAD4)
internal val ForestOnPrimaryContainer = Color(0xFF00210E)

// ── Secondary (muted sage) ─────────────────────────────────────────────────
internal val ForestSecondary = Color(0xFF516352)
internal val ForestOnSecondary = Color(0xFFFFFFFF)
internal val ForestSecondaryContainer = Color(0xFFD4E8D6)
internal val ForestOnSecondaryContainer = Color(0xFF0F1F13)

// ── Tertiary (warm earth complement) ───────────────────────────────────────
internal val ForestTertiary = Color(0xFF7A5C36)
internal val ForestOnTertiary = Color(0xFFFFFFFF)
internal val ForestTertiaryContainer = Color(0xFFFFDDB6)
internal val ForestOnTertiaryContainer = Color(0xFF2B1700)

// ── Error ──────────────────────────────────────────────────────────────────
internal val ForestError = Color(0xFFBA1A1A)
internal val ForestOnError = Color(0xFFFFFFFF)
internal val ForestErrorContainer = Color(0xFFFFDAD6)
internal val ForestOnErrorContainer = Color(0xFF410002)

// ── Neutral backgrounds (light) ────────────────────────────────────────────
internal val ForestBackground = Color(0xFFF7FBF7)
internal val ForestOnBackground = Color(0xFF191C19)
internal val ForestSurface = Color(0xFFF7FBF7)
internal val ForestOnSurface = Color(0xFF191C19)
internal val ForestSurfaceVariant = Color(0xFFDCE5DB)
internal val ForestOnSurfaceVariant = Color(0xFF404941)
internal val ForestOutline = Color(0xFF717970)
internal val ForestOutlineVariant = Color(0xFFC0C9BF)
internal val ForestInverseSurface = Color(0xFF2E312E)
internal val ForestInverseOnSurface = Color(0xFFEFF1EC)
internal val ForestInversePrimary = Color(0xFFA3D4AF)
internal val ForestScrim = Color(0xFF000000)

// ── Surface containers (light) — flat matte fills ─────────────────────────
internal val ForestSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val ForestSurfaceContainerLow = Color(0xFFF1F7F1)
internal val ForestSurfaceContainer = Color(0xFFECF2EC)
internal val ForestSurfaceContainerHigh = Color(0xFFE6EDE6)
internal val ForestSurfaceContainerHighest = Color(0xFFE0E8E0)

// ── Dark scheme ────────────────────────────────────────────────────────────
internal val ForestDarkPrimary = Color(0xFFA3D4AF)
internal val ForestDarkOnPrimary = Color(0xFF00391A)
internal val ForestDarkPrimaryContainer = Color(0xFF1F5D3A)
internal val ForestDarkOnPrimaryContainer = Color(0xFFC8EAD4)

internal val ForestDarkSecondary = Color(0xFFB8CCBA)
internal val ForestDarkOnSecondary = Color(0xFF243526)
internal val ForestDarkSecondaryContainer = Color(0xFF3A4B3C)
internal val ForestDarkOnSecondaryContainer = Color(0xFFD4E8D6)

internal val ForestDarkTertiary = Color(0xFFECC194)
internal val ForestDarkOnTertiary = Color(0xFF442B08)
internal val ForestDarkTertiaryContainer = Color(0xFF5F421D)
internal val ForestDarkOnTertiaryContainer = Color(0xFFFFDDB6)

internal val ForestDarkError = Color(0xFFFFB4AB)
internal val ForestDarkOnError = Color(0xFF690005)
internal val ForestDarkErrorContainer = Color(0xFF93000A)
internal val ForestDarkOnErrorContainer = Color(0xFFFFDAD6)

internal val ForestDarkBackground = Color(0xFF101A12)
internal val ForestDarkOnBackground = Color(0xFFE0E8E0)
internal val ForestDarkSurface = Color(0xFF101A12)
internal val ForestDarkOnSurface = Color(0xFFE0E8E0)
internal val ForestDarkSurfaceVariant = Color(0xFF404941)
internal val ForestDarkOnSurfaceVariant = Color(0xFFC0C9BF)
internal val ForestDarkOutline = Color(0xFF8A9389)
internal val ForestDarkOutlineVariant = Color(0xFF404941)
internal val ForestDarkInverseSurface = Color(0xFFE0E8E0)
internal val ForestDarkInverseOnSurface = Color(0xFF2E312E)
internal val ForestDarkInversePrimary = Color(0xFF276A3C)

// ── Dark surface containers — deep forest, matte, no blur ─────────────────
// The ground is a dark forest green, not black: true black is the AMOLED option below.
internal val ForestDarkSurfaceContainerLowest = Color(0xFF0B140D)
internal val ForestDarkSurfaceContainerLow = Color(0xFF182019)
internal val ForestDarkSurfaceContainer = Color(0xFF1C271E)
internal val ForestDarkSurfaceContainerHigh = Color(0xFF263128)
internal val ForestDarkSurfaceContainerHighest = Color(0xFF313C32)

// ── AMOLED override (true black ground) ───────────────────────────────────
internal val AmoledBackground = Color(0xFF000000)
internal val AmoledSurfaceContainerLowest = Color(0xFF000000)
internal val AmoledSurfaceContainerLow = Color(0xFF0A0F0B)
internal val AmoledSurfaceContainer = Color(0xFF111611)
internal val AmoledSurfaceContainerHigh = Color(0xFF1B201B)
internal val AmoledSurfaceContainerHighest = Color(0xFF252A25)

// ── Semantic accents (post renderer) ──────────────────────────────────────

/** Imageboard greentext — kept period-accurate. */
val GreentextColor = Color(0xFF5A7A2A)

/** Quote link — uses the forest primary at legible lightness on white. */
val QuoteLinkColor = Color(0xFF276A3C)

/** Spoiler tag — near-black fill, same as before. */
val SpoilerBackground = Color(0xFF2B2B2B)

// ── Status accents ─────────────────────────────────────────────────────────
val SuccessColor = Color(0xFF2E7D32)
val WarningColor = Color(0xFFB26A00)
val InfoColor = ForestPrimary
