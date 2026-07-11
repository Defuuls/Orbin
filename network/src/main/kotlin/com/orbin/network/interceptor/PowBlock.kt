package com.orbin.network.interceptor

import java.security.MessageDigest

/**
 * POWBlock is the Cloudflare-style proof-of-work gate that some LynxChan sites (notably
 * 8chan.moe) put in front of every response. The gate ships an HTML interstitial that mines a
 * SHA-256 (or SHA-512) hash of `token + nonce` until the digest has at least `difficulty` leading
 * zero bits, then re-requests the same URL with `?powblock=<nonce>&pbchal=<token>` to obtain the
 * `POW_TOKEN` / `POW_ID` clearance cookies.
 *
 * This object holds the pure, dispatcher-free logic (challenge parsing + mining) so it can be unit
 * tested without any network; [PowBlockInterceptor] wires it into OkHttp.
 */
internal object PowBlock {
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
    ): Long? {
        val digest = MessageDigest.getInstance(challenge.algorithm)
        val prefix = challenge.token
        var nonce = 0L
        while (nonce < maxIterations) {
            digest.reset()
            val hash = digest.digest((prefix + nonce).toByteArray(Charsets.UTF_8))
            if (leadingZeroBits(hash) >= challenge.difficulty) return nonce
            nonce++
        }
        return null
    }

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
}
