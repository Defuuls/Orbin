package com.orbin.data.repository

import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.PostId
import com.orbin.core.model.ThreadKey
import com.orbin.data.database.dao.HistoryDao
import com.orbin.data.database.toDomain
import com.orbin.data.database.toEntity
import com.orbin.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val HISTORY_LIMIT = 200

/** Room-backed [HistoryRepository], capped to the most recent [HISTORY_LIMIT] threads. */
@Singleton
class HistoryRepositoryImpl
    @Inject
    constructor(
        private val dao: HistoryDao,
    ) : HistoryRepository {
        override fun observeHistory(): Flow<List<HistoryEntry>> =
            dao.observeRecent(HISTORY_LIMIT).map { list -> list.map { it.toDomain() } }

        override suspend fun record(entry: HistoryEntry) = dao.upsert(entry.toEntity())

        override suspend fun updateLastRead(
            key: ThreadKey,
            postId: PostId,
        ) = dao.updateLastRead(key.provider.value, key.board.value, key.thread.value, postId.value)

        override suspend fun clear() = dao.clear()
    }
