package com.orbin.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.orbin.data.database.dao.BookmarkDao
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.database.dao.HistoryDao
import com.orbin.data.database.dao.RecentSearchDao
import com.orbin.data.database.dao.SavedSearchDao
import com.orbin.data.database.entity.BookmarkEntity
import com.orbin.data.database.entity.DownloadEntity
import com.orbin.data.database.entity.HistoryEntity
import com.orbin.data.database.entity.RecentSearchEntity
import com.orbin.data.database.entity.SavedSearchEntity

/**
 * The app's Room database. Schemas are exported to `data/schemas` (configured by the room
 * convention plugin) so migrations can be added and tested deterministically as the schema grows.
 */
@Database(
    entities = [
        BookmarkEntity::class,
        HistoryEntity::class,
        RecentSearchEntity::class,
        DownloadEntity::class,
        SavedSearchEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class OrbinDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao

    abstract fun historyDao(): HistoryDao

    abstract fun recentSearchDao(): RecentSearchDao

    abstract fun downloadDao(): DownloadDao

    abstract fun savedSearchDao(): SavedSearchDao

    companion object {
        const val NAME = "orbin.db"
    }
}
