package com.orbin.network.interceptor

import com.orbin.network.NetworkConfigProvider
import com.orbin.network.policy.RequestPolicy
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Applies [RequestPolicy]'s headers (user agent, Accept, Referer, cache rules) to every request,
 * read fresh per call so settings changes apply without rebuilding the client. The rules are shared
 * with iOS; this is OkHttp's adapter for them. The short max-age on API GETs is what lets the
 * ~50MB disk cache in [com.orbin.network.di.NetworkModule] serve anything.
 */
class HeadersInterceptor(
    private val configProvider: NetworkConfigProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val edits =
            RequestPolicy.headers(
                config = configProvider.current(),
                method = original.method,
                encodedPath = original.url.encodedPath,
                originReferer = original.url.originReferer(),
            )
        val builder = original.newBuilder()
        edits.remove.forEach { builder.removeHeader(it) }
        edits.set.forEach { (name, value) -> builder.header(name, value) }
        return chain.proceed(builder.build())
    }

    private fun okhttp3.HttpUrl.originReferer(): String =
        newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()
}
