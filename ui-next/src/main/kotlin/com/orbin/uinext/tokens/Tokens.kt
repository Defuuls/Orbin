package com.orbin.uinext.tokens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * M3-aligned spatial rhythm for ui-next.
 *
 * Based on the Material 3 4dp/8dp grid. 16dp screen margins, 8dp between related items,
 * 24dp between sections. Replaced the Apple-style grouped-sections metaphor with a flat
 * card-on-surface layout where tonal fills create hierarchy instead of inset depth.
 */
@Immutable
object NextSpace {
    val gutter: Dp = 16.dp
    val gutterTight: Dp = 16.dp
    val section: Dp = 24.dp
    val groupGap: Dp = 16.dp
    val rowY: Dp = 14.dp
    val rowX: Dp = 16.dp
    val titleTop: Dp = 8.dp
    val titleBottom: Dp = 16.dp
    val chromeInset: Dp = 16.dp
    val chromeBottom: Dp = 12.dp
    val pillPadX: Dp = 12.dp
    val pillPadY: Dp = 10.dp
    val sheetHandleTop: Dp = 12.dp
    val sheetPad: Dp = 24.dp
}

/**
 * M3 Expressive corner radii — large, open, no sharp edges.
 *
 * M3 Expressive pushes corners significantly larger than the original M3 baseline.
 * Cards use [card]; interactive chips/pills use [pill]; sheets use [sheet].
 */
@Immutable
object NextRadius {
    val tight: Dp = 8.dp
    val control: Dp = 12.dp
    val tile: Dp = 20.dp
    val card: Dp = 24.dp
    val sheet: Dp = 28.dp
    val continuous: Dp = 28.dp
    val pill: Dp = 100.dp
}

/**
 * Solid matte surface fills — no translucency, no frosted glass, no blur.
 *
 * M3 uses tonal elevation (a colored fill derived from primary) rather than shadows or
 * transparency to convey surface hierarchy. All values here are fully opaque.
 */
@Immutable
object NextMaterials {
    /** Press highlight alpha — used by M3-style ripple indication. */
    const val PRESS_LIGHT = 0.08f
    const val PRESS_DARK = 0.16f

    /** Selected fill for nav indicator pills. */
    const val SELECTED_FILL_LIGHT = 0.14f
    const val SELECTED_FILL_DARK = 0.24f

    /** Scrim behind modal sheets. */
    const val SCRIM = 0.32f

    /** Subtle state-layer alpha for hover/focused states (M3 state layer). */
    const val STATE_HOVER = 0.08f
    const val STATE_FOCUS = 0.12f
    const val STATE_PRESSED = 0.12f
    const val STATE_DRAGGED = 0.16f
}

/**
 * M3 typography scale — Roboto (platform default SansSerif), M3 type roles.
 *
 * Replaced the Apple SF-adjacent ramp (aggressive negative tracking, iOS size names)
 * with M3 type role names and letterspacing values tuned for Roboto rather than SF Pro.
 * Display/Headline roles use slightly negative tracking at large sizes (Roboto's optical
 * recommendation); Body and Label are at default 0sp.
 */
@Immutable
object NextType {
    private val sans = FontFamily.SansSerif

    // Display — M3 displayLarge / displayMedium / displaySmall
    val displayLarge =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-0.25).sp,
        )
    val displayMedium =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 45.sp,
            lineHeight = 52.sp,
            letterSpacing = 0.sp,
        )
    val displaySmall =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            letterSpacing = 0.sp,
        )

    // Headline — screen titles, section headers
    val headlineLarge =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp,
        )
    val headlineMedium =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp,
        )
    val headlineSmall =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp,
        )

    // Title — card titles, row primary text
    val titleLarge =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
        )
    val titleMedium =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp,
        )
    val titleSmall =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        )

    // Body — post content, descriptions
    val bodyLarge =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
        )
    val bodyMedium =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp,
        )
    val bodySmall =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
        )

    // Label — chips, badges, captions, nav labels
    val labelLarge =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        )
    val labelMedium =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        )
    val labelSmall =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        )

    // Convenience aliases used in ui-next components
    /** Screen/section primary title (maps to headlineMedium). */
    val largeTitle = headlineMedium

    /** Row primary text (maps to bodyLarge). */
    val body = bodyLarge

    /** Secondary row text, timestamps (maps to bodySmall). */
    val footnote = bodySmall

    /** Navigation bar label, chip text (maps to labelMedium). */
    val tab = labelMedium

    /** Section header — sentence case, M3 label style (maps to labelSmall). */
    val sectionHeader = labelSmall

    /** Inline action label (maps to labelLarge). */
    val callout = labelLarge

    /** Caption / badge text (maps to labelSmall). */
    val caption1 = labelSmall

    /** Tight badge (maps to labelSmall). */
    val caption2 = labelSmall

    /** Subheadline (maps to titleSmall). */
    val subheadline = titleSmall

    /** Headline row text (maps to titleMedium). */
    val headline = titleMedium
}

/**
 * M3 spring motion — replaces the iOS parallax cubic-bezier push.
 *
 * Material 3 Expressive uses spring physics with perceptible bounce for taps and
 * navigation. No fixed millisecond durations — spring settling time is emergent.
 * PUSH_PARALLAX is removed entirely (no iOS-style outgoing screen drift on Android).
 */
@Immutable
object NextMotion {
    /** Standard navigation push — medium-low stiffness, no bounce. */
    val pushSpec: SpringSpec<Float> =
        SpringSpec(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

    /** Primary tab crossfade — slightly stiffer for snappy feel. */
    val tabSpec: SpringSpec<Float> =
        SpringSpec(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )

    /** FAB / button expansion — medium bounce for M3 Expressive expressiveness. */
    val expressiveSpec: SpringSpec<Float> =
        SpringSpec(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        )

    /** Chrome show/hide — quick, no bounce. */
    val chromeSpec: SpringSpec<Float> =
        SpringSpec(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

    // Legacy numeric constants — kept for call sites that haven't migrated to SpringSpec yet.
    // TODO: remove once all animation calls use spec directly.
    const val PUSH_MS = 350
    const val TAB_MS = 200
    const val CHROME_MS = 180
}
