package com.orbin.data.database

import android.content.Context
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * Recovers an `orbin.db` that the passphrase from `DatabasePassphrase` can no longer open.
 *
 * One historical cohort is recoverable without data loss: 82-Alioth (versionCode 102 and 103)
 * zeroed the passphrase array as a heap-hygiene measure immediately after handing it to SQLCipher's
 * factory, but Room opens the database lazily, so the array was already all zeros by the time
 * SQLCipher read it. Every database those builds *created* is therefore keyed with 32 zero bytes.
 * Such a file still opens under that legacy key and is rekeyed here to the real passphrase, so the
 * user keeps their bookmarks, history and downloads.
 *
 * Anything else that will not open — a plaintext database from a pre-encryption install, or a file
 * whose key is genuinely gone — is deleted so Room recreates it empty. Losing local caches is
 * recoverable; a startup crash on every launch is not.
 */
internal class DatabaseKeyRepair(
    private val context: Context,
    private val passphrase: ByteArray,
) {
    fun repair() {
        val databaseFile = context.getDatabasePath(OrbinDatabase.NAME)
        if (databaseFile.exists() && rekeyFromLegacyPassphrase(databaseFile)) return
        context.deleteDatabase(OrbinDatabase.NAME)
    }

    private fun rekeyFromLegacyPassphrase(databaseFile: File): Boolean =
        runCatching {
            SQLiteDatabase
                .openDatabase(
                    databaseFile.absolutePath,
                    LEGACY_ZEROED_PASSPHRASE,
                    null,
                    // No CREATE_IF_NECESSARY: this must probe the existing file, never create one.
                    SQLiteDatabase.OPEN_READWRITE,
                    null,
                ).use { it.changePassword(passphrase) }
        }.isSuccess

    private companion object {
        const val PASSPHRASE_BYTES = 32

        /** What 102/103 actually keyed their databases with: a zeroed 32-byte passphrase. */
        val LEGACY_ZEROED_PASSPHRASE = ByteArray(PASSPHRASE_BYTES)
    }
}
