package com.orbin.ios

import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.lynxchan.LynxChanProvider
import com.orbin.provider.lynxchan.LynxChanSite
import com.orbin.provider.lynxchan.api.KtorLynxChanApi
import com.orbin.provider.vichan.VichanProvider
import com.orbin.provider.vichan.VichanSite
import com.orbin.provider.vichan.api.KtorVichanApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

/** The lenient parser the providers expect: the same settings as Android's `NetworkModule`. */
val OrbinJson: Json =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

/** Identifies the app to the sites it reads, as the Android app does with its own platform name. */
const val ORBIN_USER_AGENT: String = "Orbin/1.0 (iOS; +https://github.com/defuuls/orbin)"

/**
 * The client every request goes through. iOS passes the Darwin engine, which uses the system's
 * URL loading stack — so App Transport Security refuses plain HTTP, as Android's HTTPS-only
 * interceptor does.
 */
fun orbinHttpClient(engine: HttpClientEngine): HttpClient =
    HttpClient(engine) {
        defaultRequest { header(HttpHeaders.UserAgent, ORBIN_USER_AGENT) }
    }

/** The sites the app reads. The Android app registers the same two in `ProvidersModule`. */
fun orbinProviders(
    client: HttpClient,
    json: Json = OrbinJson,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): List<ImageBoardProvider> {
    val vichan = VichanSite.Example
    val lynxChan = LynxChanSite.BbwChan
    return listOf(
        VichanProvider(vichan, KtorVichanApi(client, vichan.apiBaseUrl, json), dispatcher),
        LynxChanProvider(lynxChan, KtorLynxChanApi(client, lynxChan.apiBaseUrl, json), dispatcher),
    )
}
