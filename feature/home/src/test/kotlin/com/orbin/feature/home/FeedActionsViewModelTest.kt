package com.orbin.feature.home

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeBookmarkRepository
import com.orbin.domain.repository.ThreadRepository
import com.orbin.uinext.FeedRow
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedActionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `saving cannot remove an actively watched reference`() =
        runTest {
            val repository = FakeBookmarkRepository()
            val model = FeedActionsViewModel(repository, mockk())
            val thread = entry("fourchan")
            model.toggleWatch(thread)
            runCurrent()
            model.toggleSaved(thread)
            runCurrent()
            assertThat(repository.getBookmark(thread.key)?.isWatched).isTrue()
        }

    @Test
    fun `watch actions distinguish matching board and thread numbers on different sites`() =
        runTest {
            val repository = FakeBookmarkRepository()
            val model = FeedActionsViewModel(repository, mockk())
            val fourchan = entry("fourchan")
            val bbwchan = entry("bbwchan")

            model.toggleWatch(fourchan)
            model.toggleWatch(bbwchan)
            runCurrent()

            assertThat(repository.getBookmark(fourchan.key)?.isWatched).isTrue()
            assertThat(repository.getBookmark(bbwchan.key)?.isWatched).isTrue()

            model.toggleWatch(fourchan)
            runCurrent()
            assertThat(repository.getBookmark(fourchan.key)?.isWatched).isFalse()
            assertThat(repository.getBookmark(bbwchan.key)?.isWatched).isTrue()
            assertThat(repository.getBookmark(fourchan.key)).isNotNull()
        }

    @Test
    fun `save captures catalog metadata and a second tap removes only that source`() =
        runTest {
            val repository = FakeBookmarkRepository()
            val model = FeedActionsViewModel(repository, mockk())
            val fourchan = entry("fourchan")
            val bbwchan = entry("bbwchan")

            model.toggleSaved(fourchan)
            model.toggleSaved(bbwchan)
            runCurrent()
            val saved = repository.getBookmark(fourchan.key)!!
            assertThat(saved.title).isEqualTo("Quiet landscapes")
            assertThat(saved.thumbnailUrl).isEqualTo("https://example.org/preview.jpg")
            assertThat(saved.lastSeenReplyCount).isEqualTo(24)
            assertThat(saved.latestReplyCount).isEqualTo(24)
            assertThat(saved.isWatched).isFalse()

            model.toggleSaved(fourchan)
            runCurrent()
            assertThat(repository.getBookmark(fourchan.key)).isNull()
            assertThat(repository.getBookmark(bbwchan.key)).isNotNull()
        }

    @Test
    fun `rapid watch toggles retain the saved reference`() =
        runTest {
            val repository = FakeBookmarkRepository()
            val model = FeedActionsViewModel(repository, mockk())
            val entry = entry("fourchan")

            model.toggleWatch(entry)
            model.toggleWatch(entry)
            runCurrent()

            assertThat(repository.getBookmark(entry.key)?.isWatched).isFalse()
            assertThat(repository.getBookmark(entry.key)).isNotNull()
        }

    @Test
    fun `share asks the repository for the canonical source-qualified URL`() {
        val threadRepository = mockk<ThreadRepository>()
        val entry = entry("bbwchan")
        every { threadRepository.threadWebUrl(entry.key) } returns "https://example.org/gen/res/42.html"
        val model = FeedActionsViewModel(FakeBookmarkRepository(), threadRepository)

        assertThat(model.shareUrl(entry.key)).isEqualTo("https://example.org/gen/res/42.html")
    }

    private fun entry(provider: String): FeedEntry {
        val key = ThreadKey(ProviderId(provider), BoardId("gen"), ThreadId(42L))
        return FeedEntry(
            key = key,
            title = "Quiet landscapes",
            attachment =
                MediaAttachment(
                    id = "photo",
                    originalFileName = "photo.jpg",
                    extension = "jpg",
                    type = MediaType.IMAGE,
                    sourceUrl = "https://example.org/photo.jpg",
                    thumbnailUrl = "https://example.org/preview.jpg",
                ),
            activityMillis = 1L,
            row =
                FeedRow(
                    id = "$provider/gen/42",
                    subject = "Quiet landscapes",
                    board = "/gen/",
                    activity = "1m",
                    replies = 24,
                    media = 1,
                ),
        )
    }
}
