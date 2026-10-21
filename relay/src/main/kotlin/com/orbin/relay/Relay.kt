package com.orbin.relay

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

/** Limits that keep one relay cheap to run and useless as general-purpose storage or a proxy. */
@Suppress("MagicNumber") // Each default is a documented limit (docs/relay.md), written out in its unit.
data class RelayConfig(
    /** Largest single frame: Orbin's 8 MiB nearby limit plus AES-GCM and framing overhead. */
    val maxFrameBytes: Long = 8L * 1024 * 1024 + 1024,
    /** Bytes both directions together in one session. */
    val maxSessionBytes: Long = 40L * 1024 * 1024,
    /** How long an unmatched listener may wait before it must reconnect. */
    val listenMillis: Long = 10 * 60 * 1000L,
    /** How long a connector waits for a listener on its channel. */
    val connectMillis: Long = 5_000L,
    /** A whole matched session, start to finish. */
    val sessionMillis: Long = 2 * 60 * 1000L,
    val maxListeners: Int = 10_000,
    val maxListenersPerAddress: Int = 32,
    val maxSessions: Int = 1_000,
)

/**
 * The relay: a rendezvous for two WebSockets that name the same random channel id, and a byte
 * pipe between them once they meet. It keeps no accounts, logs no channel ids and stores nothing;
 * every frame is forwarded as received and forgotten. Orbin encrypts and authenticates each frame
 * with a key the relay never sees (the pair key, or the one-time secret from the pairing QR code),
 * so the relay can delay or drop traffic but cannot read or alter it undetected.
 *
 * - `GET  /health` → `ok`
 * - `WS   /v1/{channel}/listen`  wait (up to [RelayConfig.listenMillis]) for one connector
 * - `WS   /v1/{channel}/connect` meet the waiting listener (or close after [RelayConfig.connectMillis])
 *
 * Both sides get a text frame `paired` when they meet; binary frames are forwarded after that.
 */
fun Application.relay(config: RelayConfig = RelayConfig()) {
    val rendezvous = Rendezvous(config)
    install(WebSockets) {
        pingPeriod = 20.seconds
        timeout = 30.seconds
        maxFrameSize = config.maxFrameBytes
        masking = false
    }
    routing {
        get("/health") { call.respondText("ok") }
        webSocket("/v1/{channel}/listen") {
            rendezvous.listen(this, call.parameters["channel"].orEmpty(), call.request.origin.remoteAddress)
        }
        webSocket("/v1/{channel}/connect") {
            rendezvous.connect(this, call.parameters["channel"].orEmpty())
        }
    }
}

/** Channel ids are 32 random-looking bytes in unpadded base64url, exactly what Orbin derives. */
internal fun validChannel(channel: String): Boolean =
    channel.length == CHANNEL_LENGTH &&
        channel.all { it.isLetterOrDigit() && it.code < ASCII || it == '-' || it == '_' }

private const val CHANNEL_LENGTH = 43
private const val ASCII = 128

internal class Rendezvous(
    private val config: RelayConfig,
) {
    private class Listener(
        val session: DefaultWebSocketServerSession,
        val address: String,
    ) {
        val matched = CompletableDeferred<Pair<DefaultWebSocketServerSession, CompletableDeferred<Unit>>>()
    }

    private val listeners = ConcurrentHashMap<String, Listener>()
    private val perAddress = ConcurrentHashMap<String, AtomicInteger>()
    private val sessions = AtomicInteger()

    val waiting: Int get() = listeners.size

    suspend fun listen(
        session: DefaultWebSocketServerSession,
        channel: String,
        address: String,
    ) {
        if (!validChannel(channel)) return session.reject("invalid channel")
        val count = perAddress.computeIfAbsent(address) { AtomicInteger() }
        if (listeners.size >= config.maxListeners || count.incrementAndGet() > config.maxListenersPerAddress) {
            release(address, count)
            return session.reject("relay busy")
        }
        val listener = Listener(session, address)
        try {
            // The newest listener on a channel wins: a device that reconnects replaces its old socket.
            listeners.put(channel, listener)?.let { old ->
                old.matched.cancel()
                old.session.reject("replaced")
            }
            // A listener that hangs up before it is matched must never be handed to a connector.
            val hangUp = session.launch { session.closeReason.await().also { listener.matched.cancel() } }
            val (peer, done) =
                try {
                    withTimeoutOrNull(config.listenMillis) { listener.matched.await() }
                } catch (_: CancellationException) {
                    null
                } finally {
                    hangUp.cancel()
                } ?: return session.close(CloseReason(CloseReason.Codes.NORMAL, "expired"))
            try {
                pipe(session, peer)
            } finally {
                done.complete(Unit)
            }
        } finally {
            listeners.remove(channel, listener)
            release(address, count)
        }
    }

    suspend fun connect(
        session: DefaultWebSocketServerSession,
        channel: String,
    ) {
        if (!validChannel(channel)) return session.reject("invalid channel")
        val deadline = System.currentTimeMillis() + config.connectMillis
        while (true) {
            val listener = listeners[channel]
            if (listener != null && listeners.remove(channel, listener) && meet(session, listener)) return
            if (System.currentTimeMillis() > deadline) return session.reject("no listener")
            delay(POLL_MILLIS)
        }
    }

    /** False if the listener hung up in the meantime, so the connector keeps looking. */
    private suspend fun meet(
        session: DefaultWebSocketServerSession,
        listener: Listener,
    ): Boolean {
        if (sessions.incrementAndGet() > config.maxSessions) {
            sessions.decrementAndGet()
            listener.session.reject("relay busy")
            session.reject("relay busy")
            return true
        }
        try {
            val done = CompletableDeferred<Unit>()
            if (!listener.matched.complete(session to done)) return false
            // Keep this handler (and so this socket) open while the listener's side pipes.
            done.await()
            return true
        } finally {
            sessions.decrementAndGet()
        }
    }

    private suspend fun pipe(
        first: DefaultWebSocketServerSession,
        second: DefaultWebSocketServerSession,
    ) {
        val bytes = AtomicLong()
        try {
            withTimeout(config.sessionMillis) {
                first.send(Frame.Text(PAIRED))
                second.send(Frame.Text(PAIRED))
                coroutineScope {
                    launch { forward(first, second, bytes) }
                    launch { forward(second, first, bytes) }
                }
            }
        } catch (_: TimeoutCancellationException) {
            // Session over time: both sockets are closed below.
        } finally {
            runCatching { first.close(CloseReason(CloseReason.Codes.NORMAL, "done")) }
            runCatching { second.close(CloseReason(CloseReason.Codes.NORMAL, "done")) }
        }
    }

    /** Binary frames only, counted against the session budget; the peer closing ends both. */
    private suspend fun forward(
        from: DefaultWebSocketServerSession,
        to: DefaultWebSocketServerSession,
        bytes: AtomicLong,
    ) {
        try {
            for (frame in from.incoming) {
                if (frame !is Frame.Binary) continue
                if (bytes.addAndGet(frame.data.size.toLong()) > config.maxSessionBytes) break
                to.send(Frame.Binary(true, frame.data))
            }
        } finally {
            runCatching { to.close(CloseReason(CloseReason.Codes.NORMAL, "peer closed")) }
        }
    }

    private fun release(
        address: String,
        count: AtomicInteger,
    ) {
        if (count.decrementAndGet() <= 0) perAddress.remove(address, count)
    }

    private suspend fun DefaultWebSocketServerSession.reject(reason: String) {
        runCatching { close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, reason)) }
    }

    companion object {
        const val PAIRED = "paired"
        private const val POLL_MILLIS = 100L
    }
}
