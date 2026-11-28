package com.orbin.core.model.stream

import kotlin.math.abs

/**
 * Whether a pointer gesture is a deliberate swipe. Strict on purpose: vertical scrolling and
 * taps must never be mistaken for navigation. Distances are in the host's pixels; [dtMillis]
 * is the gesture's duration.
 */
object SwipeDecision {
    const val MIN_DX = 72.0
    const val RATIO = 2.5
    const val MAX_MILLIS = 600L
    const val DISMISS_MIN_DY = 96.0

    /** `+1` for a swipe to the next view (finger moved left), `-1` for the previous, `0` for none. */
    fun horizontal(
        dx: Double,
        dy: Double,
        dtMillis: Long,
    ): Int {
        if (!dx.isFinite() || !dy.isFinite()) return 0
        val ax = abs(dx)
        val ay = abs(dy)
        if (dtMillis > MAX_MILLIS || ax < MIN_DX || ax < RATIO * ay) return 0
        return if (dx < 0) 1 else -1
    }

    /** A quick, mostly vertical, downward drag: dismisses the media viewer. */
    fun dismiss(
        dx: Double,
        dy: Double,
        dtMillis: Long,
    ): Boolean {
        if (!dx.isFinite() || !dy.isFinite()) return false
        return dtMillis <= MAX_MILLIS && dy >= DISMISS_MIN_DY && dy >= RATIO * abs(dx)
    }

    /** [index] limited to a list of [length]; `0` for an empty list. */
    fun clampIndex(
        index: Int,
        length: Int,
    ): Int = index.coerceIn(0, maxOf(length - 1, 0))
}
