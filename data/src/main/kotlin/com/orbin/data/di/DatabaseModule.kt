package com.orbin.data.di

import android.content.Context
import androidx.room.Room
import com.orbin.data.crypto.DatabasePassphrase
import com.orbin.data.database.OrbinDatabase
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
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            // Pre-1.0 schema churn: recreate on version bumps. Real migrations land before release.
            .fallbackToDestructiveMigration()
            .build()
    }

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
