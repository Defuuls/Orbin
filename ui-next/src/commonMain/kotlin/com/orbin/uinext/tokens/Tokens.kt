package com.orbin.uinext.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared social reader spatial rhythm for ui-next.
 *
 * Precision 4dp/8dp grid with 16dp viewport gutters, 8dp modular cell gaps, and 1dp structural
 * hairline chassis borders.
 */
@Immutable
object NextSpace {
    val gutter: Dp = 16.dp
    val gutterTight: Dp = 16.dp
    val section: Dp = 28.dp
    val groupGap: Dp = 20.dp
    val rowY: Dp = 12.dp
    val rowX: Dp = 16.dp
    val titleTop: Dp = 12.dp
    val titleBottom: Dp = 12.dp
    val chromeInset: Dp = 16.dp
    val chromeBottom: Dp = 12.dp
    val pillPadX: Dp = 12.dp
    val pillPadY: Dp = 10.dp
    val sheetHandleTop: Dp = 12.dp
    val sheetPad: Dp = 24.dp
}

/**
 * Shared reader corner radii.
 *
 * Media stays edge-to-edge; search fields and sheets use restrained rounding.
 */
@Immutable
object NextRadius {
    val tight: Dp = 2.dp
    val control: Dp = 10.dp
    val tile: Dp = 4.dp
    val card: Dp = 6.dp
    val sheet: Dp = 16.dp
    val continuous: Dp = 6.dp
    val pill: Dp = 20.dp
}

/**
 * Orbin Coral radii for the incoming card-based chrome.
 *
 * These sit beside [NextRadius] so existing Clover-derived reader geometry stays intact
 * until each screen is restyled. Cards are generously rounded; search fields are pills;
 * thumbnails share a 12dp corner.
 */
@Immutable
object OrbinRadius {
    /** Thumbnails and compact media tiles. */
    val thumbnail: Dp = 12.dp

    /** White (or dark-elevated) content cards and settings groups. */
    val card: Dp = 16.dp

    /** Larger panels such as the settings profile header. */
    val panel: Dp = 20.dp

    /** Search fields and filter chips. */
    val search: Dp = 24.dp

    /** Reply-count and action pills. */
    val pill: Dp = 20.dp
}

/**
 * Soft ambient elevation for Coral cards and bars on a light page ground.
 *
 * Dark and AMOLED themes prefer a tonal lift ([OrbinCard] uses [OrbinElevation.card] as
 * tonal elevation and drops the drop-shadow) so cards do not pick up a harsh halo.
 */
@Immutable
object OrbinElevation {
    /** Resting content card. */
    val card: Dp = 2.dp

    /** Floating action or "tap to top" control. */
    val floating: Dp = 6.dp

    /** App bar and bottom navigation. */
    val bar: Dp = 4.dp
}

/**
 * Solid matte anodized surface fills — no translucency, no frosted glass, no blur.
 */
@Immutable
object NextMaterials {
    /** Press highlight alpha — crisp mechanical detent state layer. */
    const val PRESS_LIGHT = 0.08f
    const val PRESS_DARK = 0.16f

    /** Selected fill for nav indicator switches. */
    const val SELECTED_FILL_LIGHT = 0.14f
    const val SELECTED_FILL_DARK = 0.24f

    /** Scrim behind modal sheets. */
    const val SCRIM = 0.42f

    /** Subtle state-layer alpha for hover/focused states. */
    const val STATE_HOVER = 0.08f
    const val STATE_FOCUS = 0.12f
    const val STATE_PRESSED = 0.12f
    const val STATE_DRAGGED = 0.16f
}

/**
 * One proportional system font for post content, navigation, and metadata.
 */
@Immutable
private object NextTypeBase {
    private val sans = FontFamily.Default
    private val mono = FontFamily.Default
    private const val TNUM = "tnum"

