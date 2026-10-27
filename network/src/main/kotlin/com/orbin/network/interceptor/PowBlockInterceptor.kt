package com.orbin.network.interceptor

import com.orbin.network.policy.PowBlock
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Transparently clears the POWBlock proof-of-work gate and the terms-of-service redirect that some
 * LynxChan sites (8chan.moe) place in front of every response, so provider and image-loading code
 * can treat those sites like any other.
 *
 * The gate has two layers, applied in order on a cold session:
 *  1. A POWBlock interstitial (HTML with an embedded challenge) is returned for the requested URL.
 *     We mine the nonce ([PowBlock] via [PowBlockSolver]) and re-request the URL with
 *     `?powblock=&pbchal=`, which sets the `POW_TOKEN` / `POW_ID` clearance cookies.
 *  2. Requests then 302-redirect to a `/.static/pages/disclaimer.html` terms page. Fetching
 *     `/.static/pages/confirmed.html` with the disclaimer as referer sets the site's ToS cookie.
 *
 * Cleared cookies live in the client's [InMemoryCookieJar], so once a session is unlocked the
 * layers are skipped. On sites without POWBlock none of the detection matches and this is a no-op.
 *
 * Requests to one host that hit the gate together mine it once, not once each (see [clearPowGate]).
 *
 * Mining never runs on OkHttp's shared dispatcher threads: [PowBlock.mine] runs it on a small
 * dedicated pool while this call waits, with a hard timeout. Timeouts fail closed so a
 * pathological challenge cannot stall API and Coil traffic forever.
 */
class PowBlockInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val gate = gates.computeIfAbsent(original.url.host) { Gate() }
        // Read before each request goes out: a gate cleared after this point was cleared without
        // this request's challenge, which then is not worth mining.
        var clearancesSeen = gate.clearances
        var response = chain.proceed(original)

        var rounds = 0
        while (rounds < MAX_ROUNDS) {
            when {
                response.isPowChallenge() -> {
                    val cleared = clearPowGate(chain, original, response, gate, clearancesSeen) ?: break
                    clearancesSeen = cleared.first
                    response = cleared.second
                }
                response.isTosRedirect() -> {
                    val accepted = acceptTos(chain, response.request.url)
                    response.close()
                    if (!accepted) break
                    clearancesSeen = gate.clearances
                    response = chain.proceed(original)
                }
                else -> return response
            }
            rounds++
        }
        return response
    }

    /**
     * Clears [challenged]'s gate and returns the original request's next response, with the
     * clearance count read just before it was sent; or null when the challenge cannot be solved.
     *
     * One request per host mines at a time. A cold session sends many requests at once (the API
     * and every thumbnail), each answered with its own challenge; mining them all would burn every
     * core for one cookie. The rest wait here, and a request whose challenge predates a clearance
     * another request made simply asks again with the cookie that request earned.
     */
    private fun clearPowGate(
        chain: Interceptor.Chain,
        original: Request,
        challenged: Response,
        gate: Gate,
        clearancesSeen: Int,
    ): Pair<Int, Response>? {
        synchronized(gate) {
            if (gate.clearances == clearancesSeen) {
                val solved = solvePow(chain, challenged)
                if (!solved) return null
                challenged.close()
                gate.clearances++
            } else {
                challenged.close()
            }
        }
        val seen = gate.clearances
        return seen to chain.proceed(original)
    }

    /** How many times a host's gate has been cleared, and the lock its miners take turns on. */
    private class Gate {
        @Volatile
        var clearances = 0
    }

    private val gates = ConcurrentHashMap<String, Gate>()

    /** Mines and submits the challenge on the interstitial's URL; returns false if unsolvable. */
    private fun solvePow(
        chain: Interceptor.Chain,
        response: Response,
    ): Boolean {
        val body = response.peekBody(MAX_INTERSTITIAL_BYTES).string()
        val challenge = PowBlock.parse(body) ?: return false
        val nonce = runBlocking { PowBlock.mine(challenge) } ?: return false
        val submitUrl =
            response.request.url
                .newBuilder()
                .query(null)
                .apply {
                    PowBlock.submitQuery(challenge, nonce).forEach { (key, value) ->
                        addQueryParameter(key, value)
                    }
                }.build()
        return runGate(
            chain,
            Request
                .Builder()
                .url(submitUrl)
                .get()
                .build(),
        )
    }

    /** Accepts the terms of service so subsequent requests stop redirecting to the disclaimer. */
    private fun acceptTos(
        chain: Interceptor.Chain,
        disclaimerUrl: HttpUrl,
    ): Boolean {
        val base =
            disclaimerUrl
                .newBuilder()
                .encodedPath(CONFIRM_PATH)
                .query(null)
                .build()
        val referer =
            disclaimerUrl
                .newBuilder()
                .encodedPath(DISCLAIMER_PATH)
                .query(null)
                .build()
        val request =
            Request
                .Builder()
                .url(base)
                .header("Referer", referer.toString())
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
        const val MAX_ROUNDS = PowBlock.MAX_ROUNDS
        const val MAX_INTERSTITIAL_BYTES = PowBlock.MAX_INTERSTITIAL_BYTES
        const val DISCLAIMER_PATH = PowBlock.DISCLAIMER_PATH
        const val CONFIRM_PATH = PowBlock.CONFIRM_PATH
    }
}
