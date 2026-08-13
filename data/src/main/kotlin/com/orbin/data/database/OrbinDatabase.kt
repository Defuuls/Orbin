package com.orbin.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
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
        BoardEntity::class,
        SavedThreadEntity::class,
        SavedPostEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
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
