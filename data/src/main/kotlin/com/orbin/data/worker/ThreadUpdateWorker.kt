package com.orbin.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.orbin.core.common.result.onSuccess
import com.orbin.domain.notification.ThreadNotifier
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.ThreadRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Periodically refreshes watched threads, updates their latest reply counts, and notifies when new
 * replies have arrived since the user last read the thread. Scheduled by [WatchScheduler].
 *
 * Refreshes run with bounded parallelism rather than strictly one-after-another so a long watch
 * list does not serialize every network round-trip.
 */
@HiltWorker
class ThreadUpdateWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val bookmarkRepository: BookmarkRepository,
        private val threadRepository: ThreadRepository,
        private val notifier: ThreadNotifier,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val watched = bookmarkRepository.watchedBookmarks()
            if (watched.isEmpty()) return Result.success()

            val permits = Semaphore(MAX_PARALLEL_REFRESHES)
            coroutineScope {
                watched
                    .map { bookmark ->
                        async {
                            permits.withPermit {
                                val key = bookmark.key
                                threadRepository
                                    .refreshThread(key.provider, key.board, key.thread)
                                    .onSuccess { thread ->
                                        val latest = thread.stats.replyCount
                                        if (latest > bookmark.latestReplyCount) {
                                            bookmarkRepository.updateLatest(
                                                key,
                                                latest,
                                                thread.stats.isArchived,
                                            )
                                            val unread = latest - bookmark.lastSeenReplyCount
                                            if (unread > 0) {
                                                notifier.notifyThreadUpdate(key, bookmark.title, unread)
                                            }
                                        }
                                    }
                            }
                        }
                    }.awaitAll()
            }
            return Result.success()
        }

        private companion object {
            const val MAX_PARALLEL_REFRESHES = 3
        }
    }
