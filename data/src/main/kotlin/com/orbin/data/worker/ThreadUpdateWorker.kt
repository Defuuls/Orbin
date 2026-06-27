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

/**
 * Periodically refreshes watched threads, updates their latest reply counts, and notifies when new
 * replies have arrived since the user last read the thread. Scheduled by [WatchScheduler].
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
            bookmarkRepository.watchedBookmarks().forEach { bookmark ->
                val key = bookmark.key
                threadRepository.refreshThread(key.provider, key.board, key.thread).onSuccess { thread ->
                    val latest = thread.stats.replyCount
                    if (latest > bookmark.latestReplyCount) {
                        bookmarkRepository.updateLatest(key, latest, thread.stats.isArchived)
                        val unread = latest - bookmark.lastSeenReplyCount
                        if (unread > 0) notifier.notifyThreadUpdate(key, bookmark.title, unread)
                    }
                }
            }
            return Result.success()
        }
    }
