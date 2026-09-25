package com.orbin.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.orbin.data.database.dao.BoardDao
import com.orbin.data.database.dao.BookmarkDao
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.database.dao.HistoryDao
import com.orbin.data.database.dao.RecentSearchDao
import com.orbin.data.database.dao.SavedSearchDao
import com.orbin.data.database.dao.SavedThreadDao
import com.orbin.data.database.entity.BoardEntity
import com.orbin.data.database.entity.BookmarkEntity
import com.orbin.data.database.entity.DownloadEntity
import com.orbin.data.database.entity.HistoryEntity
import com.orbin.data.database.entity.RecentSearchEntity
import com.orbin.data.database.entity.SavedPostEntity
import com.orbin.data.database.entity.SavedSearchEntity
import com.orbin.data.database.entity.SavedThreadEntity

/**
 * The app's Room database, shared by Android and iOS. Schemas are exported to `storage/schemas`
 * (configured by the room convention plugin) so migrations can be added and tested
 * deterministically as the schema grows.
 *
 * Only the schema and its DAOs are shared. Each platform opens the database its own way: Android
 * through its encrypted open helper and migrations (`data`'s `DatabaseModule`), iOS through the
 * bundled SQLite driver.
 */
@Database(
    entities = [
        BookmarkEntity::class,
        HistoryEntity::class,
        RecentSearchEntity::class,
        DownloadEntity::class,
        SavedSearchEntity::class,
        BoardEntity::class,
        SavedThreadEntity::class,
        SavedPostEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@ConstructedBy(OrbinDatabaseConstructor::class)
abstract class OrbinDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao

    abstract fun savedThreadDao(): SavedThreadDao

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun historyDao(): HistoryDao

    abstract fun recentSearchDao(): RecentSearchDao

    abstract fun downloadDao(): DownloadDao

    abstract fun savedSearchDao(): SavedSearchDao

    companion object {
        const val NAME = "orbin.db"
    }
}

/** Room generates the actual for each platform; outside Android this is how the database is built. */
@Suppress("KotlinNoActualForExpect")
expect object OrbinDatabaseConstructor : RoomDatabaseConstructor<OrbinDatabase> {
    override fun initialize(): OrbinDatabase
}
