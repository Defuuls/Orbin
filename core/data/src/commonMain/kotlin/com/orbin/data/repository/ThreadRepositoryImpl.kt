package com.orbin.data.repository

import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.data.util.runCatchingProvider
import com.orbin.domain.repository.ThreadRepository
import com.orbin.domain.usecase.BuildReplyGraphUseCase
import com.orbin.provider.api.EngineKind
import com.orbin.provider.api.ProviderRegistry
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ThreadRepositoryImpl(
    private val registry: ProviderRegistry,
    private val buildReplyGraph: BuildReplyGraphUseCase = BuildReplyGraphUseCase(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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

    private val threadCache = LinkedHashMap<ThreadKey, CachedThreadEntry>()
    private val cacheMutex = Mutex()

    override fun observeThread(
        key: ThreadKey,
        forceRefresh: Boolean,
    ): Flow<OrbinResult<Thread>> =
        flow {
            val cached = if (forceRefresh) null else cachedThread(key)
            if (cached != null) emit(OrbinResult.Success(cached))
            val fresh = refreshThread(key.provider, key.board, key.thread, forceRefresh = true)
            if (cached == null || fresh is OrbinResult.Success) emit(fresh)
        }

    private suspend fun cachedThread(key: ThreadKey): Thread? =
        cacheMutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            threadCache.entries.removeAll { it.value.isStale(now) }
            val entry = threadCache.remove(key)
            if (entry != null) {
                threadCache[key] = entry
                entry.thread
            } else {
                null
            }
        }

    override suspend fun refreshThread(
        provider: ProviderId,
        board: BoardId,
        thread: ThreadId,
        forceRefresh: Boolean,
    ): OrbinResult<Thread> =
        withContext(ioDispatcher) {
            val key = ThreadKey(provider, board, thread)

            if (!forceRefresh) {
                cacheMutex.withLock {
                    val now = Clock.System.now().toEpochMilliseconds()
                    threadCache.entries.removeAll { it.value.isStale(now) }
                    val cached = threadCache.remove(key)
                    if (cached != null) {
                        threadCache[key] = cached
                        return@withContext OrbinResult.Success(cached.thread)
                    }
                }
            }

            runCatchingProvider {
                val loaded =
                    registry.get(provider)?.getThread(board, thread)
                        ?: error("Unknown provider: ${provider.value}")
                val enriched = buildReplyGraph(loaded)

                cacheMutex.withLock {
                    val now = Clock.System.now().toEpochMilliseconds()
                    threadCache.entries.removeAll { it.value.isStale(now) }
                    threadCache.remove(key)
                    threadCache[key] = CachedThreadEntry(enriched, now)
                    while (threadCache.size > MAX_CACHED_THREADS) {
                        val eldestKey = threadCache.keys.firstOrNull() ?: break
                        threadCache.remove(eldestKey)
                    }
                }

                enriched
            }
        }

    override fun threadWebUrl(key: ThreadKey): String? {
        val meta = registry.get(key.provider)?.metadata ?: return null
        val base = meta.baseUrl.trimEnd('/')
        return when {
            meta.engine == EngineKind.FOURCHAN || key.provider.value == "fourchan" ->
                "$base/${key.board.value}/thread/${key.thread.value}"
            else -> "$base/${key.board.value}/res/${key.thread.value}.html"
        }
    }

    private companion object {
        const val CACHE_TTL_MILLIS = 30 * 60 * 1000L
        const val MAX_CACHED_THREADS = 12
    }
}
