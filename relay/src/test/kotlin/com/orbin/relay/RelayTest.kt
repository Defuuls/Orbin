package com.orbin.relay

import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelayTest {
    private val channel = "A".repeat(42) + "Q"

    @Test
    fun healthAnswers() =
        withRelay {
            assertEquals("ok", client.get("/health").bodyAsText())
        }

    @Test
    fun listenerAndConnectorMeetAndOnlyBinaryFramesAreForwarded() =
        withRelay {
            val ws = createClient { install(WebSockets) }
            val listenerGot = CompletableDeferred<ByteArray>()
            coroutineScope {
                val listener =
                    async {
                        ws.webSocket("/v1/$channel/listen") {
                            assertEquals(Rendezvous.PAIRED, (incoming.receive() as Frame.Text).readText())
                            listenerGot.complete((incoming.receive() as Frame.Binary).readBytes())
                            send(Frame.Binary(true, byteArrayOf(9, 8, 7)))
                            // Stay until the connector hangs up.
                            incoming.receiveCatching()
                        }
                    }
                delay(200)
                ws.webSocket("/v1/$channel/connect") {
                    assertEquals(Rendezvous.PAIRED, (incoming.receive() as Frame.Text).readText())
                    send(Frame.Text("ignored"))
                    send(Frame.Binary(true, byteArrayOf(1, 2, 3)))
                    assertContentEquals(byteArrayOf(9, 8, 7), (incoming.receive() as Frame.Binary).readBytes())
                }
                assertContentEquals(byteArrayOf(1, 2, 3), withTimeout(5000) { listenerGot.await() })
                withTimeout(5000) { listener.await() }
            }
        }

    @Test
    fun connectorWithoutListenerAndBadChannelsAreRefused() =
        withRelay {
            val ws = createClient { install(WebSockets) }
            ws.webSocket("/v1/$channel/connect") {
                assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, closeReason.await()?.code)
            }
            ws.webSocket("/v1/not-a-channel/listen") {
                assertEquals("invalid channel", closeReason.await()?.message)
            }
        }

    @Test
    fun aListenerThatHungUpIsNeverMatched() =
        withRelay {
            val ws = createClient { install(WebSockets) }
            ws.webSocket("/v1/$channel/listen") { close() }
            delay(200)
            ws.webSocket("/v1/$channel/connect") {
                assertEquals("no listener", closeReason.await()?.message)
            }
        }

    @Test
    fun channelsLookLikeOrbinsDerivedIds() {
        assertTrue(validChannel(channel))
        assertTrue(validChannel("abcdefghijklmnopqrstuvwxyz-_0123456789ABCDE"))
        assertFalse(validChannel(channel.drop(1)))
        assertFalse(validChannel(channel.dropLast(1) + "/"))
        assertFalse(validChannel(channel.dropLast(1) + "é"))
    }

    private fun withRelay(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application { relay(RelayConfig(connectMillis = 500)) }
            block()
        }
}
