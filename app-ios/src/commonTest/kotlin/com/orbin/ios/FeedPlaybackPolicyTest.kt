package com.orbin.ios

import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.PlaybackPreferences
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.PlaybackSettings
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FeedPlaybackPolicyTest {
    @Test
    fun adjacentCrossSitePreloadingRequiresBothBoardsConsent() {
        val current = feed("fourchan")
        val next = feed("bbwchan")
        val preferences = PlaybackPreferences()
        val target = feedPreloadTarget(current, next, preferences, true)
        assertEquals("bbwchan/g/7/same-file", target?.id)

        for (provider in listOf("fourchan", "bbwchan")) {
            val disabled =
                preferences.withSettings(PlaybackScope(provider, "g"), PlaybackSettings(preloadNextClip = false))
            assertNull(feedPreloadTarget(current, next, disabled, true))
        }
        val unrelated =
            preferences.withSettings(PlaybackScope("fourchan", "other"), PlaybackSettings(preloadNextClip = false))
        assertEquals(target, feedPreloadTarget(current, next, unrelated, true))
        assertNull(feedPreloadTarget(null, next, preferences, true))
        assertNull(feedPreloadTarget(current, null, preferences, true))
    }

    @Test
    fun spoilersUnsafeMediaAndUnsupportedWebMAreNeverPrepared() {
        val current = feed("fourchan")
        val preferences = PlaybackPreferences()
        assertNull(feedPreloadTarget(current, feed("bbwchan", file().copy(isSpoiler = true)), preferences, true))
        assertNull(
            feedPreloadTarget(
                current,
                feed("bbwchan", file().copy(sourceUrl = "http://example.org/clip.mp4")),
                preferences,
                true,
            ),
        )
        assertNull(feedPreloadTarget(current, feed("bbwchan", file().copy(type = MediaType.IMAGE)), preferences, true))
        val webm = feed("bbwchan", file().copy(extension = "webm"))
        assertNull(feedPreloadTarget(current, webm, preferences, false))
        assertEquals("webm", feedPreloadTarget(current, webm, preferences, true)?.extension)
    }

    @Test
    fun originalGifRequestsRequireAutoplayAndTheActivePage() {
        val gif = file().copy(type = MediaType.ANIMATED_IMAGE, extension = "gif")
        val still = file().copy(type = MediaType.IMAGE, extension = "jpg")
        assertNull(gif.feedOriginalImageUrl(active = true, autoplay = false))
        assertNull(gif.feedOriginalImageUrl(active = false, autoplay = true))
        assertEquals(gif.sourceUrl, gif.feedOriginalImageUrl(active = true, autoplay = true))
        assertEquals(still.sourceUrl, still.feedOriginalImageUrl(active = true, autoplay = false))
        assertNull(still.feedOriginalImageUrl(active = false, autoplay = true))
        assertNull(file().feedOriginalImageUrl(active = true, autoplay = true))
    }

    @Test
    fun repeatedDoubleTapsOnlyEnableWatchingAndKeepTheOtherSourcesRecord() =
        runTest {
            val bookmarks =
                object : FakeBookmarks() {
                    override suspend fun getBookmark(key: ThreadKey): Bookmark? {
                        val found = super.getBookmark(key)
                        yield() // Exposes duplicate read/write races without the collection's mutex.
                        return found
                    }
                }
            var watchStarts = 0
            val collections = LocalThreadCollections(bookmarks, this, now = { 100L }, onWatch = { watchStarts++ })
            val current = feed("fourchan")
            val other = feed("bbwchan")
            val otherRecord = Bookmark(other.thread.key, "Other source", createdAtMillis = 9L, isWatched = false)
            bookmarks.addBookmark(otherRecord)
            repeat(3) { collections.watchFeed(current) }
            advanceUntilIdle()
            assertTrue(bookmarks.getBookmark(current.thread.key)?.isWatched == true)
            assertEquals(1, watchStarts)
            assertEquals(otherRecord, bookmarks.getBookmark(other.thread.key))

            repeat(3) { collections.watchFeed(current) }
            advanceUntilIdle()
            assertEquals(1, watchStarts)
            collections.toggleFeedWatch(current)
            advanceUntilIdle()
            assertFalse(requireNotNull(bookmarks.getBookmark(current.thread.key)).isWatched)
            collections.watchFeed(current)
            advanceUntilIdle()
            assertTrue(bookmarks.getBookmark(current.thread.key)?.isWatched == true)
            assertEquals(2, watchStarts)
            assertEquals(100L, bookmarks.getBookmark(current.thread.key)?.createdAtMillis)
        }

    private fun feed(
        provider: String,
        attachment: MediaAttachment = file(),
    ): FeedThread {
        val key = ThreadKey(ProviderId(provider), BoardId("g"), ThreadId(7L))
        return FeedThread(
            key.provider,
            CatalogThread(
                key,
                Post(PostId(7L), key.board, key.thread, true, attachments = persistentListOf(attachment)),
                ThreadStats(),
            ),
        )
    }

    private fun file() =
        MediaAttachment(
            id = "same-file",
            originalFileName = "clip.mp4",
            extension = "mp4",
            type = MediaType.VIDEO,
            sourceUrl = "https://example.org/clip.mp4",
            thumbnailUrl = "https://example.org/clip.jpg",
        )
}
