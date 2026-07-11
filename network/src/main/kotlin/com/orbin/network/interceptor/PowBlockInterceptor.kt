package com.orbin.network.interceptor

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Transparently clears the POWBlock proof-of-work gate and the terms-of-service redirect that some
 * LynxChan sites (8chan.moe) place in front of every response, so provider and image-loading code
 * can treat those sites like any other.
 *
 * The gate has two layers, applied in order on a cold session:
 *  1. A POWBlock interstitial (HTML with an embedded challenge) is returned for the requested URL.
 *     We mine the nonce ([PowBlock]) and re-request the URL with `?powblock=&pbchal=`, which sets
 *     the `POW_TOKEN` / `POW_ID` clearance cookies.
 *  2. Requests then 302-redirect to a `/.static/pages/disclaimer.html` terms page. Fetching
 *     `/.static/pages/confirmed.html` with the disclaimer as referer sets the site's ToS cookie.
 *
 * Cleared cookies live in the client's [InMemoryCookieJar], so once a session is unlocked the
 * layers are skipped. On sites without POWBlock none of the detection matches and this is a no-op.
 */
class PowBlockInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        var response = chain.proceed(original)

        var rounds = 0
        while (rounds < MAX_ROUNDS) {
            when {
                response.isPowChallenge() -> {
                    val solved = solvePow(chain, response)
                    response.close()
                    if (!solved) break
                }
                response.isTosRedirect() -> {
                    val accepted = acceptTos(chain, response.request.url)
                    response.close()
                    if (!accepted) break
                }
                else -> return response
            }
            rounds++
            response = chain.proceed(original)
        }
        return response
    }

    /** Mines and submits the challenge on the interstitial's URL; returns false if unsolvable. */
    private fun solvePow(
        chain: Interceptor.Chain,
        response: Response,
    ): Boolean {
        val body = response.peekBody(MAX_INTERSTITIAL_BYTES).string()
        val challenge = PowBlock.parse(body) ?: return false
        val nonce = PowBlock.solve(challenge) ?: return false
        val submitUrl =
            response.request.url
                .newBuilder()
                .query(null)
                .addQueryParameter("powblock", nonce.toString())
                .addQueryParameter("pbchal", challenge.token)
                .build()
        return runGate(chain, Request.Builder().url(submitUrl).get().build())
    }

    /** Accepts the terms of service so subsequent requests stop redirecting to the disclaimer. */
    private fun acceptTos(
        chain: Interceptor.Chain,
        disclaimerUrl: HttpUrl,
    ): Boolean {
        val base = disclaimerUrl.newBuilder().encodedPath(CONFIRM_PATH).query(null).build()
        val request =
            Request
                .Builder()
                .url(base)
                .header("Referer", disclaimerUrl.newBuilder().encodedPath(DISCLAIMER_PATH).query(null).build().toString())
                .get()
                .build()
        return runGate(chain, request)
    }

    /** Issues a clearance sub-request; the client cookie jar captures any Set-Cookie across hops. */
    private fun runGate(
        chain: Interceptor.Chain,
        request: Request,
    ): Boolean =
        try {
            chain.proceed(request).close()
            true
        } catch (_: IOException) {
            false
        }

    private fun Response.isPowChallenge(): Boolean {
        val contentType = header("Content-Type").orEmpty()
        if (!contentType.contains("text/html", ignoreCase = true)) return false
        return PowBlock.isChallenge(peekBody(MAX_INTERSTITIAL_BYTES).string())
    }

    private fun Response.isTosRedirect(): Boolean = request.url.encodedPath.endsWith(DISCLAIMER_PATH)

    private companion object {
        const val MAX_ROUNDS = 4
        const val MAX_INTERSTITIAL_BYTES = 64L * 1024L
        const val DISCLAIMER_PATH = "/.static/pages/disclaimer.html"
        const val CONFIRM_PATH = "/.static/pages/confirmed.html"
    }
}
