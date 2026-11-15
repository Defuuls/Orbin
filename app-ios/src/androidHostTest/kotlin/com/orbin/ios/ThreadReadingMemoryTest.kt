package com.orbin.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.orbin.core.model.AppSettings
import com.orbin.core.model.BoardId
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.core.reader.ThreadPresentationIndex
import com.orbin.core.reader.preparePresentation
import com.orbin.domain.repository.InMemoryThreadMemoryRepository
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ReaderAppBarTags
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ThreadReadingMemoryTest {
    @get:Rule val compose = createComposeRule()
    private val key = ThreadKey(ProviderId("site"), BoardId("g"), ThreadId(7))

    @Test
    fun readingAnOfflineCopyRetainsNewerMemoryAndNeverAutomaticallyAcknowledgesItsLiveCounter() {
        val memory = InMemoryThreadMemoryRepository()
        runBlocking {
            memory.update(key) { it.copy(knownPostIds = setOf(7, 8, 9), seenPostIds = setOf(7, 9)) }
        }
        var acknowledgements = 0
        show(copy = true, memory = memory, onAllRead = { acknowledgements++ })
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Reply").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitUntil(5_000) { runBlocking { 8L in memory.get(key).seenPostIds } }
        val current = runBlocking { memory.get(key) }
        assertEquals(setOf(7L, 8L, 9L), current.knownPostIds)
        assertEquals(setOf(7L, 8L, 9L), current.seenPostIds)
        assertEquals(0, acknowledgements)
    }

    @Test
    fun explicitMarkAllReadReachesTheSessionAwareHostEvenInPrivateHistoryMode() {
        var acknowledgements = 0
        show(
            copy = true,
            memory = InMemoryThreadMemoryRepository(),
            privateHistory = true,
            onAllRead = { acknowledgements++ },
        )
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Reply").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(ReaderAppBarTags.OVERFLOW).performClick()
        compose.onNodeWithText("Mark all read").performClick()
        compose.runOnIdle { assertEquals(1, acknowledgements) }
    }

    private fun show(
        copy: Boolean,
        memory: InMemoryThreadMemoryRepository,
        privateHistory: Boolean = false,
        onAllRead: () -> Unit,
    ) {
        val presentation: ThreadPresentationIndex =
            runBlocking {
                Thread(
                    key = key,
                    originalPost = Post(PostId(7), key.board, key.thread, true),
                    replies =
                        persistentListOf(
                            Post(
                                PostId(8),
                                key.board,
                                key.thread,
                                false,
                                comment = PostComment("", persistentListOf(PostNode.Text("Reply"))),
                            ),
                        ),
                    stats = ThreadStats(replyCount = 1),
                    isOfflineCopy = copy,
                ).preparePresentation()
            }
        compose.setContent {
            NextTheme {
                Box(Modifier.size(400.dp, 800.dp)) {
                    ThreadContent(
                        presentation = presentation,
                        refreshing = false,
                        refreshFailure = null,
                        watching = false,
                        onWatch = {},
                        onOpenFile = {},
                        memoryRepository = memory,
                        preferences = AppSettings.Default.copy(historySessionOnly = privateHistory),
                        onAllRead = onAllRead,
                    )
                }
            }
        }
    }
}
