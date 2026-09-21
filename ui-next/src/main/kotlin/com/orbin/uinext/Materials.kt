package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape

/**
 * Flat matte surface fill — the M3 replacement for frosted glass.
 *
 * M3 uses tonal elevation: a colored fill derived from the primary palette rather than
 * transparency or blur. This modifier applies a fully opaque [next.raised] or [next.elevated]
 * fill depending on call site. No hairline border, no alpha, no RenderEffect blur.
 *
 * Use [nextSurface] for standard cards/containers and [nextElevatedSurface] for sheets,
 * dialogs, and navigation chrome that sits above the content layer.
 */
@Composable
fun Modifier.nextSurface(shape: Shape): Modifier =
    this
        .clip(shape)
        .background(next.raised)

/**
 * Elevated flat matte surface — one tonal step above [nextSurface].
 *
 * Used for bottom sheets, modal containers, and navigation bars. Still fully opaque;
 * the tonal lift comes from [next.elevated] (M3 surfaceContainer) rather than from blur.
 */
@Composable
fun Modifier.nextElevatedSurface(shape: Shape): Modifier =
    this
        .clip(shape)
        .background(next.elevated)

/**
 * Accent container surface — M3 primaryContainer fill.
 *
 * Used for selected/active state fills: selected nav indicator, active chip background,
 * highlighted feed rows. The tonal eggplant fill communicates selection without a border.
 */
@Composable
fun Modifier.nextAccentSurface(shape: Shape): Modifier =
    this
        .clip(shape)
        .background(next.accentContainer)

/**
 * Backwards-compatible chrome fill: maps to flat matte [nextElevatedSurface].
 */
@Suppress("UnusedParameter")
@Composable
fun Modifier.nextFrosted(
    shape: Shape,
    lightAlpha: Float = 1f,
    darkAlpha: Float = 1f,
): Modifier = nextElevatedSurface(shape)
