package com.orbin.network.ktor

import com.orbin.network.NetworkConfigProvider
import com.orbin.network.policy.PowBlock
import com.orbin.network.policy.RequestPolicy
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.save
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.Sender
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom
import kotlinx.io.IOException

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
    }.apply { installPowBlockGate(configProvider) }
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
    plugin(HttpSend).intercept { original ->
        var call = execute(original)
        repeat(PowBlock.MAX_ROUNDS) {
            val html = call.response.contentType()?.match(ContentType.Text.Html) == true
            if (html) call = call.save()
            val body = if (html) call.response.bodyAsText().take(PowBlock.MAX_INTERSTITIAL_BYTES.toInt()) else ""
            val challenge = if (PowBlock.isChallenge(body)) PowBlock.parse(body) else null
            val cleared =
                when {
                    challenge != null -> {
                        val nonce = PowBlock.mine(challenge) ?: return@intercept call
                        val submit =
                            URLBuilder(call.request.url).apply {
                                parameters.clear()
                                PowBlock.submitQuery(challenge, nonce).forEach { (key, value) ->
                                    parameters.append(key, value)
                                }
                            }
                        runGate(gateRequest(submit.build(), referer = null, configProvider))
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
            call = execute(HttpRequestBuilder().takeFrom(original))
        }
        call
    }
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
    val target = URLBuilder(url).takeFrom(location).build()
    return target.takeIf {
        call.response.status.value in REDIRECT_CODES &&
            it.encodedPath.endsWith(PowBlock.DISCLAIMER_PATH)
    }
}

private val REDIRECT_CODES = 300..399

private suspend fun Sender.runGate(request: HttpRequestBuilder): Boolean =
    try {
        execute(request).save()
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
