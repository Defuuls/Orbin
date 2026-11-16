package com.orbin.ios

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderMetadata
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BundledBoardDirectoryTest {
    /** A live site that fails the test if anything reaches it. */
    private class LiveSite(
        id: String,
    ) : ImageBoardProvider {
        override val metadata = ProviderMetadata(ProviderId(id), "Live $id", "https://$id.example")
        override val capabilities = ProviderCapabilities()

        override suspend fun getBoards(): List<Board> = error("The live directory was requested")

        override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> =
            error("A live catalog was requested")

        override suspend fun getThread(
            board: BoardId,
            thread: ThreadId,
        ): Thread = error("A live thread was requested")
    }

    private val fourchan = LiveSite("fourchan")
    private val bbwchan = LiveSite("bbwchan")

    @Test
    fun theFlagIsReadFromTheLaunchArgumentOrTheEnvironment() {
        val flags = UiTestLaunchFlags
        assertTrue(flags.bundledDirectoryRequested(listOf("Orbin", "-OrbinUITestBundledDirectory", "YES"), emptyMap()))
        assertTrue(flags.bundledDirectoryRequested(listOf("Orbin"), mapOf("ORBIN_UI_TEST_BUNDLED_DIRECTORY" to "1")))
        assertFalse(flags.bundledDirectoryRequested(listOf("Orbin"), emptyMap()))
        assertFalse(
            flags.bundledDirectoryRequested(
                listOf("Orbin", "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryL"),
                mapOf("ORBIN_UI_TEST_BUNDLED_DIRECTORY" to "0"),
            ),
        )
    }

    @Test
    fun withoutTheFlagTheLiveSitesAreUsedUnchanged() {
        val live = listOf(fourchan, bbwchan)
        val sources = boardSources(live, bundledDirectory = false)
        assertSame(live, sources)
    }

    @Test
    fun theFlagAnswersTheDirectoryFromTheFixtureWithoutTheNetwork() =
        runTest {
            val sources = boardSources(listOf(fourchan, bbwchan), bundledDirectory = true)

            // Only sites the fixture covers remain, under their live identity.
            assertEquals(listOf(fourchan.metadata), sources.map { it.metadata })
            val boards = sources.single().siteBoards()
            // /3/ first, as in the real directory the live smoke test relies on; all SFW.
            assertEquals(listOf("3", "ck", "diy", "g"), boards.map { it.board.id.value })
            assertEquals("3DCG", boards.first().board.title)
            assertTrue(boards.none { it.board.isNsfw })
            assertTrue(boards.all { it.provider == ProviderId("fourchan") })
        }

    @Test
    fun catalogsAndThreadsAreTextOnlyFixtures() =
        runTest {
            val source = boardSources(listOf(fourchan), bundledDirectory = true).single()

            val catalog = source.getCatalog(CatalogRequest(ProviderId("fourchan"), BoardId("3")))
            assertEquals(
                listOf("Bundled fixture: low-poly lighthouse", "Retopology workflow thread"),
                catalog.map { it.originalPost.subject },
            )
            assertTrue(catalog.all { !it.originalPost.hasMedia && it.previewReplies.none { reply -> reply.hasMedia } })
            assertEquals(2, catalog.first().stats.replyCount)

            val thread = source.getThread(BoardId("3"), ThreadId(900101))
            assertEquals(catalog.first().key, thread.key)
            assertEquals(2, thread.replies.size)
            assertTrue(thread.allPosts.none { it.hasMedia })
            assertFailsWith<ProviderException.NotFound> { source.getThread(BoardId("3"), ThreadId(1)) }
        }

    @Test
    fun everyFixtureBoardHasACatalog() =
        runTest {
            val source = boardSources(listOf(fourchan), bundledDirectory = true).single()
            source.getBoards().forEach { board ->
                val catalog = source.getCatalog(CatalogRequest(ProviderId("fourchan"), board.id))
                assertTrue(catalog.isNotEmpty(), "/${board.id.value}/ has no bundled threads")
            }
        }
}
