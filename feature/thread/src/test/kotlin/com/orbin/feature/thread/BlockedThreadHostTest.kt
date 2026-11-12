package com.orbin.feature.thread

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.BoardId
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.testing.repository.FakeBookmarkRepository
import com.orbin.core.testing.repository.FakeDownloadRepository
import com.orbin.core.testing.repository.FakeHistoryRepository
import com.orbin.core.testing.repository.FakeSavedThreadRepository
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.repository.ThreadRepository
import com.orbin.domain.usecase.ObserveThreadUseCase
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ReaderAppBarTags
import com.orbin.uinext.tokens.ReaderLightColors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

/** Exercises the actual Android route host after policy blocks its opening post. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class BlockedThreadHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var viewModel: ThreadViewModel? = null

    @After
    fun clearViewModel() {
        viewModel?.viewModelScope?.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aBlockedThreadKeepsItsGreenAppBarAndWorkingBackAction() {
        val presentationScheduler = TestCoroutineScheduler()
        val presentationQueued = AtomicBoolean()
        val presentationDispatcher =
            object : CoroutineDispatcher() {
                private val worker = StandardTestDispatcher(presentationScheduler)

                override fun dispatch(
                    context: CoroutineContext,
                    block: Runnable,
                ) {
                    presentationQueued.set(true)
                    worker.dispatch(context, block)
                }
            }
        val key = ThreadKey(ProviderId("site"), BoardId("g"), ThreadId(1))
        val blocked = Thread(key, Post(PostId(1), key.board, key.thread, isOriginalPost = true, subject = "gore"))
        val reader =
            ThreadViewModel(
                savedStateHandle =
                    SavedStateHandle(
                        mapOf(
                            "provider" to "site",
                            "board" to "g",
                            "thread" to 1L,
                            "title" to "Filtered thread",
                        ),
                    ),
                observeThread = ObserveThreadUseCase(BlockedThreadRepository(blocked)),
                bookmarkRepository = FakeBookmarkRepository(),
                downloadRepository = FakeDownloadRepository(),
                historyRepository = FakeHistoryRepository(),
                settingsRepository = FakeSettingsRepository(),
                savedThreadRepository = FakeSavedThreadRepository(),
                presentationDispatcher = presentationDispatcher,
            )
        viewModel = reader
        var backs = 0
        composeRule.setContent {
            NextTheme(darkTheme = false) {
                NextThreadScreen(onOpenMedia = {}, viewModel = reader, onBack = { backs++ })
            }
        }
        // Compose starts the real host's subscription; explicitly drain its separate worker before
        // waiting for the resulting Main publication and frame. Wall-clock polling cannot synchronize it.
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(true, presentationQueued.get()) }
        presentationScheduler.runCurrent()
        composeRule.runOnIdle { assertEquals(ThreadUiState.Blocked, reader.uiState.value) }
        composeRule.onNodeWithTag(ReaderAppBarTags.TITLE).assertTextEquals("Filtered thread")
        composeRule.onNodeWithText("This thread is caught by the built-in content filter.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertDoesNotExist()
        val pixels = composeRule.onNodeWithTag(ReaderAppBarTags.BAR).captureToImage().toPixelMap()
        assertEquals(ReaderLightColors.appBar, pixels[pixels.width / 2, pixels.height - 2])
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }
}

private class BlockedThreadRepository(
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
    ): OrbinResult<Thread> = OrbinResult.Success(this.thread)
}
