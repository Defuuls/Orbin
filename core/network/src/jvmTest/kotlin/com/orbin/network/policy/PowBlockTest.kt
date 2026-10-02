package com.orbin.network.policy

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.security.MessageDigest

class PowBlockTest {
    @Test
    fun `parses challenge markers`() {
        val challenge = PowBlock.parse(interstitial(TOKEN, difficulty = 12))
        assertThat(challenge).isNotNull()
        assertThat(challenge!!.token).isEqualTo(TOKEN)
        assertThat(challenge.difficulty).isEqualTo(12)
        assertThat(challenge.algorithm).isEqualTo("SHA-256")
    }

    @Test
    fun `isChallenge only matches powblock interstitials`() {
        assertThat(PowBlock.isChallenge(interstitial(TOKEN, difficulty = 8))).isTrue()
        assertThat(PowBlock.isChallenge("""{"status":"ok"}""")).isFalse()
    }

    @Test
    fun `solved nonce satisfies the difficulty`() {
        val difficulty = 10
        val challenge = PowBlock.parse(interstitial(TOKEN, difficulty))!!
        val nonce = PowBlock.solve(challenge)
        assertThat(nonce).isNotNull()

        // Checked against the JDK's SHA-256, independent of the multiplatform digest that mined it.
        val hash = MessageDigest.getInstance("SHA-256").digest((TOKEN + nonce).toByteArray())
        assertThat(leadingZeroBits(hash)).isAtLeast(difficulty)
    }

    @Test
    fun `solve stops when cancelled`() {
        val challenge = PowBlock.Challenge(token = TOKEN, difficulty = 40, algorithm = "SHA-256")
        assertThat(PowBlock.solve(challenge, maxIterations = Long.MAX_VALUE) { true }).isNull()
    }

    @Test
    fun `mine gives up at its timeout`() {
        val challenge = PowBlock.Challenge(token = TOKEN, difficulty = 40, algorithm = "SHA-256")
        val started = System.currentTimeMillis()
        val nonce = runBlocking { PowBlock.mine(challenge, timeoutMs = 200) }
        assertThat(nonce).isNull()
        assertThat(System.currentTimeMillis() - started).isLessThan(5_000L)
    }

    companion object {
        const val TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcd"

        fun interstitial(
            token: String,
            difficulty: Int,
        ): String =
            """
            <html><head><title>POWBlock Check…</title></head><body>
            <div class=footer>POWBlock v1.8x Enterprise</div>
            <pre id=c style=display:none>$token</pre>
            <pre id=d style=display:none>$difficulty</pre>
            <pre id=h style=display:none>256</pre>
            </body></html>
            """.trimIndent()

        fun leadingZeroBits(hash: ByteArray): Int {
            var bits = 0
            for (byte in hash) {
                val value = byte.toInt() and 0xFF
                if (value == 0) {
                    bits += 8
                    continue
                }
                var mask = 0x80
                while (mask != 0 && (value and mask) == 0) {
                    bits++
                    mask = mask shr 1
                }
                break
            }
            return bits
        }
    }
}
