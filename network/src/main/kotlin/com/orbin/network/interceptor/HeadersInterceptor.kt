package com.orbin.network.interceptor

import com.orbin.network.NetworkConfigProvider
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Applies the user-configured User-Agent (and a sane Accept) to every request, reading the value
 * fresh per call so settings changes apply immediately without rebuilding the client.
 *
 * Idempotent non-media GETs (catalog, thread JSON, board lists) may use a short-lived HTTP cache
 * so the ~50MB OkHttp disk cache in [com.orbin.network.di.NetworkModule] is actually usable.
 * Mutations keep no-store so PoW / cookie gates and POSTs are never served stale.
 */
class HeadersInterceptor(
    private val configProvider: NetworkConfigProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder =
            originalRequest
                .newBuilder()
                .header("User-Agent", configProvider.current().userAgent)

        when {
            originalRequest.isStaticMediaRequest() -> {
                requestBuilder
                    .header("Accept", "image/avif,image/webp,image/*,video/*,audio/*,*/*;q=0.8")
                    .header("Referer", originalRequest.url.originReferer())
                    .removeHeader("Cache-Control")
                    .removeHeader("Pragma")
            }
            originalRequest.isCacheableApiGet() -> {
                requestBuilder
                    .header("Accept", "application/json, image/*, */*")
                    .removeHeader("Pragma")
                    // Prefer a brief fresh window; OkHttp still revalidates when stale.
                    .header("Cache-Control", "max-age=$CACHEABLE_API_MAX_AGE_SECONDS")
            }
            else -> {
                requestBuilder
                    .header("Accept", "application/json, image/*, */*")
                    .header("Cache-Control", "no-store")
                    .header("Pragma", "no-cache")
            }
        }

        return chain.proceed(requestBuilder.build())
    }

    private fun okhttp3.Request.isStaticMediaRequest(): Boolean {
        if (method != "GET") return false
        val path = url.encodedPath.lowercase()
        return MEDIA_EXTENSIONS.any { path.endsWith(it) }
    }

    /** Idempotent GETs that are not static media — catalogs, threads, board metadata. */
    private fun okhttp3.Request.isCacheableApiGet(): Boolean = method == "GET" && !isStaticMediaRequest()

    private fun okhttp3.HttpUrl.originReferer(): String =
        newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()

    private companion object {
        const val CACHEABLE_API_MAX_AGE_SECONDS = 60

        val MEDIA_EXTENSIONS =
            setOf(
                ".jpg",
                ".jpeg",
                ".png",
                ".gif",
                ".webp",
                ".avif",
                ".bmp",
                ".webm",
                ".mp4",
                ".m4v",
                ".mov",
                ".mp3",
                ".ogg",
                ".opus",
                ".wav",
            )
    }
}
