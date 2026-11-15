package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadAvailability
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.domain.bookmarks.collectionUnread
import com.orbin.domain.repository.HistorySession
import com.orbin.domain.repository.InMemoryThreadMemoryRepository
import com.orbin.domain.repository.SessionThreadMemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CollectionPersistenceTest {
    private val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(42))

    @Test
    fun clearActivityErasesDurableAndSessionReadingMemoryAndStillClearsCaches() =
        runTest {
            HistorySession.forgetSession()
            val disk = InMemoryThreadMemoryRepository()
            disk.update(key) { it.copy(anchorPostId = 43, seenPostIds = setOf(42, 43), hiddenPostIds = setOf(44)) }
            val preferences = FakeSettings()
            preferences.setHistorySessionOnly(true)
            val memories = SessionThreadMemoryRepository(disk, preferences.settings)
            memories.update(key) { it.copy(anchorPostId = 45, seenPostIds = it.seenPostIds + 45) }
            val settings = ReaderSettings(preferences, FakeHistory(), backgroundScope, threadMemory = memories)
            var cachesCleared = false
            settings.clearActivity { cachesCleared = true }.join()
            assertTrue(disk.observeAll().first().isEmpty())
            assertTrue(memories.observeCurrent().first().isEmpty())
            assertTrue(cachesCleared)
        }

    @Test
    fun markingReadInAPrivateSessionAcknowledgesTheBadgeWithoutWritingPersistentCounters() =
        runTest {
            HistorySession.forgetSession()
            val bookmarks = FakeBookmarks()
            bookmarks.addBookmark(
                Bookmark(key, "Saved", createdAtMillis = 1, lastSeenReplyCount = 1, latestReplyCount = 6),
            )
            val disk = InMemoryThreadMemoryRepository()
            disk.update(key) { it.copy(knownPostIds = setOf(42, 43, 44)) }
            val settings = MutableStateFlow(AppSettings(historySessionOnly = true))
            val memories = SessionThreadMemoryRepository(disk, settings)
            val collections =
                LocalThreadCollections(bookmarks, this, { 1 }, {}, memory = memories, preferences = { settings.value })
            collections.markRead(key)
            advanceUntilIdle()
            assertEquals(1, bookmarks.getBookmark(key)?.lastSeenReplyCount)
            assertEquals(0, checkNotNull(bookmarks.getBookmark(key)).collectionUnread(memories.get(key)))
            assertEquals(0, disk.get(key).acknowledgedReplyCount)
            assertTrue(disk.get(key).seenPostIds.isEmpty())
            assertTrue(memories.observeAll().first().isEmpty())
            HistorySession.forgetSession()
            assertEquals(6, checkNotNull(bookmarks.getBookmark(key)).collectionUnread(memories.get(key)))
        }

    @Test
    fun cleanupRemovesDeletedOnlyAndPreservesArchivedThreadsAndOtherSourceKeys() =
        runTest {
            val bookmarks = FakeBookmarks()
            val archived = key.copy(thread = ThreadId(43))
            val otherSource = key.copy(provider = ProviderId("bbwchan"))
            bookmarks.addBookmark(
                Bookmark(key, "Deleted", createdAtMillis = 1, availability = ThreadAvailability.DELETED),
            )
            bookmarks.addBookmark(
                Bookmark(archived, "Archived", createdAtMillis = 1, availability = ThreadAvailability.ARCHIVED),
            )
            bookmarks.addBookmark(
                Bookmark(otherSource, "Same number, live", createdAtMillis = 1, availability = ThreadAvailability.LIVE),
            )
            val collections = LocalThreadCollections(bookmarks, this, { 1 }, {})
            collections.removeDeleted()
            advanceUntilIdle()
            assertNull(bookmarks.getBookmark(key))
            assertEquals(ThreadAvailability.ARCHIVED, bookmarks.getBookmark(archived)?.availability)
            assertEquals(ThreadAvailability.LIVE, bookmarks.getBookmark(otherSource)?.availability)
        }
}
