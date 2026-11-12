package com.orbin.ios

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadyMediaDurationTest {
    @Test
    fun preparingItemsNeverQuerySynchronousDurationMetadata() {
        val duration = ReadyMediaDuration()
        repeat(20) { assertEquals(0.0, duration.read(ready = false) { error("Preparing item queried metadata") }) }
    }

    @Test
    fun knownDurationIsNotQueriedAgainByRepeatedControlPolls() {
        val duration = ReadyMediaDuration()
        var queries = 0
        repeat(100) {
            assertEquals(
                12.5,
                duration.read(ready = true) {
                    queries++
                    12.5
                },
            )
        }
        assertEquals(1, queries)
    }

    @Test
    fun unknownReadyMetadataCanResolveLaterWithoutReusingAnotherFilesDuration() {
        val duration = ReadyMediaDuration()
        assertEquals(0.0, duration.read(ready = true) { Double.NaN })
        assertEquals(0.0, duration.read(ready = true) { 0.0 })
        assertEquals(4.5, duration.read(ready = true) { 4.5 })
        assertEquals(4.5, duration.read(ready = true) { error("Known duration was queried again") })
        assertEquals(9.0, ReadyMediaDuration().read(ready = true) { 9.0 })
    }
}
