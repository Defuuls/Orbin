package com.orbin.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * M3 tonal palette seeded from soft eggplant (#7B5C8B).
 *
 * Generated via Material HCT (Hue-Chroma-Tone) algorithm, matching Material Theme Builder
 * output for a purple-adjacent eggplant hue at medium chroma. Dynamic color (Android 12+)
 * is preferred when enabled; these tokens feed the static fallback schemes.
 *
 * Light scheme — 40/90/80 tone convention for primary/container/on-container.
 */

// ── Primary (eggplant purple) ──────────────────────────────────────────────
internal val EggplantPrimary = Color(0xFF7B4F8A)
internal val EggplantOnPrimary = Color(0xFFFFFFFF)
internal val EggplantPrimaryContainer = Color(0xFFF3DAFF)
internal val EggplantOnPrimaryContainer = Color(0xFF2E0042)

// ── Secondary (muted mauve) ────────────────────────────────────────────────
internal val EggplantSecondary = Color(0xFF6B5874)
internal val EggplantOnSecondary = Color(0xFFFFFFFF)
internal val EggplantSecondaryContainer = Color(0xFFF3DBFC)
internal val EggplantOnSecondaryContainer = Color(0xFF26152E)

// ── Tertiary (dusty rose complement) ──────────────────────────────────────
internal val EggplantTertiary = Color(0xFF82525E)
internal val EggplantOnTertiary = Color(0xFFFFFFFF)
internal val EggplantTertiaryContainer = Color(0xFFFFD9E1)
internal val EggplantOnTertiaryContainer = Color(0xFF33101A)

// ── Error ──────────────────────────────────────────────────────────────────
internal val EggplantError = Color(0xFFBA1A1A)
internal val EggplantOnError = Color(0xFFFFFFFF)
internal val EggplantErrorContainer = Color(0xFFFFDAD6)
internal val EggplantOnErrorContainer = Color(0xFF410002)

// ── Neutral backgrounds (light) ────────────────────────────────────────────
internal val EggplantBackground = Color(0xFFFFFBFF)
internal val EggplantOnBackground = Color(0xFF1D1B1E)
internal val EggplantSurface = Color(0xFFFFFBFF)
internal val EggplantOnSurface = Color(0xFF1D1B1E)
internal val EggplantSurfaceVariant = Color(0xFFEBDEEC)
internal val EggplantOnSurfaceVariant = Color(0xFF4D4050)
internal val EggplantOutline = Color(0xFF7E7081)
internal val EggplantOutlineVariant = Color(0xFFCEC2CF)
internal val EggplantInverseSurface = Color(0xFF323033)
internal val EggplantInverseOnSurface = Color(0xFFF5EFF5)
internal val EggplantInversePrimary = Color(0xFFDFACF0)
internal val EggplantScrim = Color(0xFF000000)

// ── Surface containers (light) — flat matte fills ─────────────────────────
internal val EggplantSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val EggplantSurfaceContainerLow = Color(0xFFF9F3F9)
internal val EggplantSurfaceContainer = Color(0xFFF3EDF3)
internal val EggplantSurfaceContainerHigh = Color(0xFFEDE7ED)
internal val EggplantSurfaceContainerHighest = Color(0xFFE7E1E7)

// ── Dark scheme ────────────────────────────────────────────────────────────
internal val EggplantDarkPrimary = Color(0xFFDFACF0)
internal val EggplantDarkOnPrimary = Color(0xFF481A5A)
internal val EggplantDarkPrimaryContainer = Color(0xFF613472)
internal val EggplantDarkOnPrimaryContainer = Color(0xFFF3DAFF)

internal val EggplantDarkSecondary = Color(0xFFD7BDDF)
internal val EggplantDarkOnSecondary = Color(0xFF3B2A44)
internal val EggplantDarkSecondaryContainer = Color(0xFF53405C)
internal val EggplantDarkOnSecondaryContainer = Color(0xFFF3DBFC)

internal val EggplantDarkTertiary = Color(0xFFF5B7C4)
internal val EggplantDarkOnTertiary = Color(0xFF4D222D)
internal val EggplantDarkTertiaryContainer = Color(0xFF673A45)
internal val EggplantDarkOnTertiaryContainer = Color(0xFFFFD9E1)

internal val EggplantDarkError = Color(0xFFFFB4AB)
internal val EggplantDarkOnError = Color(0xFF690005)
internal val EggplantDarkErrorContainer = Color(0xFF93000A)
internal val EggplantDarkOnErrorContainer = Color(0xFFFFDAD6)

internal val EggplantDarkBackground = Color(0xFF000000)
internal val EggplantDarkOnBackground = Color(0xFFE8E0E9)
internal val EggplantDarkSurface = Color(0xFF000000)
internal val EggplantDarkOnSurface = Color(0xFFE8E0E9)
internal val EggplantDarkSurfaceVariant = Color(0xFF4D4050)
internal val EggplantDarkOnSurfaceVariant = Color(0xFFCFC2CF)
internal val EggplantDarkOutline = Color(0xFF988D9B)
internal val EggplantDarkOutlineVariant = Color(0xFF4D4050)
internal val EggplantDarkInverseSurface = Color(0xFFE8E0E9)
internal val EggplantDarkInverseOnSurface = Color(0xFF323033)
internal val EggplantDarkInversePrimary = Color(0xFF7B4F8A)

// ── Dark surface containers — deep, matte, no blur ────────────────────────
internal val EggplantDarkSurfaceContainerLowest = Color(0xFF0F0D11)
internal val EggplantDarkSurfaceContainerLow = Color(0xFF1D1B1E)
internal val EggplantDarkSurfaceContainer = Color(0xFF221F24)
internal val EggplantDarkSurfaceContainerHigh = Color(0xFF2D2A2E)
internal val EggplantDarkSurfaceContainerHighest = Color(0xFF383439)

// ── AMOLED override (true black ground) ───────────────────────────────────
internal val AmoledBackground = Color(0xFF000000)
internal val AmoledSurfaceContainerLowest = Color(0xFF000000)
internal val AmoledSurfaceContainerLow = Color(0xFF0D0A0F)
internal val AmoledSurfaceContainer = Color(0xFF141116)
internal val AmoledSurfaceContainerHigh = Color(0xFF1E1B20)
internal val AmoledSurfaceContainerHighest = Color(0xFF28252A)

// ── Semantic accents (post renderer) ──────────────────────────────────────
/** Imageboard greentext — kept period-accurate; shifts toward eggplant's warm complement. */
val GreentextColor = Color(0xFF5A7A2A)

/** Quote link — uses the eggplant primary at legible lightness on white. */
val QuoteLinkColor = Color(0xFF7B4F8A)

/** Spoiler tag — near-black fill, same as before. */
val SpoilerBackground = Color(0xFF2B2B2B)

// ── Status accents ─────────────────────────────────────────────────────────
val SuccessColor = Color(0xFF2E7D32)
val WarningColor = Color(0xFFB26A00)
val InfoColor = EggplantPrimary