    // Display — instrument bay & primary screen readouts
    val displayLarge =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-1.7).sp,
            fontFeatureSettings = TNUM,
        )
    val displayMedium =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.8).sp,
            fontFeatureSettings = TNUM,
        )
    val displaySmall =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.8).sp,
            fontFeatureSettings = TNUM,
        )

    // Headline — screen titles, section headers
    val headlineLarge =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.6).sp,
            fontFeatureSettings = TNUM,
        )
    val headlineMedium =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.4).sp,
            fontFeatureSettings = TNUM,
        )
    val headlineSmall =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.3).sp,
            fontFeatureSettings = TNUM,
        )

    // Title — card titles, row primary text
    val titleLarge =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.2).sp,
            fontFeatureSettings = TNUM,
        )
    val titleMedium =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = (-0.1).sp,
            fontFeatureSettings = TNUM,
        )
    val titleSmall =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
            fontFeatureSettings = TNUM,
        )

    // Body — post content, descriptions
    val bodyLarge =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            letterSpacing = 0.15.sp,
            fontFeatureSettings = TNUM,
        )
    val bodyMedium =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
            fontFeatureSettings = TNUM,
        )
    val bodySmall =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.2.sp,
            fontFeatureSettings = TNUM,
        )

    // Label — chips, badges, captions, nav labels, telemetry readouts
    val labelLarge =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.3.sp,
            fontFeatureSettings = TNUM,
        )
    val labelMedium =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
            fontFeatureSettings = TNUM,
        )
    val labelSmall =
        TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            letterSpacing = 0.6.sp,
            fontFeatureSettings = TNUM,
        )

    /** Dedicated monospace telemetry styles for board codes, post numbers, byte counts, and HUD tags. */
    val telemetry =
        TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            letterSpacing = 0.7.sp,
            fontFeatureSettings = TNUM,
        )

    val telemetryLarge =
        TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.5.sp,
            fontFeatureSettings = TNUM,
        )

    // Convenience aliases used in ui-next components

    /** Screen/section primary title. */
    val largeTitle = headlineSmall

    val title1 = headlineLarge
    val title2 = headlineMedium
    val title3 = headlineSmall

    /** Row primary text. */
    val body = bodyLarge

    /** Secondary row text, timestamps. */
    val footnote = bodySmall

    /** Navigation bar label, chip text. */
    val tab = labelMedium

    /** Section header — technical index header. */
    val sectionHeader = bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)

    /** Inline action label. */
    val callout = labelLarge

    /** Caption / badge text. */
    val caption1 = labelSmall

    /** Tight badge. */
    val caption2 = labelSmall

    /** Subheadline. */
    val subheadline = titleSmall

    /** Headline row text. */
    val headline = titleMedium

    /** Dense list primary text (14sp). */
    val bodyDense = bodyMedium

    /** Dense secondary text (12sp). */
    val footnoteDense = bodySmall

    /** Extra-small telemetry caption (11sp). */
    val captionDense = labelSmall
}

/** Shared type scale resolved against the bundled family installed by NextTheme. */
@Immutable
object NextType {
    val displayLarge: TextStyle
        @Composable get() = NextTypeBase.displayLarge.copy(fontFamily = LocalNextFontFamily.current)

    val displayMedium: TextStyle
        @Composable get() = NextTypeBase.displayMedium.copy(fontFamily = LocalNextFontFamily.current)

    val displaySmall: TextStyle
        @Composable get() = NextTypeBase.displaySmall.copy(fontFamily = LocalNextFontFamily.current)

    val headlineLarge: TextStyle
        @Composable get() = NextTypeBase.headlineLarge.copy(fontFamily = LocalNextFontFamily.current)

    val headlineMedium: TextStyle
        @Composable get() = NextTypeBase.headlineMedium.copy(fontFamily = LocalNextFontFamily.current)

    val headlineSmall: TextStyle
        @Composable get() = NextTypeBase.headlineSmall.copy(fontFamily = LocalNextFontFamily.current)

    val titleLarge: TextStyle
        @Composable get() = NextTypeBase.titleLarge.copy(fontFamily = LocalNextFontFamily.current)

    val titleMedium: TextStyle
        @Composable get() = NextTypeBase.titleMedium.copy(fontFamily = LocalNextFontFamily.current)

    val titleSmall: TextStyle
        @Composable get() = NextTypeBase.titleSmall.copy(fontFamily = LocalNextFontFamily.current)

    val bodyLarge: TextStyle
        @Composable get() = NextTypeBase.bodyLarge.copy(fontFamily = LocalNextFontFamily.current)

    val bodyMedium: TextStyle
        @Composable get() = NextTypeBase.bodyMedium.copy(fontFamily = LocalNextFontFamily.current)

    val bodySmall: TextStyle
        @Composable get() = NextTypeBase.bodySmall.copy(fontFamily = LocalNextFontFamily.current)

    val labelLarge: TextStyle
        @Composable get() = NextTypeBase.labelLarge.copy(fontFamily = LocalNextFontFamily.current)

    val labelMedium: TextStyle
        @Composable get() = NextTypeBase.labelMedium.copy(fontFamily = LocalNextFontFamily.current)

