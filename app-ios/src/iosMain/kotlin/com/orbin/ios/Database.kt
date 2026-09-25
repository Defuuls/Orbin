package com.orbin.ios

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.orbin.data.database.OrbinDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * The shared database (`:storage`), opened the iOS way: SQLite bundled into the app through Room's
 * driver, in Application Support, which is backed up but not shown to the user. iOS Data Protection
 * encrypts it at rest; Android, which has no equivalent guarantee, encrypts its copy with SQLCipher.
 *
 * Schema changes need migrations that run on both platforms: Room's `Migration.migrate(connection)`,
 * not the Android-only `SupportSQLiteDatabase` overload.
 */
internal fun openDatabase(): OrbinDatabase =
    Room
        .databaseBuilder<OrbinDatabase>(name = applicationSupportPath(OrbinDatabase.NAME))
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

/**
 * Board preferences (followed and favourite boards, feed limits) in a DataStore file beside the
 * database, read and written by the same `BoardPreferencesStore` Android uses.
 */
internal fun openPreferences(): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath { applicationSupportPath(PREFERENCES_FILE).toPath() }

private const val PREFERENCES_FILE = "orbin.preferences_pb"

@OptIn(ExperimentalForeignApi::class)
private fun applicationSupportPath(file: String): String {
    val directory =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    return requireNotNull(directory?.path) { "No Application Support directory" } + "/" + file
}
