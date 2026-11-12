package com.orbin.ios

/**
 * One player item's duration. A preparing item must not trigger a synchronous metadata query
 * on the UI thread; once finite duration is known, control polling reuses it instead of querying
 * AVFoundation every time. Unknown/live values remain eligible for a later ready-item update.
 */
internal class ReadyMediaDuration {
    private var known = 0.0

    fun read(
        ready: Boolean,
        query: () -> Double,
    ): Double {
        if (!ready) return 0.0
        if (known <= 0.0) known = query().takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        return known
    }
}
