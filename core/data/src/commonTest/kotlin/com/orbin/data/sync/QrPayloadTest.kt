package com.orbin.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** What the sync-setup QR code carries, and what a scanner will accept. */
class QrPayloadTest {
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
