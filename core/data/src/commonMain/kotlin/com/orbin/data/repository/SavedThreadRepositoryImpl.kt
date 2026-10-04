package com.orbin.data.repository

import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.SavedThreadSummary
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.data.database.dao.SavedThreadDao
import com.orbin.data.database.toDomain
import com.orbin.data.database.toSavedEntities
import com.orbin.domain.repository.SavedThreadRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class SavedThreadRepositoryImpl(
    private val savedThreadDao: SavedThreadDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SavedThreadRepository {
    override fun observeSaved(): Flow<List<SavedThreadSummary>> =
        savedThreadDao.observeSavedThreads().map { threads ->
            threads.map { saved ->
                SavedThreadSummary(
                    key =
                        ThreadKey(
                            ProviderId(saved.provider),
                            BoardId(saved.board),
                            ThreadId(saved.thread),
                        ),
                    title = saved.title,
                    savedAtMillis = saved.savedAtMillis,
                    postCount = saved.postCount,
                )
            }
        }

    override fun isSaved(key: ThreadKey): Flow<Boolean> = observeSaved().map { saved -> saved.any { it.key == key } }

    override suspend fun save(thread: Thread) {
        withContext(ioDispatcher) {
            val (savedThread, savedPosts) = thread.toSavedEntities(Clock.System.now().toEpochMilliseconds())
            savedThreadDao.save(savedThread, savedPosts)
        }
    }

    override suspend fun load(key: ThreadKey): Thread? =
        withContext(ioDispatcher) {
            val saved =
                savedThreadDao.getSavedThread(key.provider.value, key.board.value, key.thread.value)
                    ?: return@withContext null
            val posts = savedThreadDao.getSavedPosts(key.provider.value, key.board.value, key.thread.value)
            if (posts.isEmpty()) null else saved.toDomain(posts)
        }

    override suspend fun forget(key: ThreadKey) {
        withContext(ioDispatcher) {
            savedThreadDao.delete(key.provider.value, key.board.value, key.thread.value)
        }
    }
}
