package com.orbin.feature.bookmarks

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeBookmarkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class BookmarksViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(1))
    private val bookmark = Bookmark(key = key, title = "Thread", createdAtMillis = 0)

    @Test
    fun `exposes bookmarks from the repository`() =
        runTest {
            val viewModel = BookmarksViewModel(FakeBookmarkRepository(listOf(bookmark)))

            viewModel.bookmarks.test {
                assertThat(awaitItem().map { it.title }).containsExactly("Thread")
            }
        }

    @Test
    fun `remove deletes the bookmark`() =
        runTest {
            val viewModel = BookmarksViewModel(FakeBookmarkRepository(listOf(bookmark)))

            viewModel.bookmarks.test {
                assertThat(awaitItem()).hasSize(1)
                viewModel.remove(key)
                assertThat(awaitItem()).isEmpty()
            }
        }

    @Test
    fun `toggleWatched updates the watched flag`() =
        runTest {
            val viewModel = BookmarksViewModel(FakeBookmarkRepository(listOf(bookmark)))

            viewModel.bookmarks.test {
                assertThat(awaitItem().single().isWatched).isFalse()
                viewModel.toggleWatched(key, watched = true)
                assertThat(awaitItem().single().isWatched).isTrue()
            }
        }
}
