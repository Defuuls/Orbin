package com.orbin.ios

import com.orbin.network.NetworkConfig
import com.orbin.network.NetworkConfigProvider
import com.orbin.network.ktor.orbinHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine

/** Identifies the app to the sites it reads, as the Android app does with its own platform name. */
const val ORBIN_USER_AGENT: String = "Orbin/1.0 (iOS; +https://github.com/defuuls/orbin)"

/** iOS's network settings: the shared defaults (HTTPS only, the same timeouts) under its own user agent. */
val IosNetworkConfig: NetworkConfigProvider = NetworkConfigProvider { IOS_NETWORK_CONFIG }

private val IOS_NETWORK_CONFIG = NetworkConfig(userAgent = ORBIN_USER_AGENT)

/**
 * The client every request goes through: the shared factory, with the shared request policy and
 * POWBlock gate installed as Ktor plugins. iOS passes the Darwin engine, which uses the system's URL
 * loading stack and cookie store, so App Transport Security also refuses plain HTTP.
 */
fun iosHttpClient(engine: HttpClientEngine): HttpClient = orbinHttpClient(engine, IosNetworkConfig)
