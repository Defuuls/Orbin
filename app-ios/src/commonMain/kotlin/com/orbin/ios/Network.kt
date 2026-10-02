package com.orbin.ios

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

/** Identifies the app to the sites it reads, as the Android app does with its own platform name. */
const val ORBIN_USER_AGENT: String = "Orbin/1.0 (iOS; +https://github.com/defuuls/orbin)"

/**
 * The client every request goes through. iOS passes the Darwin engine, which uses the system's
 * URL loading stack — so App Transport Security refuses plain HTTP, as Android's HTTPS-only
 * interceptor does.
 */
fun orbinHttpClient(engine: HttpClientEngine): HttpClient =
    HttpClient(engine) {
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        defaultRequest { header(HttpHeaders.UserAgent, ORBIN_USER_AGENT) }
    }

private const val CONNECT_TIMEOUT_MS: Long = 15_000
private const val REQUEST_TIMEOUT_MS: Long = 30_000
