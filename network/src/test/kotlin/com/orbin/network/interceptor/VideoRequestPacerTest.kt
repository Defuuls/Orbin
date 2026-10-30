package com.orbin.network.interceptor

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class VideoRequestPacerTest {
    private var now = 0L
    private val sleeps = mutableListOf<Long>()
    private val pacer =
        VideoRequestPacer(
            maxInFlight = 2,
            spacingMs = 100,
            now = { now },
            sleep = {
                sleeps += it
                now += it
            },
        )

    @Test
    fun `back to back requests for one url start a spacing apart`() {
        val starts = mutableListOf<Long>()
        repeat(3) { pacer.paced(URL, { false }) { starts += now } }

        assertThat(starts).containsExactly(0L, 100L, 200L).inOrder()
    }

    @Test
    fun `different urls do not wait for each other`() {
        val starts = mutableListOf<Long>()
        pacer.paced("a", { false }) { starts += now }
        pacer.paced("b", { false }) { starts += now }

        assertThat(starts).containsExactly(0L, 0L)
        assertThat(sleeps).isEmpty()
    }

    @Test
    fun `a request waits for a slot when the url is at its in flight cap`() {
        var canceledAfterWaiting = false
        pacer.paced(URL, { false }) {
            now += 100
            pacer.paced(URL, { false }) {
                now += 100
                // Third request: both slots are taken, so it can only give up when canceled.
                var polls = 0
                val failure =
                    assertThrows(IOException::class.java) {
                        pacer.paced(URL, { ++polls > 3 }) { error("must not run") }
                    }
                canceledAfterWaiting = failure.message == "Canceled"
            }
        }

        assertThat(canceledAfterWaiting).isTrue()
    }

    @Test
    fun `a failed request still frees its slot`() {
        repeat(5) {
            assertThrows(IOException::class.java) { pacer.paced(URL, { false }) { throw IOException("boom") } }
        }

        // Five failures through a cap of two only work if every one released its slot.
        assertThat(pacer.paced(URL, { false }) { "ok" }).isEqualTo("ok")
    }

    @Test
    fun `a canceled call stops waiting for its turn`() {
        pacer.paced(URL, { false }) { }

        val failure = assertThrows(IOException::class.java) { pacer.paced(URL, { true }) { error("must not run") } }

        assertThat(failure).hasMessageThat().isEqualTo("Canceled")
    }

    @Test
    fun `a burst of parallel range requests never exceeds the cap`() {
        val real = VideoRequestPacer(maxInFlight = 2, spacingMs = 5)
        val running = AtomicInteger()
        val peak = AtomicInteger()
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val jobs =
                (1..8).map {
                    pool.submit {
                        start.await()
                        real.paced(URL, { false }) {
                            peak.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                            Thread.sleep(30)
                            running.decrementAndGet()
                        }
                    }
                }
            start.countDown()
            jobs.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertThat(peak.get()).isAtMost(2)
    }

    private companion object {
        const val URL = "https://i.4cdn.org/g/1.webm"
    }
}
