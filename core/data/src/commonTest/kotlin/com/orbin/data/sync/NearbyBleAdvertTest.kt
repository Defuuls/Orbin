package com.orbin.data.sync

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NearbyBleAdvertTest {
    private val beacon = byteArrayOf(1, 2, 3, -4, 5, -128)

    @Test
    fun roundTripsThroughUuidText() {
        val uuid = NearbyBleAdvert.encode(beacon, 0x00c1)
        val text = NearbyBleAdvert.text(uuid)
        assertEquals("4f52424e-0100-0102-03fc-0580" + "00c10000", text)
        val parsed = NearbyBleAdvert.parse(text.uppercase())!!
        val (tag, psm) = NearbyBleAdvert.decode(parsed)!!
        assertContentEquals(beacon, tag)
        assertEquals(0x00c1, psm)
        assertContentEquals(NearbyBleAdvert.match(beacon), uuid.copyOf(NearbyBleAdvert.MATCH_BYTES))
    }

    @Test
    fun ignoresOtherUuids() {
        assertNull(NearbyBleAdvert.decode(NearbyBleAdvert.parse("0000180f-0000-1000-8000-00805f9b34fb")!!))
        assertNull(NearbyBleAdvert.parse("not-a-uuid"))
    }
}
