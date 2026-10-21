package com.orbin.data.sync

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.io.encoding.Base64

private const val MAX_RELAY_URL_LENGTH = 512

/**
 * Normalizes a relay address the user typed: `https://` (or `http://` to a loopback or private
 * address, for a relay on your own network), no query or fragment, no trailing slash. Returns null
 * for anything else, so a typo never sends traffic somewhere unexpected.
 */
fun normalizeRelayUrl(input: String): String? {
    val text = input.trim().trimEnd('/')
    val url =
        text
            .takeIf {
                it.isNotEmpty() && it.length <= MAX_RELAY_URL_LENGTH
            }?.let { runCatching { Url(it) }.getOrNull() }
    val secure = url?.protocol == URLProtocol.HTTPS && text.startsWith("https://", ignoreCase = true)
    val local =
        url?.protocol == URLProtocol.HTTP &&
            text.startsWith("http://", ignoreCase = true) &&
            (url.host == "localhost" || localAddress(url.host))
    val plain =
        url != null &&
            url.host.isNotEmpty() &&
            url.parameters.names().isEmpty() &&
            url.fragment.isEmpty() &&
            url.user == null &&
            !text.contains('@')
    return text.takeIf { (secure || local) && plain }
}

/**
 * The Internet transport: both devices open a WebSocket to a relay the reader runs (see
 * docs/relay.md) and name the same random channel. The relay only forwards frames; each one is
 * still encrypted and authenticated with the pair key or the QR code's one-time secret, exactly as
 * over Wi-Fi, so the relay never sees activity, device names or keys.
 *
 * Channels are derived like the Bluetooth beacons, but full length: an invitation's from its
 * selector, a paired device's from its private id and a ten-minute window.
 */
class NearbyRelay(
    client: HttpClient,
) {
    private val client =
        client.config {
            install(WebSockets) {
                pingIntervalMillis = PING_MS
                maxFrameSize = MAX_NEARBY_BYTES.toLong() + FRAME_OVERHEAD
            }
        }

    /** Keep a listening socket open on each of the latest [channels] and accept every match. */
    suspend fun serve(
        relay: String,
        channels: Flow<List<String>>,
        accept: suspend (NearbyChannel) -> Unit,
    ) {
        channels.collectLatest { list ->
            coroutineScope { list.forEach { channel -> launch { listen(relay, channel, accept) } } }
        }
    }

    private suspend fun listen(
        relay: String,
        channel: String,
        accept: suspend (NearbyChannel) -> Unit,
    ) = coroutineScope {
        var backoff = MIN_BACKOFF_MS
        while (isActive) {
            try {
                val session = client.webSocketSession(endpoint(relay, channel, "listen"))
                if (awaitPaired(session, timeoutMillis = null)) {
                    backoff = MIN_BACKOFF_MS
                    launch { accept(RelayChannel(session)) }
                    continue
                }
                session.cancel()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Relay unreachable or restarting: back off and try again while sync is on.
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    /** Meet whichever of [channels] has a listener, trying them in order. */
    suspend fun connect(
        relay: String,
        channels: List<String>,
    ): NearbyChannel {
        var failure: Exception? = null
        for (channel in channels) {
            try {
                val session = client.webSocketSession(endpoint(relay, channel, "connect"))
                if (awaitPaired(session, CONNECT_TIMEOUT_MS)) return RelayChannel(session)
                session.cancel()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught") other: Exception,
            ) {
                failure = other
            }
        }
        failure?.let { throw it }
        error("The other device isn't connected to the relay")
    }

    /** The relay's `paired` text frame; false if it closes the socket first (no listener, expired). */
    private suspend fun awaitPaired(
        session: DefaultClientWebSocketSession,
        timeoutMillis: Long?,
    ): Boolean {
        suspend fun first(): Boolean {
            val frame = session.incoming.receiveCatching().getOrNull() ?: return false
            return frame is Frame.Text && frame.readText() == PAIRED
        }
        return if (timeoutMillis == null) first() else withTimeout(timeoutMillis) { first() }
    }

    private fun endpoint(
        relay: String,
        channel: String,
        role: String,
    ): String {
        val base = checkNotNull(normalizeRelayUrl(relay)) { "Invalid relay address" }
        val socket =
            when {
                base.startsWith("https://", ignoreCase = true) -> "wss://" + base.drop("https://".length)
                else -> "ws://" + base.drop("http://".length)
            }
        return "$socket/v1/$channel/$role"
    }

    private class RelayChannel(
        private val session: DefaultClientWebSocketSession,
    ) : NearbyChannel {
        override suspend fun read(limit: Int): ByteArray {
            while (true) {
                val frame = session.incoming.receive()
                if (frame !is Frame.Binary) continue
                val bytes = frame.readBytes()
                require(bytes.size in 1..limit) { "Invalid nearby frame size" }
                return bytes
            }
        }

        override suspend fun write(bytes: ByteArray) {
            require(bytes.size in 1..MAX_NEARBY_BYTES) { "Too much activity for one nearby sync" }
            session.send(Frame.Binary(true, bytes))
        }

        override fun close() = session.cancel()
    }

    companion object {
        private const val PAIRED = "paired"
        private const val PING_MS = 20_000L
        private const val FRAME_OVERHEAD = 1024L
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val MIN_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 60_000L
    }
}

/** Relay channel ids: SHA-256 in unpadded base64url, 43 characters, which the relay checks. */
internal object NearbyRelayChannels {
    suspend fun invite(selector: String): String = channel("orbin-relay1|invite|$selector")

    suspend fun device(
        device: String,
        nowMillis: Long,
    ): List<String> {
        val window = nowMillis / NearbyBeacons.WINDOW_MS
        return listOf(
            channel("orbin-relay1|device|$device|$window"),
            channel("orbin-relay1|device|$device|${window - 1}"),
        )
    }

    private suspend fun channel(input: String): String =
        Base64.UrlSafe
            .withPadding(Base64.PaddingOption.ABSENT)
            .encode(
                CryptographyProvider.Default
                    .get(SHA256)
                    .hasher()
                    .hash(input.encodeToByteArray()),
            )
}
