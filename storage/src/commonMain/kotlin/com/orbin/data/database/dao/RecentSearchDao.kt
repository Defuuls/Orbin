package com.orbin.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.orbin.data.database.entity.RecentSearchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentSearchDao {
    @Query("SELECT * FROM recent_searches ORDER BY lastUsedMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentSearchEntity>>

    @Upsert
    suspend fun upsert(entry: RecentSearchEntity)

    @Query("DELETE FROM recent_searches")
    suspend fun clear()
}
