package com.orbin.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException

/**
 * Wraps SQLCipher's open helper so a database file the current passphrase cannot decrypt is
 * repaired once instead of crash-looping the app on every launch.
 *
 * SQLCipher reports "file is not a database" ([SQLiteNotADatabaseException]) both for a plaintext
 * file and for one encrypted under a different key — it cannot tell the two apart, because with the
 * wrong key the header decrypts to noise either way. That failure surfaces lazily, on whichever
 * background thread first touches a DAO, so it cannot be caught where the database is constructed:
 * it has to be caught around the open itself.
 *
 * [repair] runs at most once per helper, so a repair that does not actually fix the file fails
 * loudly on the retry rather than deleting and recreating the database in a loop.
 */
internal class SelfHealingOpenHelperFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory,
    private val repair: () -> Unit,
) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        SelfHealingOpenHelper({ delegate.create(configuration) }, repair)
}

/** The [SupportSQLiteOpenHelper] created by [SelfHealingOpenHelperFactory]. */
internal class SelfHealingOpenHelper(
    private val createDelegate: () -> SupportSQLiteOpenHelper,
    private val repair: () -> Unit,
) : SupportSQLiteOpenHelper {
    private val lock = Any()
    private var delegate = createDelegate()

    /** Replayed onto the replacement helper, which is created after Room has configured this one. */
    private var writeAheadLoggingEnabled: Boolean? = null
    private var repaired = false

    override val databaseName: String?
        get() = delegate.databaseName

    override val writableDatabase: SupportSQLiteDatabase
        get() = open { it.writableDatabase }

    override val readableDatabase: SupportSQLiteDatabase
        get() = open { it.readableDatabase }

    override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
        synchronized(lock) {
            writeAheadLoggingEnabled = enabled
            delegate.setWriteAheadLoggingEnabled(enabled)
        }
    }

    override fun close() {
        synchronized(lock) { delegate.close() }
    }

    private fun open(openDatabase: (SupportSQLiteOpenHelper) -> SupportSQLiteDatabase): SupportSQLiteDatabase =
        synchronized(lock) {
            try {
                openDatabase(delegate)
            } catch (e: SQLiteNotADatabaseException) {
                if (repaired) throw e
                repaired = true
                // The failed helper holds a half-open connection pool for a file that is about to
                // be rekeyed or deleted; drop it and open the repaired file through a fresh one.
                runCatching { delegate.close() }
                repair()
                delegate =
                    createDelegate().also { helper ->
                        writeAheadLoggingEnabled?.let(helper::setWriteAheadLoggingEnabled)
                    }
                openDatabase(delegate)
            }
        }
}
