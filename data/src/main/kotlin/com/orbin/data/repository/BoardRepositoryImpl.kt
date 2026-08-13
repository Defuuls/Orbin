package com.orbin.data.repository

import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.common.result.map
import com.orbin.core.model.Board
import com.orbin.core.model.ProviderId
import com.orbin.data.database.dao.BoardDao
import com.orbin.data.database.toDomain
import com.orbin.data.database.toEntity
import com.orbin.data.util.runCatchingProvider
import com.orbin.domain.repository.BoardRepository
import com.orbin.provider.api.ProviderRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Board repository backed by Room, so a provider's board list survives process death and is
 * readable with no network at all — the offline banner used to appear over an empty board list,
 * because the previous cache lived only in memory for the lifetime of the process.
 *
 * The cached list is served immediately and a refresh runs behind it when the cache is empty or
 * older than [BOARD_CACHE_TTL_MILLIS]. A failed refresh leaves the cached rows in place: stale
 * boards are worth more than none, and boards change on the order of months.
 */
@Singleton
class BoardRepositoryImpl
    @Inject
    constructor(
        private val registry: ProviderRegistry,
        private val boardDao: BoardDao,
        @Dispatcher(OrbinDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    ) : BoardRepository {
        override fun observeBoards(provider: ProviderId): Flow<List<Board>> =
            boardDao
                .observeBoards(provider.value)
                .map { entities -> entities.map { it.toDomain() } }
                .onStart {
                    if (isStale(provider)) refreshBoards(provider)
                }

        override suspend fun refreshBoards(provider: ProviderId): OrbinResult<List<Board>> =
            withContext(ioDispatcher) {
                val result =
                    runCatchingProvider {
                        registry.get(provider)?.getBoards()
                            ?: error("Unknown provider: ${provider.value}")
                    }
                result.also {
                    it.map { boards ->
                        val now = System.currentTimeMillis()
                        boardDao.replaceBoards(
                            provider = provider.value,
                            boards = boards.mapIndexed { index, board -> board.toEntity(provider, index, now) },
                        )
                    }
                }
            }

        private suspend fun isStale(provider: ProviderId): Boolean =
            withContext(ioDispatcher) {
                val cachedAt = boardDao.cachedAtMillis(provider.value)
                cachedAt == null || System.currentTimeMillis() - cachedAt > BOARD_CACHE_TTL_MILLIS
            }

        private companion object {
            /** Boards change rarely; a day-old list is fine to show while a refresh runs. */
            const val BOARD_CACHE_TTL_MILLIS = 24L * 60 * 60 * 1000
        }
    }
