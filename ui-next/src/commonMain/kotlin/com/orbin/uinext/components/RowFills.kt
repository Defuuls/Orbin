package com.orbin.uinext.components

import androidx.compose.foundation.background
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * Whether the shaped fills of scrolling rows (label pills, media labels, file badges and
 * placeholder tiles) clip to their shape before filling.
 *
 * Android keeps the clip, drawing exactly as it always has. iOS paints the shape with the fill
 * instead: a clip is an offscreen layer that Core Animation composites on every scrolled frame,
 * and catalog rows carry several of them each.
 */
internal expect val clipShapedRowFills: Boolean

/** The platform's [clipShapedRowFills]; tests override it to measure either drawing path. */
internal val LocalClipShapedRowFills: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { clipShapedRowFills }

/** A row's shaped fill: `clip(shape).background(color)` when [clip], else a shaped background. */
internal fun Modifier.rowFill(
    color: Color,
    shape: Shape,
    clip: Boolean,
): Modifier = if (clip) clip(shape).background(color) else background(color, shape)

/** A row's shaped fill: `clip(shape).background(brush)` when [clip], else a shaped background. */
internal fun Modifier.rowFill(
    brush: Brush,
    shape: Shape,
    clip: Boolean,
): Modifier = if (clip) clip(shape).background(brush) else background(brush, shape)
