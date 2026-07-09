package com.orbin.network.interceptor

import com.orbin.network.NetworkConfigProvider
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Applies the user-configured User-Agent (and a sane Accept) to every request, reading the value
 * fresh per call so settings changes apply immediately without rebuilding the client.
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

        if (originalRequest.isStaticMediaRequest()) {
            requestBuilder
                .header("Accept", "image/avif,image/webp,image/*,video/*,audio/*,*/*;q=0.8")
                .removeHeader("Cache-Control")
                .removeHeader("Pragma")
        } else {
            requestBuilder
                .header("Accept", "application/json, image/*, */*")
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
        }

        val request =
            requestBuilder.build()
        return chain.proceed(request)
    }

    private fun okhttp3.Request.isStaticMediaRequest(): Boolean {
        if (method != "GET") return false
        val path = url.encodedPath.lowercase()
        return MEDIA_EXTENSIONS.any { path.endsWith(it) }
    }

    private companion object {
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
