package com.orbin.network.policy

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.hash.sha2.SHA512

/**
 * POWBlock is the Cloudflare-style proof-of-work gate that some LynxChan sites (notably
 * 8chan.moe) put in front of every response. The gate ships an HTML interstitial that mines a
 * SHA-256 (or SHA-512) hash of `token + nonce` until the digest has at least `difficulty` leading
 * zero bits, then re-requests the same URL with `?powblock=<nonce>&pbchal=<token>` to obtain the
 * `POW_TOKEN` / `POW_ID` clearance cookies.
 *
 * This object holds the gate's logic (challenge parsing, mining, and the paths of the terms page it
 * redirects to next), shared by Android and iOS. Android's `PowBlockInterceptor` wires it into
 * OkHttp; [com.orbin.network.ktor.installPowBlockGate] wires it into Ktor.
 */
object PowBlock {
    /** The terms-of-service page the gate redirects to once the proof of work is accepted. */
    const val DISCLAIMER_PATH = "/.static/pages/disclaimer.html"

    /** Fetched (with the disclaimer as referer) to accept the terms. */
    const val CONFIRM_PATH = "/.static/pages/confirmed.html"

    /** How many gate layers one request may clear before giving up and returning what it has. */
    const val MAX_ROUNDS = 4

    /** How much of an HTML response is read to look for a challenge. */
    const val MAX_INTERSTITIAL_BYTES = 64L * 1024L

    private const val MINING_TIMEOUT_MS = 30_000L

    /** Mining is CPU-bound: two at a time, off whatever thread is waiting for the response. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val miningDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2)

    /** A parsed POWBlock challenge extracted from an interstitial HTML body. */
    data class Challenge(
        val token: String,
        val difficulty: Int,
        val algorithm: String,
    )

    private val TOKEN_REGEX = Regex("<pre id=c[^>]*>([^<]+)</pre>")
    private val DIFFICULTY_REGEX = Regex("<pre id=d[^>]*>([^<]+)</pre>")
    private val ALGORITHM_REGEX = Regex("<pre id=h[^>]*>([^<]+)</pre>")

    /** True when [body] looks like a POWBlock interstitial rather than real content. */
    fun isChallenge(body: String): Boolean = body.contains("POWBlock") && TOKEN_REGEX.containsMatchIn(body)

    /** Parses a challenge out of an interstitial body, or null if the markers are absent/invalid. */
    fun parse(body: String): Challenge? {
        val token =
            TOKEN_REGEX
                .find(body)
                ?.groupValues
                ?.get(1)
                ?.trim() ?: return null
        if (token.length < MIN_TOKEN_LENGTH) return null
        val difficulty =
            DIFFICULTY_REGEX
                .find(body)
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.toIntOrNull() ?: DEFAULT_DIFFICULTY
        val algorithm =
            when (
                ALGORITHM_REGEX
                    .find(body)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    ?.toIntOrNull()
            ) {
                SHA512_BITS -> "SHA-512"
                else -> "SHA-256"
            }
        return Challenge(token = token, difficulty = difficulty, algorithm = algorithm)
    }

    /**
     * Mines the smallest non-negative nonce whose `token + nonce` digest has at least
     * [Challenge.difficulty] leading zero bits, matching the reference solver's bit-counting. The
     * search is bounded by [maxIterations] so a malformed/absurd difficulty can't hang forever.
     */
    fun solve(
        challenge: Challenge,
        maxIterations: Long = DEFAULT_MAX_ITERATIONS,
        isCancelled: () -> Boolean = { false },
    ): Long? {
        val digest = if (challenge.algorithm == "SHA-512") SHA512() else SHA256()
        val prefix = challenge.token
        var nonce = 0L
        while (nonce < maxIterations) {
            // Checked every few thousand hashes so a stuck mine cannot outlive its timeout.
            if (nonce % CANCEL_CHECK_INTERVAL == 0L && isCancelled()) return null
            digest.reset()
            val hash = digest.digest((prefix + nonce).encodeToByteArray())
            if (leadingZeroBits(hash) >= challenge.difficulty) return nonce
            nonce++
        }
        return null
    }

    /**
     * Mines [challenge] off the caller's thread, giving up after [timeoutMs]. Returns null on
     * timeout, cancellation or an unsolvable challenge, so a gate fails closed rather than
     * stalling every request waiting behind it.
     */
    suspend fun mine(
        challenge: Challenge,
        timeoutMs: Long = MINING_TIMEOUT_MS,
    ): Long? =
        withTimeoutOrNull(timeoutMs) {
            withContext(miningDispatcher) {
                val context = currentCoroutineContext()
                solve(challenge) { !context.isActive }
            }
        }

    /** The query that submits a solved challenge on the interstitial's own URL. */
    fun submitQuery(
        challenge: Challenge,
        nonce: Long,
    ): List<Pair<String, String>> = listOf("powblock" to nonce.toString(), "pbchal" to challenge.token)

    /** Counts leading zero bits until the first set bit, as the reference JS solver does. */
    private fun leadingZeroBits(hash: ByteArray): Int {
        var bits = 0
        for (byte in hash) {
            val value = byte.toInt() and BYTE_MASK
            if (value == 0) {
                bits += BITS_PER_BYTE
                continue
            }
            var mask = HIGH_BIT
            while (mask != 0 && (value and mask) == 0) {
                bits++
                mask = mask shr 1
            }
            break
        }
        return bits
    }

    private const val MIN_TOKEN_LENGTH = 60
    private const val DEFAULT_DIFFICULTY = 20
    private const val SHA512_BITS = 512
    private const val BYTE_MASK = 0xFF
    private const val BITS_PER_BYTE = 8
    private const val HIGH_BIT = 0x80

    // 2^24 nonces is comfortably above the ~2^18 expected work for 8chan's difficulty 18 while
    // still bounding a pathological challenge.
    private const val DEFAULT_MAX_ITERATIONS = 16_777_216L
    private const val CANCEL_CHECK_INTERVAL = 4_096L
}
