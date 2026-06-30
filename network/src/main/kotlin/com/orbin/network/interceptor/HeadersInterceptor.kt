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
        val request =
            chain
                .request()
                .newBuilder()
                .header("User-Agent", configProvider.current().userAgent)
                .header("Accept", "application/json, image/*, */*")
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
                .build()
        return chain.proceed(request)
    }
}
