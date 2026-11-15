package com.orbin.domain.repository

import com.orbin.core.model.AppSettings
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionThreadMemoryRepositoryTest {
    @Test
    fun leavingWhileAnAnchorUpdateIsSuspendedCannotRepopulateTheSession() =
        runTest {
            ThreadMemorySession.forgetSession()
            val disk = InMemoryThreadMemoryRepository()
            val gate = CompletableDeferred<Unit>()
            val delayed =
                object : ThreadMemoryRepository by disk {
                    override suspend fun get(key: ThreadKey): com.orbin.core.model.ThreadMemory {
                        gate.await()
                        return disk.get(key)
                    }
                }
            val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(42))
            val prefs = MutableStateFlow(AppSettings(historySessionOnly = true))
            val repository = SessionThreadMemoryRepository(delayed, prefs)
            val write = launch { repository.update(key) { it.copy(anchorPostId = 99, seenPostIds = setOf(99)) } }
            runCurrent()
            HistorySession.forgetSession()
            gate.complete(Unit)
            write.join()
            assertTrue(repository.observeCurrent().first().isEmpty())
            assertEquals(null, disk.get(key).anchorPostId)
        }

    @Test
    fun privateAnchorsSeenAndHiddenPostsNeverReachPersistentMemoryOrExports() =
        runTest {
            ThreadMemorySession.forgetSession()
            val disk = InMemoryThreadMemoryRepository()
            val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(42))
            disk.update(key) { it.copy(seenPostIds = setOf(1)) }
            val prefs = MutableStateFlow(AppSettings(historySessionOnly = true))
            val sessionRepository = SessionThreadMemoryRepository(disk, prefs)
            sessionRepository.update(
                key,
            ) { it.copy(anchorPostId = 7, seenPostIds = it.seenPostIds + 7, hiddenPostIds = setOf(9)) }
            assertEquals(7L, sessionRepository.get(key).anchorPostId)
            assertEquals(setOf(1L), disk.get(key).seenPostIds)
            assertTrue(disk.get(key).hiddenPostIds.isEmpty())
            assertTrue(sessionRepository.observeAll().first().isEmpty())
            HistorySession.forgetSession()
            assertEquals(setOf(1L), sessionRepository.get(key).seenPostIds)
            prefs.value = prefs.value.copy(historySessionOnly = false)
            assertEquals(
                setOf(1L),
                sessionRepository
                    .observeAll()
                    .first()
                    .single()
                    .seenPostIds,
            )
        }
}
