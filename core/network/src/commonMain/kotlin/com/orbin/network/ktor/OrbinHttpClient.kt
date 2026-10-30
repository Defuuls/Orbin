package com.orbin.network.ktor

import com.orbin.network.NetworkConfigProvider
import com.orbin.network.policy.PowBlock
import com.orbin.network.policy.RequestPolicy
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelinePhase
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlin.concurrent.Volatile

/** Thrown for a cleartext request while HTTPS-only is on; it never leaves the device. */
class CleartextBlockedException(
    host: String,
) : IOException("Blocked cleartext request to $host; HTTPS-only is enabled")

/**
 * The HTTP client the shared code sends through, on both platforms.
 *
 * @param engineAppliesPolicy true when the [engine] already enforces [RequestPolicy] and clears the
 *   POWBlock gate itself. Android's OkHttp engine does, through interceptors that also cover the
 *   image, video and download traffic that never touches Ktor, along with its own timeouts,
 *   cache, cookies and DNS over HTTPS; installing the plugins too would apply everything twice.
 *   On iOS (Darwin) it is false, and the plugins here enforce the same policy.
 */
fun orbinHttpClient(
    engine: HttpClientEngine,
    configProvider: NetworkConfigProvider,
    engineAppliesPolicy: Boolean = false,
): HttpClient {
    if (engineAppliesPolicy) return HttpClient(engine)
    val config = configProvider.current()
    return HttpClient(engine) {
        install(HttpTimeout) {
            connectTimeoutMillis = config.connectTimeoutSeconds * MILLIS_PER_SECOND
            requestTimeoutMillis = config.readTimeoutSeconds * MILLIS_PER_SECOND
            socketTimeoutMillis = config.readTimeoutSeconds * MILLIS_PER_SECOND
        }
        install(requestPolicyPlugin(configProvider))
    }.apply {
        installHtmlResponseLimit()
        installPowBlockGate(configProvider)
    }
}

/** Refuses cleartext under HTTPS-only and sets the policy's headers on every request. */
fun requestPolicyPlugin(configProvider: NetworkConfigProvider) =
    createClientPlugin("OrbinRequestPolicy") {
        onRequest { request, _ -> request.applyPolicy(configProvider) }
    }

/**
 * Clears the POWBlock proof-of-work gate and the terms page some LynxChan sites (8chan.moe) put in
 * front of every response, so the providers can read them like any other site. Mirrors Android's
 * `PowBlockInterceptor`: mine and submit the challenge, accept the terms if redirected to them,
 * then repeat the original request, at most [PowBlock.MAX_ROUNDS] times. The clearance cookies are
 * kept by the engine's cookie store (NSHTTPCookieStorage on iOS).
 */
fun HttpClient.installPowBlockGate(configProvider: NetworkConfigProvider) {
    val gates = PowGates()
    plugin(HttpSend).intercept { original ->
        if (original.attributes.contains(GATE_REQUEST)) return@intercept execute(original)
        val gate = gates.forHost(original.url.host)
        // Read before each request goes out, as on Android: a gate cleared after this point was
        // cleared without this request's challenge, which then is not worth mining.
        var clearancesSeen = gate.clearances
        var call = execute(original)
        repeat(PowBlock.MAX_ROUNDS) {
            val html = call.response.contentType()?.match(ContentType.Text.Html) == true
            val body = if (html) call.response.bodyAsText().take(PowBlock.MAX_INTERSTITIAL_BYTES.toInt()) else ""
            val challenge = if (PowBlock.isChallenge(body)) PowBlock.parse(body) else null
            val cleared =
                when {
                    // One request per host mines at a time; the rest wait, then ask again with the
                    // cookie it earned. A cold session's thumbnails would otherwise each mine one.
                    challenge != null ->
                        gate.mutex.withLock {
                            if (gate.clearances != clearancesSeen) return@withLock true
                            val nonce = PowBlock.mine(challenge) ?: return@intercept call
                            val submit =
                                URLBuilder(call.request.url).apply {
                                    parameters.clear()
                                    PowBlock.submitQuery(challenge, nonce).forEach { (key, value) ->
                                        parameters.append(key, value)
                                    }
                                }
                            runGate(gateRequest(submit.build(), referer = null, configProvider)).also { solved ->
                                if (solved) gate.clearances++
                            }
                        }
                    termsPage(call) != null -> {
                        val disclaimer = termsPage(call)!!
                        runGate(
                            gateRequest(
                                disclaimer.withPath(PowBlock.CONFIRM_PATH),
                                disclaimer.withPath(PowBlock.DISCLAIMER_PATH),
                                configProvider,
                            ),
                        )
                    }
                    else -> return@intercept call
                }
            if (!cleared) return@intercept call
            clearancesSeen = gate.clearances
            call = execute(HttpRequestBuilder().takeFrom(original))
        }
        call
    }
}