    val labelSmall: TextStyle
        @Composable get() = NextTypeBase.labelSmall.copy(fontFamily = LocalNextFontFamily.current)

    val telemetry: TextStyle
        @Composable get() = NextTypeBase.telemetry.copy(fontFamily = LocalNextFontFamily.current)

    val telemetryLarge: TextStyle
        @Composable get() = NextTypeBase.telemetryLarge.copy(fontFamily = LocalNextFontFamily.current)

    val largeTitle: TextStyle
        @Composable get() = NextTypeBase.largeTitle.copy(fontFamily = LocalNextFontFamily.current)

    val title1: TextStyle
        @Composable get() = NextTypeBase.title1.copy(fontFamily = LocalNextFontFamily.current)

    val title2: TextStyle
        @Composable get() = NextTypeBase.title2.copy(fontFamily = LocalNextFontFamily.current)

    val title3: TextStyle
        @Composable get() = NextTypeBase.title3.copy(fontFamily = LocalNextFontFamily.current)

    val body: TextStyle
        @Composable get() = NextTypeBase.body.copy(fontFamily = LocalNextFontFamily.current)

    val footnote: TextStyle
        @Composable get() = NextTypeBase.footnote.copy(fontFamily = LocalNextFontFamily.current)

    val tab: TextStyle
        @Composable get() = NextTypeBase.tab.copy(fontFamily = LocalNextFontFamily.current)

    val sectionHeader: TextStyle
        @Composable get() = NextTypeBase.sectionHeader.copy(fontFamily = LocalNextFontFamily.current)

    val callout: TextStyle
        @Composable get() = NextTypeBase.callout.copy(fontFamily = LocalNextFontFamily.current)

    val caption1: TextStyle
        @Composable get() = NextTypeBase.caption1.copy(fontFamily = LocalNextFontFamily.current)

    val caption2: TextStyle
        @Composable get() = NextTypeBase.caption2.copy(fontFamily = LocalNextFontFamily.current)

    val subheadline: TextStyle
        @Composable get() = NextTypeBase.subheadline.copy(fontFamily = LocalNextFontFamily.current)

    val headline: TextStyle
        @Composable get() = NextTypeBase.headline.copy(fontFamily = LocalNextFontFamily.current)

    val bodyDense: TextStyle
        @Composable get() = NextTypeBase.bodyDense.copy(fontFamily = LocalNextFontFamily.current)

    val footnoteDense: TextStyle
        @Composable get() = NextTypeBase.footnoteDense.copy(fontFamily = LocalNextFontFamily.current)

    val captionDense: TextStyle
        @Composable get() = NextTypeBase.captionDense.copy(fontFamily = LocalNextFontFamily.current)

    val material: Typography
        @Composable get() =
            Typography(
                displayLarge = displayLarge,
                displayMedium = displayMedium,
                displaySmall = displaySmall,
                headlineLarge = headlineLarge,
                headlineMedium = headlineMedium,
                headlineSmall = headlineSmall,
                titleLarge = titleLarge,
                titleMedium = titleMedium,
                titleSmall = titleSmall,
                bodyLarge = bodyLarge,
                bodyMedium = bodyMedium,
                bodySmall = bodySmall,
                labelLarge = labelLarge,
                labelMedium = labelMedium,
                labelSmall = labelSmall,
            )
}

/**
 * OPTIC-26 critically damped mechanical motion.
 *
 * Zero bounce (`DampingRatioNoBouncy`) across every transition, toggle, and dock switch so the UI
 * responds like a precision optical shutter rather than a playful spring.
 */
@Immutable
object NextMotion {
    /** Mechanical shutter easing — fast lock-in deceleration. */
    val Ease: Easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)

    /** Standard navigation push — critically damped, zero bounce. */
    val pushSpec: SpringSpec<Float> =
        SpringSpec(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )

    /** Primary tab crossfade — stiff, zero-overshoot mechanical switch. */
    val tabSpec: SpringSpec<Float> =
        SpringSpec(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )

    /** Control / switch actuation — high-stiffness detent with zero bounce. */
    val expressiveSpec: SpringSpec<Float> =
        SpringSpec(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh,
        )

    /** Chrome show/hide — quick, critically damped. */
    val chromeSpec: SpringSpec<Float> =
        SpringSpec(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )

    const val PUSH_MS = 220
    const val TAB_MS = 150
    const val CHROME_MS = 140

    const val TAB_NUDGE = 0.04f
}
