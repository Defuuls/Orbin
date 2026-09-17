package com.orbin.uinext.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Apple-inspired spatial rhythm for ui-next.
 *
 * Generous padding and inset grouped sections — Settings-style cards — rather than dense Material
 * lists. Values are deliberate: the previous module used 20dp gutters and hairline-only separation;
 * this scale adds room between groups while keeping rows themselves calm.
 */
@Immutable
object NextSpace {
    val gutter: Dp = 16.dp
    val gutterTight: Dp = 16.dp
    val section: Dp = 22.dp
    val groupGap: Dp = 20.dp
    val rowY: Dp = 12.dp
    val rowX: Dp = 16.dp
    val titleTop: Dp = 12.dp
    val titleBottom: Dp = 20.dp
    val chromeInset: Dp = 14.dp
    val chromeBottom: Dp = 12.dp
    val pillPadX: Dp = 10.dp
    val pillPadY: Dp = 8.dp
    val sheetHandleTop: Dp = 10.dp
    val sheetPad: Dp = 20.dp
}

/**
 * Continuous corner radii — soft, large, never Material's sharp chips.
 *
 * Prefer [continuous] / [card] for surfaces and [pill] for chrome. Avoid hairline borders around
 * every container; elevation and fill do the work.
 */
@Immutable
object NextRadius {
    val tight: Dp = 10.dp
    val control: Dp = 12.dp
    val tile: Dp = 16.dp
    val card: Dp = 14.dp
    val sheet: Dp = 22.dp
    val continuous: Dp = 26.dp
    val pill: Dp = 100.dp
}

/**
 * Translucent materials for chrome and sheets.
 *
 * Chrome uses [BLUR_RADIUS] with RenderEffect on API 31+ (our minSdk) via [nextFrosted]; denser
 * alphas remain the fallback when blur is skipped. True content-sampling backdrop blur still needs a
 * haze host around scrolling lists.
 */
@Immutable
object NextMaterials {
    const val BAR_FILL_LIGHT = 0.82f
    const val BAR_FILL_DARK = 0.72f

    /** More translucent when a chrome blur is applied on API 31+. */
    const val BAR_FILL_LIGHT_BLUR = 0.62f
    const val BAR_FILL_DARK_BLUR = 0.52f
    const val SCRIM = 0.36f
    const val HIGHLIGHT = 0.08f
    const val SELECTED_FILL_LIGHT = 0.12f
    const val SELECTED_FILL_DARK = 0.22f
    const val PRESS_LIGHT = 0.06f
    const val PRESS_DARK = 0.14f
    val BLUR_RADIUS = 22.dp
}

/**
 * SF-adjacent type ramp using the platform default (variable) sans.
 *
 * Large titles, clear hierarchy, comfortable tracking — not noisy Material labels. Letter spacing
 * is negative at display sizes (the main tell that a heading was drawn rather than defaulted).
 */
@Immutable
object NextType {
    private val sans = FontFamily.SansSerif

    val largeTitle =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            lineHeight = 41.sp,
            letterSpacing = (-1.1).sp,
        )
    val title1 =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            letterSpacing = (-0.9).sp,
        )
    val title2 =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.6).sp,
        )
    val title3 =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 25.sp,
            letterSpacing = (-0.25).sp,
        )
    val headline =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            letterSpacing = (-0.43).sp,
        )
    val body =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            letterSpacing = (-0.43).sp,
        )
    val callout =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 21.sp,
            letterSpacing = (-0.15).sp,
        )
    val subheadline =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            letterSpacing = (-0.1).sp,
        )
    val footnote =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = (-0.05).sp,
        )
    val caption1 =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.sp,
        )
    val caption2 =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            letterSpacing = 0.2.sp,
        )
    val tab =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            letterSpacing = (-0.1).sp,
        )
    val sectionHeader =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            letterSpacing = (-0.05).sp,
        )
}

/**
 * Motion for Next chrome and navigation — soft iOS pushes, not Material shared-axis theatre.
 *
 * Prefer these curves over linear tweens or Material motion tokens. Hierarchical pushes use
 * [PUSH_MS] with a parallax exit; primary-tab swaps use the shorter [TAB_MS] fade.
 */
@Immutable
object NextMotion {
    const val PUSH_MS = 420
    const val TAB_MS = 240
    const val CHROME_MS = 220

    /** Fraction of width the outgoing screen drifts on a hierarchical push/pop (iOS parallax). */
    const val PUSH_PARALLAX = 0.24f

    /** Subtle horizontal nudge when swapping primary tabs. */
    const val TAB_NUDGE = 0.06f

    /** iOS navigation-style ease — soft settle, no Material linear snap. */
    val Ease: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
}
