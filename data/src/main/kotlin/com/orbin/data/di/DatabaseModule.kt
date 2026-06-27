package com.orbin.data.di

import android.content.Context
import androidx.room.Room
import com.orbin.data.database.OrbinDatabase
import com.orbin.data.database.dao.BookmarkDao
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.database.dao.HistoryDao
import com.orbin.data.database.dao.RecentSearchDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the Room database and its DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(
        @ApplicationContext context: Context,
    ): OrbinDatabase =
        Room
            .databaseBuilder(context, OrbinDatabase::class.java, OrbinDatabase.NAME)
            // Pre-1.0 schema churn: recreate on version bumps. Real migrations land before release.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providesBookmarkDao(database: OrbinDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun providesHistoryDao(database: OrbinDatabase): HistoryDao = database.historyDao()

    @Provides
    fun providesRecentSearchDao(database: OrbinDatabase): RecentSearchDao = database.recentSearchDao()

    @Provides
    fun providesDownloadDao(database: OrbinDatabase): DownloadDao = database.downloadDao()
}
