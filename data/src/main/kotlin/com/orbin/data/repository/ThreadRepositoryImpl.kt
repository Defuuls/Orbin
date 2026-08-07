package com.orbin.data.repository

import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.data.util.runCatchingProvider
import com.orbin.domain.repository.ThreadRepository
import com.orbin.domain.usecase.BuildReplyGraphUseCase
import com.orbin.provider.api.ProviderRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread repository. Loads threads through the active provider with in-memory caching for
 * fast repeat loads (500ms-2s faster thread reopening). Cache is keyed by ThreadKey and expires
 * after 30 minutes of inactivity. The Flow shape leaves room for future live background refresh.
 */
@Singleton
class ThreadRepositoryImpl
    @Inject
    constructor(
        private val registry: ProviderRegistry,
        private val buildReplyGraph: BuildReplyGraphUseCase,
        @Dispatcher(OrbinDispatcher.IO) private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) : ThreadRepository {
        private data class CachedThreadEntry(
            val thread: Thread,
            val cachedAtMillis: Long,
        ) {
            fun isStale(
                nowMillis: Long,
                ttlMillis: Long = CACHE_TTL_MILLIS,
            ): Boolean = nowMillis - cachedAtMillis > ttlMillis
        }

        private val threadCache = mutableMapOf<ThreadKey, CachedThreadEntry>()
        private val cacheMutex = Mutex()

        override fun observeThread(
            key: ThreadKey,
            forceRefresh: Boolean,
        ): Flow<OrbinResult<Thread>> =
            flow {
                emit(refreshThread(key.provider, key.board, key.thread, forceRefresh))
            }

        override suspend fun refreshThread(
            provider: ProviderId,
            board: BoardId,
            thread: ThreadId,
            forceRefresh: Boolean,
        ): OrbinResult<Thread> =
            withContext(ioDispatcher) {
                val key = ThreadKey(provider, board, thread)

                // Try cache first (fast path for repeated navigation) unless the caller has asked
                // for the network explicitly, which is what a pull-to-refresh does.
                if (!forceRefresh) {
                    cacheMutex.withLock {
                        val cached = threadCache[key]
                        if (cached != null && !cached.isStale(System.currentTimeMillis())) {
                            return@withContext OrbinResult.Success(cached.thread)
                        }
                    }
                }

                // Network load.
                runCatchingProvider {
                    val loaded =
                        registry.get(provider)?.getThread(board, thread)
                            ?: error("Unknown provider: ${provider.value}")
                    val enriched = buildReplyGraph(loaded)

                    // Cache the result for future loads.
                    cacheMutex.withLock {
                        threadCache[key] = CachedThreadEntry(enriched, System.currentTimeMillis())
                    }

                    enriched
                }
            }

        private companion object {
            const val CACHE_TTL_MILLIS = 30 * 60 * 1000L // 30 minutes
        }
    }
