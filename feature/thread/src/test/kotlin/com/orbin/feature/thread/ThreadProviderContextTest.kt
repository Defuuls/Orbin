package com.orbin.feature.thread

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppSettings
import com.orbin.core.model.BoardId
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeBookmarkRepository
import com.orbin.core.testing.repository.FakeDownloadRepository
import com.orbin.core.testing.repository.FakeHistoryRepository
import com.orbin.core.testing.repository.FakeSavedThreadRepository
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.repository.ThreadRepository
import com.orbin.domain.usecase.ObserveThreadUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadProviderContextTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `opening a bbw chan thread keeps bbw chan as the active browsing provider`() =
        runTest {
            val bbwProvider = ProviderId("bbwchan")
            val board = BoardId("bbw")
            val threadId = ThreadId(42L)
            val key = ThreadKey(bbwProvider, board, threadId)
            val settings =
                FakeSettingsRepository(
                    AppSettings.Default.copy(activeProviderId = "fourchan"),
                )

            ThreadViewModel(
                savedStateHandle =
                    SavedStateHandle(
                        mapOf(
                            "provider" to bbwProvider.value,
                            "board" to board.value,
                            "thread" to threadId.value,
                            "title" to "BBW Chan thread",
                        ),
                    ),
                observeThread = ObserveThreadUseCase(SingleThreadRepository(testThread(key))),
                bookmarkRepository = FakeBookmarkRepository(),
                downloadRepository = FakeDownloadRepository(),
                historyRepository = FakeHistoryRepository(),
                settingsRepository = settings,
                savedThreadRepository = FakeSavedThreadRepository(),
            )

            runCurrent()

            assertThat(settings.settings.first().activeProviderId).isEqualTo("bbwchan")
        }

    private fun testThread(key: ThreadKey) =
        Thread(
            key = key,
            originalPost =
                Post(
                    id = PostId(key.thread.value),
                    board = key.board,
                    threadId = key.thread,
                    isOriginalPost = true,
                ),
            stats = ThreadStats(),
        )
}

private class SingleThreadRepository(
    private val thread: Thread,
) : ThreadRepository {
    override fun observeThread(
        key: ThreadKey,
        forceRefresh: Boolean,
    ): Flow<OrbinResult<Thread>> = flowOf(OrbinResult.Success(thread))

    override suspend fun refreshThread(
        provider: ProviderId,
        board: BoardId,
        thread: ThreadId,
        forceRefresh: Boolean,
    ): OrbinResult<Thread> = OrbinResult.Success(thread)
}
