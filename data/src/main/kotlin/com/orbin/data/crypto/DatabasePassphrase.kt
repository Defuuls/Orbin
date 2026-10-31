package com.orbin.data.crypto

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

/**
 * Supplies the SQLCipher passphrase for the Room database. A random passphrase is generated once,
 * stored encrypted (via [LocalDataCipher]) in a dedicated prefs file, and decrypted on each open.
 *
 * [Resolved.wipeDatabase] is true when no usable passphrase existed yet — either a pre-encryption
 * install whose plaintext `orbin.db` SQLCipher cannot open, or a recovery case where the stored
 * passphrase can no longer be decrypted (e.g. the Keystore key was lost). In both cases the caller
 * must drop the existing database file before opening with the freshly generated passphrase.
 *
 * Every encrypted database in the process (Room's `orbin.db` and the media cache) shares this one
 * passphrase, so it is resolved once per process and the same [Resolved] - including its
 * [Resolved.wipeDatabase] verdict - is handed to every caller. Resolving independently let two
 * first-launch callers each generate and store a different key, and after a key loss only the first
 * caller was told to wipe, leaving the other database keyed with a passphrase that no longer exists.
 */
internal class DatabasePassphrase(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun resolve(): Resolved = synchronized(lock) { resolved ?: resolveOnce().also { resolved = it } }

    private fun resolveOnce(): Resolved {
        prefs.getString(KEY_PASSPHRASE, null)?.let { stored ->
            runCatching {
                val decoded = Base64.decode(stored, Base64.NO_WRAP)
                LocalDataCipher.decrypt(decoded)
            }.getOrNull()
                ?.let { return Resolved(it, wipeDatabase = false) }
        }
        val fresh = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val persisted =
            runCatching {
                val encrypted = LocalDataCipher.encrypt(fresh)
                val wrapped = Base64.encodeToString(encrypted, Base64.NO_WRAP)
                prefs.edit().putString(KEY_PASSPHRASE, wrapped).commit()
            }.getOrDefault(false)
        return Resolved(fresh, wipeDatabase = persisted)
    }

    data class Resolved(
        val passphrase: ByteArray,
        val wipeDatabase: Boolean,
    ) {
        override fun equals(other: Any?): Boolean =
            this === other ||
                (other is Resolved && passphrase.contentEquals(other.passphrase) && wipeDatabase == other.wipeDatabase)

        override fun hashCode(): Int = passphrase.contentHashCode() * HASH_MULTIPLIER + wipeDatabase.hashCode()
    }

    private companion object {
        private val lock = Any()

        /** The process-wide result; never cleared, since the passphrase cannot change while running. */
        private var resolved: Resolved? = null

        const val PREFS_NAME = "orbin_db_key"
        const val KEY_PASSPHRASE = "db_passphrase"
        const val PASSPHRASE_BYTES = 32
        const val HASH_MULTIPLIER = 31
    }
}
