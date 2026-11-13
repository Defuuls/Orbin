package com.orbin.ios

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

/**
 * Caps and spaces iOS video CDN work (first-frame decodes, next-clip preload). Android's
 * video request pacer does the same for ExoPlayer: at most [DEFAULT_MAX_IN_FLIGHT] loads in
 * flight and [DEFAULT_SPACING_MS] between starts. Waiting is cancellable, so a card that has
 * scrolled off screen never starts its request.
 *
 * The visible player is not gated; it must start as soon as its page settles.
 */
internal class VideoLoadGate(
    private val maxInFlight: Int = DEFAULT_MAX_IN_FLIGHT,
    private val spacingMs: Long = DEFAULT_SPACING_MS,
    private val pollMs: Long = DEFAULT_POLL_MS,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    init {
        require(maxInFlight > 0) { "maxInFlight must be positive" }
        require(spacingMs >= 0L) { "spacingMs cannot be negative" }
        require(pollMs > 0L) { "pollMs must be positive" }
    }

    private val mutex = Mutex()
    private var inFlight = 0
    private var nextStartMs = 0L
    private val released = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** How many loads currently hold a permit; for tests. */
    suspend fun inFlightCount(): Int = mutex.withLock { inFlight }

    /**
     * Runs [block] once a slot is free and the spacing interval has passed. Cancelling the caller
     * while it is waiting does not consume a permit and does not run [block].
     */
    suspend fun <T> withPermit(block: suspend () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            mutex.withLock { inFlight-- }
            released.tryEmit(Unit)
        }
    }

    private suspend fun acquire() {
        while (true) {
            val waitMs =
                mutex.withLock {
                    val now = nowMs()
                    when {
                        inFlight >= maxInFlight -> pollMs
                        now < nextStartMs -> minOf(nextStartMs - now, pollMs)
                        else -> {
                            inFlight++
                            nextStartMs = now + spacingMs
                            return
                        }
                    }
                }
            // A released permit wakes waiters at once; the timeout covers the spacing interval.
            withTimeoutOrNull(waitMs) { released.first() }
        }
    }

    internal companion object {
        const val DEFAULT_MAX_IN_FLIGHT = 2
        const val DEFAULT_SPACING_MS = 150L
        const val DEFAULT_POLL_MS = 25L

        /**
         * How long a thumbnail or upcoming preload waits after appearing, so a fast fling can
         * cancel it before any CDN request starts.
         */
        const val SETTLE_MS = 300L
    }
}

/** Shared session gate for iOS video thumbnail decodes and next-clip preload. */
internal val iosVideoLoadGate = VideoLoadGate()

/**
 * Waits until a visible item has settled. Cancelling this (the card scrolled away, the pager
 * moved on) is the cancellation that skips the request.
 */
internal suspend fun awaitVideoLoadSettle(settleMs: Long = VideoLoadGate.SETTLE_MS) {
    delay(settleMs)
}

/**
 * Starts preload for [target] only after the viewport has settled. A null target, including
 * preload being turned off, releases work immediately so a disabled setting is not delayed.
 */
internal suspend fun prepareUpcomingAfterSettle(
    target: MediaPreloadKey?,
    settleMs: Long = VideoLoadGate.SETTLE_MS,
    prepare: (MediaPreloadKey?) -> Unit,
) {
    if (target == null) {
        prepare(null)
        return
    }
    awaitVideoLoadSettle(settleMs)
    prepare(target)
}
