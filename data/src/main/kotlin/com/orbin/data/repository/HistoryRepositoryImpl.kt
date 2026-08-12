package com.orbin.data.repository

import com.orbin.core.model.BoardId
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
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

        override fun observeVisitedKeys(): Flow<Set<ThreadKey>> =
            dao.observeKeys().map { rows ->
                rows.mapTo(mutableSetOf()) { row ->
                    ThreadKey(ProviderId(row.provider), BoardId(row.board), ThreadId(row.thread))
                }
            }

        override suspend fun getEntry(key: ThreadKey): HistoryEntry? =
            dao.getEntry(key.provider.value, key.board.value, key.thread.value)?.toDomain()

        override suspend fun record(entry: HistoryEntry) {
            // Wrapped in @Transaction to prevent lost-update races between record() and
            // updateScrollPosition() when both run concurrently (e.g. pull-to-refresh while scrolling).
            dao.recordEntry(entry.toEntity())
        }

        override suspend fun updateScrollPosition(
            key: ThreadKey,
            postId: PostId,
            offsetPx: Int,
        ) = dao.updateScrollPosition(key.provider.value, key.board.value, key.thread.value, postId.value, offsetPx)

        override suspend fun clear() = dao.clear()
    }
