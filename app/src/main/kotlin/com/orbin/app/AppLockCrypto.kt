package com.orbin.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Backs the app-lock unlock gate with an Android Keystore key that requires a fresh
 * BIOMETRIC_STRONG authentication for every use (no grace period). A `BiometricPrompt` success
 * callback alone only proves the callback fired, which instrumentation that bypasses the real
 * sensor can also trigger. Requiring the returned `CryptoObject`'s cipher to complete a real
 * Keystore-backed [Cipher.doFinal] call ties "unlocked" to an operation the TEE/StrongBox itself
 * only authorizes after a genuine biometric match.
 */
internal object AppLockCrypto {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "orbin_app_lock_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val REQUIRE_AUTH_PER_USE_SECONDS = 0
    private val GATE_PLAINTEXT = "orbin-app-lock-gate".toByteArray()

    /** A freshly initialized cipher to hand to `BiometricPrompt.authenticate`. */
    fun createUnlockCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return cipher
    }

    /** Runs the actual crypto operation; throws if [cipher] wasn't genuinely authorized. */
    fun verify(cipher: Cipher) {
        cipher.doFinal(GATE_PLAINTEXT)
    }

    /** Drops the key so a fresh one is generated (and re-gated) on the next unlock attempt. */
    fun invalidate() {
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = keyStore()
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(REQUIRE_AUTH_PER_USE_SECONDS, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
}
