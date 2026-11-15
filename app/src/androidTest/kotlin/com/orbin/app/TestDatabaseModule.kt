package com.orbin.app

import android.content.Context
import com.orbin.data.database.OrbinDatabase
import com.orbin.data.database.dao.BoardDao
import com.orbin.data.database.dao.BookmarkDao
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.database.dao.HistoryDao
import com.orbin.data.database.dao.RecentSearchDao
import com.orbin.data.database.dao.SavedSearchDao
import com.orbin.data.database.dao.SavedThreadDao
import com.orbin.data.di.DatabaseModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.util.UUID
import javax.inject.Singleton

/** Keep the real SQLCipher/migration/provider path, with an isolated database per Hilt component. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides
    @Singleton
    fun databaseFile(): TestDatabaseFile = TestDatabaseFile("orbin-test-${UUID.randomUUID()}.db")

    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
        file: TestDatabaseFile,
    ): OrbinDatabase = DatabaseModule.openDatabase(context, file.name)

    @Provides fun boards(database: OrbinDatabase): BoardDao = database.boardDao()

    @Provides fun bookmarks(database: OrbinDatabase): BookmarkDao = database.bookmarkDao()

    @Provides fun downloads(database: OrbinDatabase): DownloadDao = database.downloadDao()

    @Provides fun history(database: OrbinDatabase): HistoryDao = database.historyDao()

    @Provides fun recentSearches(database: OrbinDatabase): RecentSearchDao = database.recentSearchDao()

    @Provides fun savedSearches(database: OrbinDatabase): SavedSearchDao = database.savedSearchDao()

    @Provides fun savedThreads(database: OrbinDatabase): SavedThreadDao = database.savedThreadDao()
}

data class TestDatabaseFile(
    val name: String,
)
