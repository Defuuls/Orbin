package com.orbin.data.repository

import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.core.common.result.DataError
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.common.result.map
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.data.util.runCatchingProvider
import com.orbin.domain.repository.BoardRepository
import com.orbin.provider.api.ProviderRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Board repository with a lightweight in-memory cache per provider. Boards rarely change within a
 * session, so the first observer triggers a network refresh and subsequent ones are served from
 * the cached [MutableStateFlow].
 *
 * TODO(persistence): back the cache with Room so the board list survives process death offline.
 */
@Singleton
class BoardRepositoryImpl
    @Inject
    constructor(
        private val registry: ProviderRegistry,
        @Dispatcher(OrbinDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    ) : BoardRepository {
        private val caches = ConcurrentHashMap<ProviderId, MutableStateFlow<List<Board>>>()

        private fun cacheFor(provider: ProviderId) = caches.getOrPut(provider) { MutableStateFlow(emptyList()) }

        override fun observeBoards(provider: ProviderId): Flow<List<Board>> =
            cacheFor(provider).onStart {
                if (cacheFor(provider).value.isEmpty()) refreshBoards(provider)
            }

        override suspend fun refreshBoards(provider: ProviderId): OrbinResult<List<Board>> =
            withContext(ioDispatcher) {
                val result =
                    runCatchingProvider {
                        registry.get(provider)?.getBoards()
                            ?: error("Unknown provider: ${provider.value}")
                    }
                result.also { it.map { boards -> cacheFor(provider).value = boards } }
            }

        override suspend fun getBoard(
            provider: ProviderId,
            board: BoardId,
        ): OrbinResult<Board> {
            val cached = cacheFor(provider).value.firstOrNull { it.id == board }
            if (cached != null) return OrbinResult.Success(cached)
            return when (val refreshed = refreshBoards(provider)) {
                is OrbinResult.Success ->
                    refreshed.data
                        .firstOrNull { it.id == board }
                        ?.let { OrbinResult.Success(it) }
                        ?: OrbinResult.Failure(DataError.NotFound("Board /${board.value}/ not found"))
                is OrbinResult.Failure -> refreshed
            }
        }
    }
