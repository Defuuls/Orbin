package com.orbin.data.sync

import com.orbin.relay.relay
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Two complete nearby-sync controllers with no shared network or Bluetooth, meeting only through a
 * real relay server over real WebSockets: QR-code pairing, then a later "Sync now".
 */
class NearbyRelayEndToEndTest {
    private val port = ServerSocket(0).use { it.localPort }
    private lateinit var server: EmbeddedServer<*, *>
    private val relayUrl get() = "http://127.0.0.1:$port"

    @BeforeTest
    fun startRelay() {
        server = embeddedServer(ServerCIO, port = port, host = "127.0.0.1") { relay() }.start(wait = false)
    }

    @AfterTest
    fun stopRelay() = server.stop(0, 0)

    @Test
    fun pairsByQrCodeAndSyncsAgainOnlyThroughTheRelay() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val client = HttpClient(CIO)
            val firstProgress = Progress(doc(1))
            val secondProgress = Progress(doc(2))
            val first = controller(firstProgress, scope, client)
            val second = controller(secondProgress, scope, client)
            try {
                listOf(first, second).forEach {
                    it.setActive(true)
                    it.setEnabled(true)
                }
                first.setRelayUrl("$relayUrl/")
                assertEquals(relayUrl, first.state.value.relayUrl)
                // Give the inviter's listening socket a moment to reach the relay.
                delay(300)
                first.createPairingCode()
                val code = assertNotNull(first.state.value.pairingCode)
                val invite = NearbyInvite.parse(code)
                assertNull(invite.host)
                assertEquals(relayUrl, invite.relay)

                second.join(code)
                withTimeout(10_000) { first.state.first { it.devices.firstOrNull()?.lastSyncedMillis != null } }
                assertEquals(
                    setOf(1L, 2L),
                    firstProgress
                        .snapshot()
                        .visits
                        .map { it.thread }
                        .toSet(),
                )
                assertEquals(firstProgress.snapshot().visits.toSet(), secondProgress.snapshot().visits.toSet())
                // The joiner had no relay of its own and adopted the inviter's.
                assertEquals(relayUrl, second.state.value.relayUrl)

                // Later: the joiner changes something and syncs; the inviter only listens on the relay.
                secondProgress.replace(doc(2))
                delay(300)
                second.syncNow()
                withTimeout(10_000) { while (firstProgress.snapshot().visits.size != 1) delay(20) }
                assertEquals(listOf(2L), firstProgress.snapshot().visits.map { it.thread })

                // And the other direction, through the joiner's own listening socket.
                firstProgress.replace(doc(5))
                delay(300)
                first.syncNow()
                withTimeout(10_000) {
                    while (secondProgress.snapshot().visits.map { it.thread } !=
                        listOf(5L)
                    ) {
                        delay(20)
                    }
                }
            } finally {
                first.setActive(false)
                second.setActive(false)
                scope.cancel()
                client.close()
            }
        }

    @Test
    fun relayAddressesMustBeHttpsOrLocal() {
        assertEquals("https://relay.example.org/orbin", normalizeRelayUrl(" https://relay.example.org/orbin/ "))
        assertEquals("http://192.168.1.10:8080", normalizeRelayUrl("http://192.168.1.10:8080"))
        assertNull(normalizeRelayUrl("http://relay.example.org"))
        assertNull(normalizeRelayUrl("https://relay.example.org/?x=1"))
        assertNull(normalizeRelayUrl("https://user:pw@relay.example.org"))
        assertNull(normalizeRelayUrl("ftp://relay.example.org"))
    }

    private fun controller(
        progress: Progress,
        scope: CoroutineScope,
        client: HttpClient,
    ) = NearbySync(
        progress,
        MemoryPreferences(),
        NearbySyncSecrets(MemorySecret()),
        scope,
        { emptyList() },
        0,
        networks = { emptyList() },
        relay = NearbyRelay(client),
    )

    private fun doc(thread: Long) =
        SyncDocument(visits = listOf(SyncedVisit("site", "g", thread, "Title", lastVisitedMillis = 1)))
}
