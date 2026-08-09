package com.orbin.core.testing.repository

import com.orbin.core.common.network.DnsPrivacyMonitor
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.DownloadRecord
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.PostId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.UpdateStatus
import com.orbin.domain.repository.DownloadRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.UpdateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory repositories for driving screens under test.
 *
 * These exist so a Compose test can construct a real ViewModel with real state rather than reach
 * for Hilt: the screens take their ViewModel as a defaulted parameter, so supplying one directly
 * is both simpler and more honest about what is under test.
 */
class FakeDownloadRepository(
    initial: List<DownloadRecord> = emptyList(),
) : DownloadRepository {
    private val state = MutableStateFlow(initial)

    override fun observeDownloads(): Flow<List<DownloadRecord>> = state

    override suspend fun enqueue(
        url: String,
        fileName: String,
        boardId: String?,
        threadId: Long?,
        threadTitle: String?,
    ): Long = 0L

    override suspend fun refreshStatuses() = Unit

    override suspend fun clearHistory() {
        state.value = emptyList()
    }

    override suspend fun retry(id: Long): Long = id

    override suspend fun writeTextFile(
        fileName: String,
        content: String,
    ): Boolean = true
}

class FakeHistoryRepository(
    initial: List<HistoryEntry> = emptyList(),
) : HistoryRepository {
    private val state = MutableStateFlow(initial)

    override fun observeHistory(): Flow<List<HistoryEntry>> = state

    override fun observeVisitedKeys(): Flow<Set<ThreadKey>> =
        state.map { entries -> entries.mapTo(mutableSetOf()) { it.key } }

    override suspend fun getEntry(key: ThreadKey): HistoryEntry? = state.value.find { it.key == key }

    override suspend fun record(entry: HistoryEntry) {
        val existing = state.value.find { it.key == entry.key }
        val merged =
            if (existing != null) {
                entry.copy(lastReadPostId = existing.lastReadPostId, lastReadOffsetPx = existing.lastReadOffsetPx)
            } else {
                entry
            }
        state.value = state.value.filterNot { it.key == entry.key } + merged
    }

    override suspend fun updateScrollPosition(
        key: ThreadKey,
        postId: PostId,
        offsetPx: Int,
    ) {
        state.value =
            state.value.map {
                if (it.key == key) it.copy(lastReadPostId = postId, lastReadOffsetPx = offsetPx) else it
            }
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}

/** Reports whatever [status] is set to; defaults to "no update", the quiet case. */
class FakeUpdateRepository(
    var status: OrbinResult<UpdateStatus> = OrbinResult.Success(UpdateStatus.UpToDate),
) : UpdateRepository {
    override suspend fun checkForUpdate(currentVersionName: String): OrbinResult<UpdateStatus> = status
}

/** Reports DNS as encrypted unless a test says otherwise. */
class FakeDnsPrivacyMonitor(
    fallbackActive: Boolean = false,
) : DnsPrivacyMonitor {
    private val state = MutableStateFlow(fallbackActive)

    override val usingSystemFallback: Flow<Boolean> = state

    fun setFallbackActive(active: Boolean) {
        state.value = active
    }
}
