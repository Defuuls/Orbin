package com.orbin.data.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NearbyProtocolTest {
    @Test
    fun authenticatedEncryptionRejectsWrongKeysTamperingAndReplayContexts() =
        runTest {
            val key = NearbyCrypto.token()
            val plaintext = "private bookmarks".encodeToByteArray()
            val context = "session-one|request"
            val encrypted = NearbyCrypto.encrypt(key, context, plaintext)
            assertContentEquals(plaintext, NearbyCrypto.decrypt(key, context, encrypted))
            assertFalse(encrypted.decodeToString().contains("private bookmarks"))
            assertFailsWith<Exception> { NearbyCrypto.decrypt(NearbyCrypto.token(), context, encrypted) }
            assertFailsWith<Exception> { NearbyCrypto.decrypt(key, "session-two|request", encrypted) }
            assertFailsWith<Exception> { NearbyCrypto.decrypt(key, "session-one|response", encrypted) }
            encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
            assertFailsWith<Exception> { NearbyCrypto.decrypt(key, context, encrypted) }
        }

    @Test
    fun codesRoundTripButNeverAuthorizeInternetOrHostnameEndpoints() =
        runTest {
            val invite = NearbyInvite(NearbyCrypto.token(), NearbyCrypto.token(), "192.168.1.4", 39157, 1000)
            assertEquals(invite, NearbyInvite.parse(invite.code()))
            listOf("8.8.8.8", "example.com", "192.168.0.999", "192.168.1.1.attacker.example", "::1").forEach { host ->
                assertFailsWith<IllegalArgumentException> { NearbyInvite.parse(invite.copy(host = host).code()) }
            }
            assertFailsWith<IllegalArgumentException> { NearbyInvite.parse(invite.copy(port = 80).code()) }
            assertFailsWith<IllegalArgumentException> { NearbyInvite.parse("orbin-nearby1:bad") }
        }

    @Test
    fun privateAddressPolicyCoversEachIpv4RangeWithoutDns() {
        listOf("10.0.1.2", "172.16.0.1", "172.31.255.1", "192.168.1.2", "169.254.1.2", "127.0.0.1").forEach {
            assertTrue(localAddress(it))
        }
        listOf("172.15.0.1", "172.32.0.1", "192.169.0.1", "0.0.0.0", "224.0.0.1", "localhost").forEach {
            assertFalse(localAddress(it))
        }
    }

    @Test
    fun rejectsUnsupportedDocumentsAndDuplicateThreadIdentities() {
        assertFailsWith<IllegalArgumentException> { validateNearbyDocument(SyncDocument(version = 99)) }
        val visit = SyncedVisit("site", "g", 1, "title", lastVisitedMillis = 1)
        assertFailsWith<IllegalArgumentException> {
            validateNearbyDocument(
                SyncDocument(visits = listOf(visit, visit)),
            )
        }
    }
}
