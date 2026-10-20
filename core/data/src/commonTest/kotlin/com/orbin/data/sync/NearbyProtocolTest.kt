package com.orbin.data.sync

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun transferIntentIsRequiredSoOldAutomaticClientsCannotAppearManual() {
        val legacy = """{"device":"old-peer","name":"Old device","port":39157,"document":{"version":1}}"""
        assertFailsWith<SerializationException> { NearbyJson.decodeFromString<NearbyRequest>(legacy) }
        listOf(false, true).forEach { automatic ->
            val request = NearbyRequest("peer", "Device", 39157, SyncDocument(), automatic)
            assertEquals(request, NearbyJson.decodeFromString<NearbyRequest>(NearbyJson.encodeToString(request)))
        }
    }

    @Test
    fun localNetworkCheckUsesActualPrefixAndRejectsMalformedMasks() {
        assertTrue(NearbyNetwork("192.168.1.20", 24).contains("192.168.1.42"))
        assertFalse(NearbyNetwork("192.168.1.20", 24).contains("192.168.2.42"))
        assertTrue(NearbyNetwork("10.8.1.20", 16).contains("10.8.200.42"))
        assertFalse(NearbyNetwork("10.8.1.20", 16).contains("10.9.1.20"))
        assertFalse(NearbyNetwork("192.168.1.20", 28).contains("192.168.1.42"))
        assertFalse(NearbyNetwork("192.168.1.20", 0).contains("192.168.1.42"))
        assertFalse(NearbyNetwork("192.168.1.20", 24).contains("example.com"))
        assertEquals(24, nearbyPrefix("255.255.255.0"))
        assertEquals(16, nearbyPrefix("255.255.0.0"))
        assertNull(nearbyPrefix("255.0.255.0"))
        assertNull(nearbyPrefix("0.0.0.0"))
        assertNull(nearbyPrefix("broken"))
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
