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
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread repository. Loads a thread through the active provider and enriches it with backlinks
 * ([BuildReplyGraphUseCase]) before exposing it. [observeThread] currently emits a single loaded
 * value; the Flow shape leaves room for live background refresh to push updates later.
 *
 * TODO(refresh): poll watched threads and re-emit new replies; cache via Room for offline reads.
 */
@Singleton
class ThreadRepositoryImpl
    @Inject
    constructor(
        private val registry: ProviderRegistry,
        private val buildReplyGraph: BuildReplyGraphUseCase,
        @Dispatcher(OrbinDispatcher.IO) private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) : ThreadRepository {
        override fun observeThread(key: ThreadKey): Flow<OrbinResult<Thread>> =
            flow {
                emit(refreshThread(key.provider, key.board, key.thread))
            }

        override suspend fun refreshThread(
            provider: ProviderId,
            board: BoardId,
            thread: ThreadId,
        ): OrbinResult<Thread> =
            withContext(ioDispatcher) {
                runCatchingProvider {
                    val loaded =
                        registry.get(provider)?.getThread(board, thread)
                            ?: error("Unknown provider: ${provider.value}")
                    buildReplyGraph(loaded)
                }
            }
    }
