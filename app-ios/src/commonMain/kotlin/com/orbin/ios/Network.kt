package com.orbin.ios

import com.orbin.network.NetworkConfig
import com.orbin.network.NetworkConfigProvider
import com.orbin.network.ktor.orbinHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine

/**
 * The same request policy the Android app uses, over a Darwin (URLSession) engine.
 *
 * HTTPS only, a customizable DNS-over-HTTPS resolver, this app's User-Agent, and the same
 * cache and header rules — including clearing the POWBlock session-origin cookie, which otherwise
 * hitchhikes on the shared cookie store. [configProvider] defaults to a static Cloudflare, not
 * strict, snapshot so unit tests can keep calling [iosHttpClient] with only an engine. The running
 * app passes a live provider so a change in Settings is picked up on the next request.
 */
fun iosHttpClient(
    engine: HttpClientEngine,
    configProvider: NetworkConfigProvider = IosNetworkConfig,
): HttpClient = orbinHttpClient(engine, configProvider)

const val ORBIN_USER_AGENT = "Orbin/1.0 (iOS; +https://github.com/defuuls/orbin)"

/**
 * A frozen default for tests and for the first moment of a process, before settings are loaded:
 * this app's User-Agent, Cloudflare DoH, and system-DNS fallback allowed (the same default
 * Android ships).
 */
val IosNetworkConfig: NetworkConfigProvider = NetworkConfigProvider { IOS_NETWORK_CONFIG }

private val IOS_NETWORK_CONFIG = NetworkConfig(userAgent = ORBIN_USER_AGENT)
