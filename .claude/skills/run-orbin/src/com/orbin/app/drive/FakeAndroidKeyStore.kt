package com.orbin.app.drive

import android.security.keystore.KeyGenParameterSpec
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Robolectric has no AndroidKeyStore, and the app keeps its settings DataStore and DB passphrase
 * encrypted under a Keystore AES key (`LocalDataCipher`). This registers an in-memory provider of
 * that name — a KeyStore plus an AES KeyGenerator that honours KeyGenParameterSpec's alias — so
 * the app's real encryption code runs against a software key.
 */
object FakeAndroidKeyStore {
    private val keys = Collections.synchronizedMap(mutableMapOf<String, Key>())

    fun install() {
        if (Security.getProvider("AndroidKeyStore") != null) return
        Security.addProvider(
            object : Provider("AndroidKeyStore", 1.0, "in-memory stand-in for Robolectric") {
                init {
                    put("KeyStore.AndroidKeyStore", Store::class.java.name)
                    put("KeyGenerator.AES", AesGenerator::class.java.name)
                }
            },
        )
    }

    class Store : KeyStoreSpi() {
        override fun engineGetKey(alias: String, password: CharArray?): Key? = keys[alias]
        override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null
        override fun engineGetCertificate(alias: String): Certificate? = null
        override fun engineGetCreationDate(alias: String): Date = Date()
        override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<Certificate>?) {
            keys[alias] = key
        }
        override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<Certificate>?) = error("unsupported")
        override fun engineSetCertificateEntry(alias: String, cert: Certificate) = error("unsupported")
        override fun engineDeleteEntry(alias: String) { keys.remove(alias) }
        override fun engineAliases(): Enumeration<String> = Collections.enumeration(keys.keys.toList())
        override fun engineContainsAlias(alias: String) = alias in keys
        override fun engineSize() = keys.size
        override fun engineIsKeyEntry(alias: String) = alias in keys
        override fun engineIsCertificateEntry(alias: String) = false
        override fun engineGetCertificateAlias(cert: Certificate): String? = null
        override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit
        override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
    }

    class AesGenerator : KeyGeneratorSpi() {
        private var alias = "default"
        private var bits = 256
        private var random = SecureRandom()

        override fun engineInit(random: SecureRandom) { this.random = random }
        override fun engineInit(params: AlgorithmParameterSpec, random: SecureRandom?) {
            val spec = params as KeyGenParameterSpec
            alias = spec.keystoreAlias
            if (spec.keySize > 0) bits = spec.keySize
            random?.let { this.random = it }
        }
        override fun engineInit(keysize: Int, random: SecureRandom) { bits = keysize; this.random = random }
        override fun engineGenerateKey(): SecretKey =
            SecretKeySpec(ByteArray(bits / 8).also(random::nextBytes), "AES").also { keys[alias] = it }
    }
}
