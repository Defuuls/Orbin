package com.orbin.media.video

import android.os.Looper
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OffFrameReleaseTest {
    private val calls = mutableListOf<String>()
    private val scheduled = mutableListOf<() -> Unit>()
    private val deferred = OffFrameRelease { scheduled += it }

    @Test
    fun `a retired player is stopped at once and released only when the scheduled step runs`() {
        deferred.retire(stop = { calls += "stop" }, release = { calls += "release" })

        assertThat(calls).containsExactly("stop")
        assertThat(scheduled).hasSize(1)

        scheduled.single().invoke()
        assertThat(calls).containsExactly("stop", "release").inOrder()
    }

    @Test
    fun `retiring several tiles in one frame releases none of them inside it`() {
        repeat(3) { tile ->
            deferred.retire(stop = { calls += "stop$tile" }, release = { calls += "release$tile" })
        }

        assertThat(calls).containsExactly("stop0", "stop1", "stop2").inOrder()
        scheduled.forEach { it() }
        assertThat(calls.filter { it.startsWith("release") }).containsExactly("release0", "release1", "release2")
    }

    @Test
    fun `a stop that throws still leaves the release scheduled`() {
        deferred.retire(stop = { error("already released") }, release = { calls += "release" })

        scheduled.single().invoke()
        assertThat(calls).containsExactly("release")
    }

    @Test
    fun `the main-thread release waits out its delay and then an idle main looper`() {
        var released = false
        OffFrameRelease.Main.retire(stop = {}, release = { released = true })
        val main = shadowOf(Looper.getMainLooper())

        main.idleFor(Duration.ofMillis(OffFrameRelease.RELEASE_DELAY_MS - 1))
        assertThat(released).isFalse()

        main.idleFor(Duration.ofMillis(1))
        main.idle()
        assertThat(released).isTrue()
    }
}