/** Each host's POWBlock gate: how often it has been cleared, and the lock its miners take turns on. */
private class PowGates {
    class Gate {
        val mutex = Mutex()

        @Volatile
        var clearances = 0
    }

    private val lock = Mutex()
    private val gates = mutableMapOf<String, Gate>()

    suspend fun forHost(host: String): Gate = lock.withLock { gates.getOrPut(host) { Gate() } }
}

/**
 * The terms page this response leads to, if any: either a redirect to it (Ktor's redirect handling
 * wraps this interceptor, so it sees the 302 itself) or the page itself, when the engine followed
 * the redirect natively.
 */
private fun termsPage(call: HttpClientCall): Url? {
    val url = call.request.url
    if (url.encodedPath.endsWith(PowBlock.DISCLAIMER_PATH)) return url
    val location = call.response.headers[HttpHeaders.Location] ?: return null
    val target = runCatching { URLBuilder(url).takeFrom(location).build() }.getOrElse { return null }
    return target.takeIf {
        call.response.status.value in REDIRECT_CODES &&
            it.encodedPath.endsWith(PowBlock.DISCLAIMER_PATH)
    }
}

private val REDIRECT_CODES = 300..399

private suspend fun HttpClient.runGate(request: HttpRequestBuilder): Boolean =
    try {
        prepareRequest {
            takeFrom(request)
            attributes.put(GATE_REQUEST, Unit)
        }.execute { /* Cookies are header-driven; never save or drain a clearance body. */ }
        true
    } catch (_: IOException) {
        false
    }

private fun gateRequest(
    url: Url,
    referer: Url?,
    configProvider: NetworkConfigProvider,
): HttpRequestBuilder =
    HttpRequestBuilder().apply {
        this.url.takeFrom(url)
        // Gate requests skip the plugin pipeline, so they get the policy's headers here, as the
        // OkHttp gate's sub-requests do from the interceptors after it.
        applyPolicy(configProvider)
        referer?.let { headers[HttpHeaders.Referrer] = it.toString() }
    }

private fun HttpRequestBuilder.applyPolicy(configProvider: NetworkConfigProvider) {
    val config = configProvider.current()
    if (!RequestPolicy.allows(config, url.protocol.name)) throw CleartextBlockedException(url.host)
    val edits = RequestPolicy.headers(config, method.value, url.encodedPath, originReferer(url.build()))
    edits.remove.forEach { headers.remove(it) }
    edits.set.forEach { (name, value) -> headers[name] = value }
}

private fun originReferer(url: Url): String =
    URLBuilder(url)
        .apply {
            encodedPath = "/"
            parameters.clear()
            fragment = ""
        }.buildString()

private fun Url.withPath(path: String): Url =
    URLBuilder(this)
        .apply {
            encodedPath = path
            parameters.clear()
        }.build()

private const val MILLIS_PER_SECOND = 1_000L

/** Preserve ordinary HTML for consumers, without ever saving more than the inspection budget. */
@OptIn(InternalAPI::class)
private suspend fun HttpClientCall.saveBounded(maxBytes: Int): HttpClientCall =
    BoundedReplayCall(this, response.rawContent.readBoundedBytes(maxBytes))

private class BoundedReplayCall(
    original: HttpClientCall,
    bytes: ByteArray,
) : HttpClientCall(original.client) {
    init {
        request =
            object : HttpRequest by original.request {
                override val call: HttpClientCall get() = this@BoundedReplayCall
            }
        response = ReplayResponse(this, original.response, bytes)
    }

    override val allowDoubleReceive: Boolean = true
}

private class ReplayResponse(
    override val call: HttpClientCall,
    original: HttpResponse,
    private val bytes: ByteArray,
) : HttpResponse() {
    override val status = original.status
    override val version = original.version
    override val requestTime = original.requestTime
    override val responseTime = original.responseTime
    override val headers = original.headers
    override val coroutineContext = original.coroutineContext

    @InternalAPI
    override val rawContent: ByteReadChannel get() = ByteReadChannel(bytes)
}

private val GATE_REQUEST = AttributeKey<Unit>("OrbinGateRequest")

/** Runs before Ktor's default SaveBody receive phase, including for non-streaming get calls. */
private fun HttpClient.installHtmlResponseLimit() {
    val phase = PipelinePhase("OrbinBoundedHtml")
    receivePipeline.insertPhaseBefore(HttpReceivePipeline.Before, phase)
    receivePipeline.intercept(phase) { response ->
        if (!response.call.attributes.contains(GATE_REQUEST) &&
            response.contentType()?.match(ContentType.Text.Html) == true
        ) {
            if ((response.contentLength() ?: 0L) > PowBlock.MAX_INTERSTITIAL_BYTES) {
                response.cancel()
                throw IOException("HTML response exceeds the inspection limit")
            }
            proceedWith(response.call.saveBounded(PowBlock.MAX_INTERSTITIAL_BYTES.toInt()).response)
        }
    }
}
