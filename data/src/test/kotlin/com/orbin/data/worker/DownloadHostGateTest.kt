package com.orbin.data.worker

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class DownloadHostGateTest {
    @Test
    fun aFourthCallerToTheSameHostWaitsForAPermit() =
        runTest {
            val gate = DownloadHostGate()
            val running = AtomicInteger(0)
            val peak = AtomicInteger(0)
            val started = AtomicInteger(0)
            val release = Mutex(locked = true)

            val waiters =
                List(DownloadAttempt.MAX_CONCURRENT_PER_HOST) {
                    launch {
                        gate.withPermit("cdn.example") {
                            started.incrementAndGet()
                            val n = running.incrementAndGet()
                            peak.updateAndGet { maxOf(it, n) }
                            release.lock()
                            release.unlock()
                            running.decrementAndGet()
                        }
                    }
                }
            // Give the first three time to enter before launching the fourth.
            var fourthEntered = false
            val fourth =
                async {
                    gate.withPermit("cdn.example") {
                        fourthEntered = true
                        val n = running.incrementAndGet()
                        peak.updateAndGet { maxOf(it, n) }
                        running.decrementAndGet()
                    }
                }

            // The first three hold their permits; the fourth must not have entered yet.
            delay(50)
            assertThat(started.get()).isEqualTo(DownloadAttempt.MAX_CONCURRENT_PER_HOST)
            assertThat(fourthEntered).isFalse()
            assertThat(peak.get()).isEqualTo(DownloadAttempt.MAX_CONCURRENT_PER_HOST)

            release.unlock()
            waiters.forEach { it.join() }
            fourth.await()
            assertThat(fourthEntered).isTrue()
            assertThat(peak.get()).isEqualTo(DownloadAttempt.MAX_CONCURRENT_PER_HOST)
        }

    @Test
    fun differentHostsDoNotShareACap() =
        runTest {
            val gate = DownloadHostGate()
            val running = AtomicInteger(0)
            val peak = AtomicInteger(0)
            val perHost = DownloadAttempt.MAX_CONCURRENT_PER_HOST
            val jobs =
                listOf("a.example", "b.example").flatMap { host ->
                    List(perHost) {
                        async {
                            gate.withPermit(host) {
                                val n = running.incrementAndGet()
                                peak.updateAndGet { maxOf(it, n) }
                                delay(50)
                                running.decrementAndGet()
                            }
                        }
                    }
                }
            jobs.awaitAll()
            assertThat(peak.get()).isEqualTo(perHost * 2)
        }
}
