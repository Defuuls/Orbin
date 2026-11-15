package com.orbin.domain.repository

import com.orbin.core.model.AppSettings
import com.orbin.core.model.BoardId
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionHistoryRepositoryTest {
    @Test
    fun aVisitWaitingForSettingsCannotRepopulateHistoryAfterLeaving() =
        runTest {
            HistorySession.forgetSession()
            val gate = CompletableDeferred<Unit>()
            val settings =
                flow {
                    gate.await()
                    emit(AppSettings(historySessionOnly = true))
                }
            val disk = RecordingHistory()
            val repository = SessionHistoryRepository(disk, settings, backgroundScope)
            val write = launch { repository.record(visit) }
            runCurrent()
            HistorySession.forgetSession()
            gate.complete(Unit)
            write.join()
            assertTrue(repository.observeHistory().first().isEmpty())
            assertEquals(0, disk.writes)
        }

    @Test
    fun privateVisitsAndAnchorsNeverReachDiskOrPortableHistoryAndEndOnBackground() =
        runTest {
            HistorySession.forgetSession()
            val disk = RecordingHistory()
            val prefs = MutableStateFlow(AppSettings(historySessionOnly = true))
            val repository = SessionHistoryRepository(disk, prefs, backgroundScope)
            runCurrent()
            repository.record(visit)
            repository.updateScrollPosition(key, PostId(77), 31)
            assertEquals(0, disk.writes)
            assertTrue(disk.rows.value.isEmpty())
            assertTrue(repository.observeAllEntries().first().isEmpty())
            assertEquals(PostId(77), repository.getEntry(key)?.lastReadPostId)
            assertEquals(setOf(key), repository.observeVisitedKeys().first())
            HistorySession.forgetSession()
            assertTrue(repository.observeHistory().first().isEmpty())
        }

    @Test
    fun disablingTrackingClearsOldRecordsAndBlocksFurtherVisitsAndScrollWrites() =
        runTest {
            val disk = RecordingHistory()
            val prefs = MutableStateFlow(AppSettings())
            val repository = SessionHistoryRepository(disk, prefs, backgroundScope)
            runCurrent()
            repository.record(visit)
            assertEquals(1, disk.writes)
            prefs.value = prefs.value.copy(saveBrowsingHistory = false)
            runCurrent()
            repository.record(visit)
            repository.updateScrollPosition(key, PostId(99), 11)
            assertEquals(1, disk.writes)
            assertTrue(disk.rows.value.isEmpty())
            assertTrue(repository.observeAllEntries().first().isEmpty())
        }

    private class RecordingHistory : HistoryRepository {
        val rows = MutableStateFlow<List<HistoryEntry>>(emptyList())
        var writes = 0

        override fun observeHistory(): Flow<List<HistoryEntry>> = rows

        override fun observeVisitedKeys(): Flow<Set<ThreadKey>> = rows.map { it.mapTo(mutableSetOf()) { r -> r.key } }

        override suspend fun getEntry(key: ThreadKey): HistoryEntry? = rows.value.firstOrNull { it.key == key }

        override suspend fun record(entry: HistoryEntry) {
            writes++
            rows.value = listOf(entry)
        }

        override suspend fun updateScrollPosition(
            key: ThreadKey,
            postId: PostId,
            offsetPx: Int,
        ) {
            writes++
            rows.value = rows.value.map { it.copy(lastReadPostId = postId, lastReadOffsetPx = offsetPx) }
        }

        override suspend fun clear() {
            rows.value = emptyList()
        }
    }

    private companion object {
        val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(42))
        val visit = HistoryEntry(key, "A local visit", lastVisitedMillis = 1)
    }
}
