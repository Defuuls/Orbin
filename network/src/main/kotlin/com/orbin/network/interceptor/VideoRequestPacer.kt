package com.orbin.network.interceptor

import java.io.IOException

/**
 * Spreads out the requests one video makes. ExoPlayer probes an MP4 with several different byte
 * ranges as soon as it prepares, and each is a separate CDN request; fired together they are what
 * trips the rate limit on a single clip.
 *
 * Per URL, at most [maxInFlight] requests wait for response headers at once and each one starts at
 * least [spacingMs] after the previous. The permit covers only the wait for headers, never the body,
 * so a paused player holding a response open cannot stall another request.
 */
class VideoRequestPacer(
    private val maxInFlight: Int = MAX_IN_FLIGHT,
    private val spacingMs: Long = SPACING_MS,
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    private class Slot {
        var inFlight = 0
        var nextStartMs = 0L
    }

    private val slots = mutableMapOf<String, Slot>()

    /** Runs [block] once a slot for [url] is free, or throws if [isCanceled] turns true while waiting. */
    fun <T> paced(
        url: String,
        isCanceled: () -> Boolean,
        block: () -> T,
    ): T {
        enter(url, isCanceled)
        try {
            return block()
        } finally {
            synchronized(slots) { slots[url]?.let { it.inFlight-- } }
        }
    }

    private fun enter(
        url: String,
        isCanceled: () -> Boolean,
    ) {
        while (true) {
            val waitMs =
                synchronized(slots) {
                    val t = now()
                    slots.entries.removeIf { it.value.inFlight == 0 && it.value.nextStartMs <= t }
                    val slot = slots.getOrPut(url) { Slot() }
                    when {
                        slot.inFlight >= maxInFlight -> POLL_MS
                        t < slot.nextStartMs -> minOf(slot.nextStartMs - t, POLL_MS)
                        else -> {
                            slot.inFlight++
                            slot.nextStartMs = t + spacingMs
                            return
                        }
                    }
                }
            if (isCanceled()) throw IOException("Canceled")
            sleep(waitMs)
        }
    }

    private companion object {
        const val MAX_IN_FLIGHT = 2
        const val SPACING_MS = 150L
        const val POLL_MS = 25L
    }
}
