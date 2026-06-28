package com.orbin.network.interceptor

import com.orbin.network.NetworkConfigProvider
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Enforces the user's "HTTPS only" preference on every request, read fresh per call so toggling
 * the setting takes effect immediately (the shared client is a singleton and isn't rebuilt). When
 * the preference is on, any cleartext request is rejected before it leaves the device.
 */
class HttpsOnlyInterceptor(
    private val configProvider: NetworkConfigProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (configProvider.current().httpsOnly && !request.isHttps) {
            throw IOException("Blocked cleartext request to ${request.url.host}; HTTPS-only is enabled")
        }
        return chain.proceed(request)
    }
}
