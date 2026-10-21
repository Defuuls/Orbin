package com.orbin.data.sync

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the pairing and sync-setup QR codes carry, and what a scanner will accept. */
class QrPayloadTest {
    private val token = runBlocking { NearbyCrypto.token() }

    @Test
    fun invitesCarryWifiBluetoothOrBothButNeverNeither() {
        val both = NearbyInvite(token, token, "192.168.1.4", 39157, 1, bluetooth = true)
        assertEquals(both, NearbyInvite.parse(both.code()))
        val air = NearbyInvite(token, token, null, 39157, 1, bluetooth = true)
        assertEquals(air, NearbyInvite.parse(air.code()))
        // Wi-Fi-only codes are byte-for-byte the previous format, so older builds still read them.
        val wifi = NearbyInvite(token, token, "10.0.0.2", 39157, 1)
        assertFalse(wifi.code().let { NearbyInvite.parse(it).bluetooth })
        assertFailsWith<IllegalArgumentException> {
            NearbyInvite.parse(NearbyInvite(token, token, null, 39157, 1).code())
        }
    }

    @Test
    fun pairingCodesStayWellInsideOneQrCode() {
        val invite = NearbyInvite(token, token, "192.168.100.200", 65535, Long.MAX_VALUE, bluetooth = true)
        // QR version 40 at error correction M holds 2331 bytes; keep pairing codes far below that.
        assertTrue(invite.code().length < 400, "pairing code is ${invite.code().length} characters")
    }

    @Test
    fun beaconsAreShortStablePerWindowAndRotate() =
        runBlocking {
            val now = 5 * NearbyBeacons.WINDOW_MS + 1
            val current = NearbyBeacons.device(token, now)
            assertEquals(2, current.size)
            assertTrue(current.all { it.size == NEARBY_BEACON_BYTES })
            assertContentEquals(current[0], NearbyBeacons.device(token, now + 1000)[0])
            // The next window's "previous" tag is this window's current one, so clocks may drift.
            assertContentEquals(current[0], NearbyBeacons.device(token, now + NearbyBeacons.WINDOW_MS)[1])
            assertFalse(current[0].contentEquals(NearbyBeacons.device(token, now + NearbyBeacons.WINDOW_MS)[0]))
            assertFalse(NearbyBeacons.invite(token).contentEquals(current[0]))
        }

    @Test
    fun syncSetupCodesRoundTripAndRequireHttps() {
        val account = SyncAccount("https://dav.example/orbin/", "reader", "p@ss word")
        val code = SyncSetupCode.encode(account)
        assertTrue(code.startsWith("orbin-webdav1:"))
        assertEquals(account, SyncSetupCode.parse(code))
        assertFailsWith<IllegalArgumentException> {
            SyncSetupCode.encode(
                account.copy(folderUrl = "http://dav.example"),
            )
        }
        assertFailsWith<IllegalArgumentException> { SyncSetupCode.parse("orbin-nearby1:abc") }
    }
}
