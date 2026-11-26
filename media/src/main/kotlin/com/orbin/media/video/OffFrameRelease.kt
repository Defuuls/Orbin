package com.orbin.media.video

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player

/**
 * Releases players that leave composition outside the frame that disposed them.
 *
 * `ExoPlayer.release()` blocks the app thread until the player's playback thread has torn down
 * its renderers and codec. Called from a tile's `onDispose`, that wait lands inside the scroll
 * frame: in the thread scroll benchmark's traces it was the main thread's largest wait on an
 * ExoPlayer thread inside frames, up to 140 ms at a time. [retire] instead asks the player to
 * stop at once, which does not block and lets the playback thread free the codec in the
 * background, and calls [release][Player.release] later from [schedule], by which time that work
 * is done and the release returns quickly.
 *
 * Main thread only, like the players themselves.
 */
internal class OffFrameRelease(
    private val schedule: (() -> Unit) -> Unit,
) {
    /** Stops [player] now and releases it once [schedule] runs, never synchronously. */
    fun retire(player: Player) {
        retire(stop = player::stop, release = player::release)
    }

    /** [retire] for the two calls it makes, so the order can be checked without a real player. */
    fun retire(
        stop: () -> Unit,
        release: () -> Unit,
    ) {
        runCatching(stop)
        schedule { runCatching(release) }
    }

    companion object {
        /** How long a stopped player waits before its release is queued for an idle main thread. */
        const val RELEASE_DELAY_MS = 500L

        /**
         * Releases on the main looper after [RELEASE_DELAY_MS], once its queue is idle, so the
         * release neither runs inside a frame nor waits on a codec still being freed.
         */
        val Main: OffFrameRelease by lazy {
            val main = Handler(Looper.getMainLooper())
            OffFrameRelease { release ->
                main.postDelayed(
                    {
                        Looper.myQueue().addIdleHandler {
                            release()
                            false
                        }
                    },
                    RELEASE_DELAY_MS,
                )
            }
        }
    }
}
