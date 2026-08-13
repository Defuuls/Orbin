package com.orbin.data.di

import android.content.Context
import androidx.room.Room
import com.orbin.data.crypto.DatabasePassphrase
import com.orbin.data.database.DatabaseKeyRepair
import com.orbin.data.database.MIGRATION_2_3
import com.orbin.data.database.MIGRATION_3_4
import com.orbin.data.database.MIGRATION_4_5
import com.orbin.data.database.MIGRATION_5_6
import com.orbin.data.database.OrbinDatabase
import com.orbin.data.database.SelfHealingOpenHelperFactory
import com.orbin.data.database.dao.BoardDao
import com.orbin.data.database.dao.BookmarkDao
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.database.dao.HistoryDao
import com.orbin.data.database.dao.RecentSearchDao
import com.orbin.data.database.dao.SavedSearchDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

/** Provides the Room database (encrypted at rest via SQLCipher) and its DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(
        @ApplicationContext context: Context,
    ): OrbinDatabase {
        // sqlcipher-android does not auto-load its native library the way the older
        // android-database-sqlcipher artifact did; without this, SupportOpenHelperFactory's first
        // open throws UnsatisfiedLinkError on nativeOpen.
        System.loadLibrary("sqlcipher")
        val (passphrase, wipeDatabase) = DatabasePassphrase(context).resolve()
        if (wipeDatabase) {
            // No usable passphrase existed yet: either a pre-encryption install left a plaintext
            // orbin.db that SQLCipher cannot open, or the stored key was lost. Drop the old file so
            // the database is (re)created encrypted with the fresh passphrase.
            context.deleteDatabase(OrbinDatabase.NAME)
        }
        return Room
            .databaseBuilder(context, OrbinDatabase::class.java, OrbinDatabase.NAME)
            // The passphrase array must stay intact for as long as the database is open: Room opens
            // lazily (on whichever thread first touches a DAO, long after this function returns) and
            // SQLCipher keeps the array by reference, re-reading it every time the connection pool
            // opens another connection. Zeroing it here to keep key material off the heap is what
            // shipped in 82-Alioth — it keyed every open with 32 zero bytes and crash-looped the app
            // with "file is not a database". [DatabaseKeyRepair] recovers the databases that created.
            .openHelperFactory(
                SelfHealingOpenHelperFactory(
                    delegate = SupportOpenHelperFactory(passphrase),
                    repair = DatabaseKeyRepair(context, passphrase)::repair,
                ),
            ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            // Only v1 predates exported schemas, so it cannot be migrated faithfully and is
            // recreated. Every version from 2 on migrates: a missing migration now fails loudly
            // at open time instead of silently dropping the user's bookmarks and history.
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
            .build()
    }

    @Provides
    fun providesBoardDao(database: OrbinDatabase): BoardDao = database.boardDao()

    @Provides
    fun providesBookmarkDao(database: OrbinDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun providesHistoryDao(database: OrbinDatabase): HistoryDao = database.historyDao()

    @Provides
    fun providesRecentSearchDao(database: OrbinDatabase): RecentSearchDao = database.recentSearchDao()

    @Provides
    fun providesDownloadDao(database: OrbinDatabase): DownloadDao = database.downloadDao()

    @Provides
    fun providesSavedSearchDao(database: OrbinDatabase): SavedSearchDao = database.savedSearchDao()
}
