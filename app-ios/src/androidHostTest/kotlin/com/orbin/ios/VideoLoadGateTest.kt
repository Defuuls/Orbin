package com.orbin.ios

import com.orbin.core.model.MediaType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VideoLoadGateTest {
    @Test
    fun aThirdCallerWaitsUntilOnePermitIsReleased() =
        runTest {
            val gate = VideoLoadGate(maxInFlight = 2, spacingMs = 0L, nowMs = { currentTime })
            val firstHold = CompletableDeferred<Unit>()
            val secondHold = CompletableDeferred<Unit>()
            val order = mutableListOf<String>()
            val first =
                async {
                    gate.withPermit {
                        order += "first-start"
                        firstHold.await()
                        "first"
                    }
                }
            val second =
                async {
                    gate.withPermit {
                        order += "second-start"
                        secondHold.await()
                        "second"
                    }
                }
            runCurrent()
            assertEquals(listOf("first-start", "second-start"), order)
            assertEquals(2, gate.inFlightCount())

            val third =
                async {
                    gate.withPermit {
                        order += "third-start"
                        "third"
                    }
                }
            runCurrent()
            assertFalse(third.isCompleted, "A third load must wait for a free slot")
            assertEquals(2, gate.inFlightCount())

            firstHold.complete(Unit)
            runCurrent()
            assertEquals("first", first.await())
            assertTrue(third.isCompleted, "Releasing one permit must start the waiting load")
            assertEquals(listOf("first-start", "second-start", "third-start"), order)
            assertEquals("third", third.await())
            secondHold.complete(Unit)
            assertEquals("second", second.await())
            assertEquals(0, gate.inFlightCount())
        }

    @Test
    fun startsAreSpacedAndACanceledWaiterNeverRuns() =
        runTest {
            val gate = VideoLoadGate(maxInFlight = 2, spacingMs = 150L, pollMs = 25L, nowMs = { currentTime })
            val hold = CompletableDeferred<Unit>()
            val started = mutableListOf<Long>()
            val first =
                async {
                    gate.withPermit {
                        started += currentTime
                        hold.await()
                        "ok"
                    }
                }
            runCurrent()
            assertEquals(listOf(0L), started)

            val canceled =
                launch {
                    gate.withPermit { started += currentTime }
                }
            val second =
                async {
                    gate.withPermit {
                        started += currentTime
                        "second"
                    }
                }
            advanceTimeBy(100L)
            runCurrent()
            assertFalse(second.isCompleted)
            canceled.cancel()
            runCurrent()
            assertTrue(canceled.isCancelled)
            assertEquals(listOf(0L), started, "Cancelling a waiter must not start its request")

            hold.complete(Unit)
            runCurrent()
            assertEquals("ok", first.await())
            if (!second.isCompleted) {
                advanceTimeBy(50L)
                runCurrent()
            }
            assertEquals("second", second.await())
            assertEquals(listOf(0L, 150L), started)
            assertEquals(0, gate.inFlightCount())
        }

    @Test
    fun settleDelayIsSkippedWhenTheCallerIsCancelled() =
        runTest {
            var ran = false
            val job =
                launch {
                    awaitVideoLoadSettle(300L)
                    ran = true
                }
            advanceTimeBy(299L)
            runCurrent()
            assertFalse(ran)
            job.cancel()
            runCurrent()
            assertFalse(ran, "A card that scrolls away during the settle delay must not start a load")
        }

    @Test
    fun upcomingPreloadStartsOnlyAfterTheViewportSettles() =
        runTest {
            val prepared = mutableListOf<MediaPreloadKey?>()
            val target = MediaPreloadKey("next", "https://cdn.example/next.mp4", MediaType.VIDEO, "mp4")
            val job =
                launch {
                    prepareUpcomingAfterSettle(target, settleMs = 300L) { prepared += it }
                }
            advanceTimeBy(299L)
            runCurrent()
            assertEquals(emptyList<MediaPreloadKey?>(), prepared)
            advanceTimeBy(1L)
            runCurrent()
            assertEquals(listOf<MediaPreloadKey?>(target), prepared)
            job.join()
        }

    @Test
    fun aNullUpcomingTargetReleasesImmediately() =
        runTest {
            val prepared = mutableListOf<MediaPreloadKey?>()
            prepareUpcomingAfterSettle(null, settleMs = 300L) { prepared += it }
            assertEquals(listOf<MediaPreloadKey?>(null), prepared)
            assertEquals(0L, currentTime, "Turning preload off must not wait to release")
        }

    @Test
    fun cancellingDuringSettleNeverStartsPreload() =
        runTest {
            val prepared = mutableListOf<MediaPreloadKey?>()
            val target = MediaPreloadKey("next", "https://cdn.example/next.webm", MediaType.VIDEO, "webm")
            val job =
                launch {
                    prepareUpcomingAfterSettle(target, settleMs = 300L) { prepared += it }
                }
            advanceTimeBy(100L)
            job.cancel()
            runCurrent()
            assertEquals(emptyList<MediaPreloadKey?>(), prepared)
            assertTrue(job.isCancelled)
        }
}
