package com.orbin.network.di

import com.orbin.network.DohConfig
import com.orbin.network.NetworkConfigProvider
import com.orbin.network.interceptor.HeadersInterceptor
import com.orbin.network.interceptor.HttpsOnlyInterceptor
import com.orbin.network.interceptor.InMemoryCookieJar
import com.orbin.network.interceptor.PowBlockInterceptor
import com.orbin.network.interceptor.VideoRetryAfterInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifies the base OkHttpClient shared by providers and image loaders. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseOkHttp

/** Qualifies the OkHttpClient used exclusively for video/audio CDN requests. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VideoOkHttp

/**
 * Provides the shared networking primitives: the lenient [Json] parser, and an [OkHttpClient]
 * configured for secure defaults (HTTPS-only, modern TLS), optional DNS-over-HTTPS, the
 * user-agent interceptor and opt-in logging.
 *
 * A [NetworkConfigProvider] binding must be supplied by the app/data layer; :data provides one
 * backed by DataStore. A default fallback is provided here so the module is usable standalone
 * (and in tests) before settings exist.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun providesJson(): Json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
            explicitNulls = false
        }

    @Provides
    @Singleton
    @BaseOkHttp
    fun providesOkHttpClient(configProvider: NetworkConfigProvider): OkHttpClient {
        val config = configProvider.current()

        // Bootstrap client used only to resolve the DoH endpoint itself.
        val bootstrap =
            OkHttpClient
                .Builder()
                .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
                .build()

        // Always negotiate modern TLS. Cleartext is intentionally absent from the connection
        // specs so HTTPS-only remains a hard privacy boundary.
        return OkHttpClient
            .Builder()
            .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionSpecs(
                listOf(
                    okhttp3.ConnectionSpec.RESTRICTED_TLS,
                    okhttp3.ConnectionSpec.MODERN_TLS,
                ),
            ).cookieJar(InMemoryCookieJar())
            .addInterceptor(HttpsOnlyInterceptor(configProvider))
            // Before HeadersInterceptor so that gate-clearance sub-requests still carry the
            // configured User-Agent and Accept headers.
            .addInterceptor(PowBlockInterceptor())
            .addInterceptor(HeadersInterceptor(configProvider))
            .apply {
                dns(DynamicDns(configProvider, bootstrap))
                if (config.enableHttpLogging) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                    )
                }
            }.build()
    }

    @Provides
    @Singleton
    @VideoOkHttp
    fun providesVideoOkHttpClient(
        @BaseOkHttp base: OkHttpClient,
    ): OkHttpClient =
        base
            .newBuilder()
            // Deliberately no okhttp3.Cache here: ExoPlayer's OkHttpDataSource issues byte-range
            // (Range:) requests to seek/buffer within the file, and OkHttp's built-in disk cache
            // doesn't correctly serve or store partial (206) responses against it - a documented
            // source of misbehavior for large streamed files. Media3's range-aware
            // SimpleCache/CacheDataSource is layered on top of this client in :media.
            // Keep video requests CDN-friendly even when the URL does not have a media extension.
            .addInterceptor { chain ->
                val req =
                    chain
                        .request()
                        .newBuilder()
                        .removeHeader("Cache-Control")
                        .removeHeader("Pragma")
                        .header("Accept", "video/webm,video/mp4,video/*,*/*;q=0.9")
                        .header("Referer", "https://boards.4chan.org/")
                        .build()
                chain.proceed(req)
            }.addInterceptor(VideoRetryAfterInterceptor())
            .build()

    private class DynamicDns(
        private val configProvider: NetworkConfigProvider,
        private val bootstrap: OkHttpClient,
    ) : Dns {
        private val dohCache = ConcurrentHashMap<DohConfig.Enabled, Dns>()

        @Throws(UnknownHostException::class)
        override fun lookup(hostname: String): List<InetAddress> =
            when (val doh = configProvider.current().dnsOverHttps) {
                DohConfig.Disabled -> Dns.SYSTEM.lookup(hostname)
                is DohConfig.Enabled -> dohCache.getOrPut(doh) { doh.toDns() }.lookup(hostname)
            }

        private fun DohConfig.Enabled.toDns(): Dns =
            DnsOverHttps
                .Builder()
                .client(bootstrap)
                .url(resolverUrl.toHttpUrl())
                .apply {
                    if (bootstrapIps.isNotEmpty()) {
                        bootstrapDnsHosts(bootstrapIps.map { InetAddress.getByName(it) })
                    }
                }.build()
    }
}
