package com.orbin.data.repository

import com.orbin.core.common.result.OrbinResult
import com.orbin.core.common.result.map
import com.orbin.core.common.result.onSuccess
import com.orbin.core.model.Board
import com.orbin.core.model.ProviderId
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.data.database.dao.BoardDao
import com.orbin.data.database.toDomain
import com.orbin.data.database.toEntity
import com.orbin.data.util.runCatchingProvider
import com.orbin.domain.repository.BoardRepository
import com.orbin.provider.api.ProviderRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class BoardRepositoryImpl(
    private val registry: ProviderRegistry,
    private val boardDao: BoardDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BoardRepository {
    override fun observeBoards(provider: ProviderId): Flow<List<Board>> =
        boardDao
            .observeBoards(provider.value)
            .map { entities ->
                entities.map { it.toDomain() }.filterNot { it.isPermanentlyFiltered() }
            }.onStart {
                if (isStale(provider)) refreshBoards(provider)
            }

    override suspend fun refreshBoards(provider: ProviderId): OrbinResult<List<Board>> =
        withContext(ioDispatcher) {
            val result =
                runCatchingProvider {
                    registry.get(provider)?.getBoards()
                        ?: error("Unknown provider: ${provider.value}")
                }
            result
                .onSuccess { boards ->
                    val now = Clock.System.now().toEpochMilliseconds()
                    boardDao.replaceBoards(
                        provider = provider.value,
                        boards = boards.mapIndexed { index, board -> board.toEntity(provider, index, now) },
                    )
                }.map { boards -> boards.filterNot { board -> board.isPermanentlyFiltered() } }
        }

    private suspend fun isStale(provider: ProviderId): Boolean =
        withContext(ioDispatcher) {
            val cachedAt = boardDao.cachedAtMillis(provider.value)
            cachedAt == null || Clock.System.now().toEpochMilliseconds() - cachedAt > BOARD_CACHE_TTL_MILLIS
        }

    private companion object {
        const val BOARD_CACHE_TTL_MILLIS = 24L * 60 * 60 * 1000
    }
}
