package com.orbin.uinext.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

/** Canonical corner roles for Coral chrome and the retained compact reader geometry. */
@Immutable
object OrbinRadius {
    /** Compact reader thumbnails. */
    val compact: Dp = 2.dp

    /** Skeleton bars and small helper corners. */
    val small: Dp = 4.dp

    /** Compact grouped controls. */
    val compactCard: Dp = 6.dp

    /** Form and inline controls. */
    val control: Dp = 10.dp

    /** Coral thumbnail tiles. */
    val thumbnail: Dp = 12.dp

    /** Coral content cards. */
    val card: Dp = 16.dp

    /** Dialog and sheet surfaces. */
    val sheet: Dp = 16.dp

    /** Larger feature panels. */
    val panel: Dp = 20.dp

    /** Action and counter pills. */
    val pill: Dp = 20.dp

    /** Pill search and filter fields. */
    val search: Dp = 24.dp
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
