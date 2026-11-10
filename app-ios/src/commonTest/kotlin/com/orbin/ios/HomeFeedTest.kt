package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.HomeFeedMode
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.api.ViolentMediaCoverProvider
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Real provider decorators over a controlled catalog transport, with no timing or network dependency. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedTest {
    @Test
    fun discoveryIncludesUnfollowedBoardsAndBothSourcesWhileFollowingNeedsNoWatchedThread() =
        runTest {
            val fixture = Fixture(backgroundScope, sources = listOf("one", "two"), count = 12)
            fixture.followed.value = setOf(fixture.key("one", 0))
            val home = fixture.start()
            runCurrent()
            assertEquals(8, fixture.requested.size)
            assertEquals(setOf("one", "two"), fixture.requested.map { it.provider.value }.toSet())
            assertTrue(fixture.requested.any { it !in fixture.followed.value })
            assertEquals(
                8,
                home
                    .rows()
                    .map { it.feedRowId }
                    .toSet()
                    .size,
            )

            home.selectMode(HomeFeedMode.FOLLOWING)
            runCurrent()
            assertEquals(
                listOf(fixture.key("one", 0)),
                home.rows().map { FollowedBoard(it.provider, it.thread.key.board) },
            )
            assertEquals(8, fixture.requested.size, "the followed catalog was already cached")
        }

    @Test
    fun initialDiscoveryIncludesOutsidersEvenWhenMoreThanAPageOfBoardsAreFollowed() =
        runTest {
            val fixture = Fixture(backgroundScope, count = 16)
            fixture.followed.value = (0..13).mapTo(mutableSetOf()) { fixture.key("one", it) }
            fixture.start()
            runCurrent()
            assertEquals(8, fixture.requested.size)
            assertTrue(fixture.requested.any { it !in fixture.followed.value })
        }

    @Test
    fun pagesCoalesceRepeatedCallbacksAndSurviveATabRoundTripWithoutRefetching() =
        runTest {
            val fixture = Fixture(backgroundScope, count = 24)
            fixture.followed.value = setOf(fixture.key("one", 0))
            val home = fixture.start()
            runCurrent()
            home.loadMore()
            home.loadMore()
            runCurrent()
            assertEquals(16, fixture.requested.size)
            assertEquals(16, home.completedDiscoveryCount.value)
            home.selectMode(HomeFeedMode.FOLLOWING)
            runCurrent()
            home.selectMode(HomeFeedMode.FOR_YOU)
            runCurrent()
            assertEquals(16, home.rows().size)
            assertEquals(16, fixture.requested.size)
            assertTrue(home.canLoadMore.value)
        }

    @Test
    fun changingSourcesResetsThePageWindowAndNeverMergesSameNamedBoards() =
        runTest {
            val fixture = Fixture(backgroundScope, sources = listOf("one", "two"), count = 18)
            val home = fixture.start()
            runCurrent()
            fixture.source.value = ProviderId("two")
            home.load()
            runCurrent()
            assertEquals(8, home.rows().size)
            assertTrue(home.rows().all { it.provider.value == "two" })
            home.loadMore()
            runCurrent()
            assertEquals(16, home.rows().size)
            fixture.source.value = ProviderId("one")
            home.load()
            runCurrent()
            assertEquals(8, home.rows().size)
            assertTrue(home.rows().all { it.provider.value == "one" })
            assertEquals(fixture.requested.distinct().size, fixture.requested.size)
        }

    @Test
    fun atMostFourCatalogRequestsAreInFlightAcrossBothSources() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val fixture = Fixture(backgroundScope, sources = listOf("one", "two"))
            fixture.catalog = { key ->
                release.await()
                listOf(video(key))
            }
            val home = fixture.start()
            runCurrent()
            assertEquals(4, fixture.inFlight)
            assertEquals(4, fixture.maxInFlight)
            release.complete(Unit)
            runCurrent()
            assertEquals(8, home.rows().size)
            assertFalse(home.isLoadingMore.value)
        }

    @Test
    fun firstVideosPublishWhileAnotherCatalogIsStillWaiting() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val fixture = Fixture(backgroundScope)
            fixture.catalog = { key ->
                if (key == fixture.key("one", 0)) release.await()
                listOf(video(key))
            }
            val home = fixture.start()
            runCurrent()
            assertEquals(7, home.rows().size)
            assertTrue(home.isLoadingMore.value)
            release.complete(Unit)
            runCurrent()
            assertEquals(8, home.rows().size)
            assertFalse(home.isLoadingMore.value)
        }

    @Test
    fun anOutageIsRetryableAndDoesNotAutomaticallySweepEveryBoard() =
        runTest {
            val fixture = Fixture(backgroundScope, count = 24)
            fixture.catalog = { error("offline") }
            val home = fixture.start()
            runCurrent()
            assertIs<Load.Failed>(home.content.value)
            assertEquals(8, fixture.requested.size)
            runCurrent()
            assertEquals(8, fixture.requested.size)
            fixture.catalog = { listOf(video(it)) }
            home.load(force = true)
            runCurrent()
            assertEquals(8, home.rows().size)
            assertEquals(16, fixture.requested.size)
        }

    @Test
    fun hiddenAndPermanentlyFilteredBoardsNeverFetchAndUnknownFollowIdsAreNotSynthesized() =
        runTest {
            val fixture = Fixture(backgroundScope, count = 2)
            val safe =
                fixture.directory.value.boards
                    .first()
            val hidden =
                fixture.directory.value.boards
                    .last()
                    .let { it.copy(board = it.board.copy(isNsfw = true)) }
            val blocked = SiteBoard(ProviderId("one"), "one", Board(BoardId("blocked"), "Lolicon"))
            fixture.directory.value =
                HomeFeedDirectory(listOf(safe, hidden, blocked), completed = setOf(ProviderId("one")))
            fixture.settings.value = fixture.settings.value.copy(hideNsfwBoards = true)
            fixture.followed.value =
                setOf(safe.key(), hidden.key(), blocked.key(), FollowedBoard(ProviderId("one"), BoardId("missing")))
            val home = fixture.start()
            runCurrent()
            assertEquals(listOf(safe.key()), fixture.requested)
            home.selectMode(HomeFeedMode.FOLLOWING)
            runCurrent()
            assertEquals(listOf(safe.board.id), home.rows().map { it.thread.key.board })
            assertEquals(1, fixture.requested.size)
        }

    @Test
    fun anUnavailableSourceShowsAnErrorWhileFollowingWithNoBoardsIsImmediatelyEmpty() =
        runTest {
            val fixture = Fixture(backgroundScope)
            fixture.directory.value = HomeFeedDirectory(failures = mapOf(ProviderId("one") to "Directory unavailable"))
            val home = fixture.start()
            runCurrent()
            assertEquals(Load.Failed("Directory unavailable"), home.content.value)
            home.selectMode(HomeFeedMode.FOLLOWING)
            runCurrent()
            assertEquals(Load.Ready(emptyList()), home.content.value)
            fixture.followed.value = setOf(fixture.key("one", 0))
            runCurrent()
            assertIs<Load.Failed>(home.content.value)
            assertTrue(fixture.requested.isEmpty())
        }

    @Test
    fun aWorkingDirectoryPublishesVideosBeforeTheOtherSiteFinishes() =
        runTest {
            val fixture = Fixture(backgroundScope, sources = listOf("one", "two"))
            val available =
                fixture.directory.value.boards
                    .filter { it.provider.value == "one" }
            fixture.directory.value =
                HomeFeedDirectory(available, completed = setOf(ProviderId("one")), loading = setOf(ProviderId("two")))
            val home = fixture.start()
            runCurrent()
            assertTrue(home.rows().isNotEmpty())
            assertTrue(home.rows().all { it.provider.value == "one" })
            fixture.source.value = ProviderId("two")
            home.load()
            runCurrent()
            assertIs<Load.Loading>(home.content.value)
            fixture.directory.value =
                fixture.directory.value.copy(
                    loading = emptySet(),
                    failures =
                        mapOf(
                            ProviderId("two") to "Second source offline",
                        ),
                )
            runCurrent()
            assertEquals(Load.Failed("Second source offline"), home.content.value)
        }

    @Test
    fun privacyChangesCannotFallBackToAnUncoveredCatalogAfterFailure() =
        runTest {
            val fixture = Fixture(backgroundScope, count = 1)
            fixture.settings.value = fixture.settings.value.copy(coverViolentMedia = false)
            val home = fixture.start()
            runCurrent()
            assertFalse(
                home
                    .rows()
                    .single()
                    .thread.originalPost.attachments
                    .single()
                    .isSpoiler,
            )
            fixture.catalog = { error("offline") }
            val response = CompletableDeferred<Unit>()
            fixture.catalog = {
                response.await()
                error("offline")
            }
            fixture.settings.value = fixture.settings.value.copy(coverViolentMedia = true)
            runCurrent()
            assertIs<Load.Loading>(home.content.value)
            response.complete(Unit)
            runCurrent()
            assertIs<Load.Failed>(home.content.value)
            fixture.catalog = { listOf(video(it)) }
            home.load(force = true)
            runCurrent()
            assertTrue(
                home
                    .rows()
                    .single()
                    .thread.originalPost.attachments
                    .single()
                    .isSpoiler,
            )
            fixture.settings.value = fixture.settings.value.copy(coverViolentMedia = false)
            runCurrent()
            assertFalse(
                home
                    .rows()
                    .single()
                    .thread.originalPost.attachments
                    .single()
                    .isSpoiler,
            )
        }

    @Test
    fun cancelledAndNewSourcesShareTheSameFourRequestBudget() =
        runTest {
            val releaseOld = CompletableDeferred<Unit>()
            val releaseCooperative = CompletableDeferred<Unit>()
            val releaseNew = CompletableDeferred<Unit>()
            val fixture = Fixture(backgroundScope, sources = listOf("one", "two"))
            fixture.source.value = ProviderId("one")
            fixture.catalog = { key ->
                when {
                    key == fixture.key("one", 0) -> withContext(NonCancellable) { releaseOld.await() }
                    key.provider.value == "one" -> releaseCooperative.await()
                    else -> releaseNew.await()
                }
                listOf(video(key))
            }
            val home = fixture.start()
            try {
                runCurrent()
                assertEquals(4, fixture.inFlight)
                fixture.source.value = ProviderId("two")
                home.load()
                runCurrent()
                assertEquals(3, fixture.requested.count { it.provider.value == "two" })
                assertEquals(4, fixture.maxInFlight, "the cancelled non-cooperative source still owns one permit")
                releaseOld.complete(Unit)
                runCurrent()
                assertEquals(4, fixture.requested.count { it.provider.value == "two" })
                releaseNew.complete(Unit)
                runCurrent()
                assertEquals(8, home.rows().size)
                assertTrue(home.rows().all { it.provider.value == "two" })
                assertEquals(4, fixture.maxInFlight)
            } finally {
                releaseOld.complete(Unit)
                releaseCooperative.complete(Unit)
                releaseNew.complete(Unit)
            }
        }

    @Test
    fun threadLimitChangesOnlyReloadTheAffectedCatalog() =
        runTest {
            val fixture = Fixture(backgroundScope, count = 1)
            fixture.catalog = { key -> (1L..8L).map { video(key, it) } }
            val home = fixture.start()
            runCurrent()
            assertEquals(8, home.rows().size)
            fixture.preferences.setFeedThreadLimit(ProviderId("one"), BoardId("b00"), FeedThreadLimit.SIX)
            runCurrent()
            assertEquals(6, home.rows().size)
            assertEquals(2, fixture.requested.size)
        }

    private fun HomeFeed.rows(): List<FeedThread> = assertIs<Load.Ready<List<FeedThread>>>(content.value).value

    @Test
    fun discoveryWaitsForPersistedPrivacyBeforeRequestingAnyCatalog() =
        runTest {
            val raw = MutableSharedFlow<AppSettings>()
            val fixture = Fixture(backgroundScope, count = 2)
            val safe =
                fixture.directory.value.boards
                    .first()
            val adult =
                fixture.directory.value.boards
                    .last()
                    .let { it.copy(board = it.board.copy(isNsfw = true)) }
            fixture.directory.value = fixture.directory.value.copy(boards = listOf(safe, adult))
            val home = fixture.start(raw)
            runCurrent()
            assertTrue(fixture.requested.isEmpty())
            assertIs<Load.Loading>(home.content.value)
            raw.emit(AppSettings.Default.copy(hideNsfwBoards = true))
            runCurrent()
            assertEquals(listOf(safe.key()), fixture.requested)
            assertEquals(listOf(safe.board.id), home.rows().map { it.thread.key.board })
            assertEquals(HomeFeedContentPolicy(true, true), home.contentPolicy.value)
            raw.emit(AppSettings.Default.copy(hideNsfwBoards = true, saveSearchHistory = true))
            runCurrent()
            assertEquals(1, fixture.requested.size, "unrelated settings cannot refetch every catalog")
        }

    @Test
    fun healthyImageOnlyCatalogsCannotHideAnotherSourcesOutage() =
        runTest {
            val fixture = Fixture(backgroundScope, sources = listOf("one", "two"), count = 12)
            fixture.catalog = { key ->
                if (key.provider.value == "one") error("offline")
                val loaded = video(key)
                val image =
                    loaded.originalPost.attachments
                        .single()
                        .copy(type = MediaType.IMAGE)
                listOf(loaded.copy(originalPost = loaded.originalPost.copy(attachments = persistentListOf(image))))
            }
            val home = fixture.start()
            runCurrent()
            assertIs<Load.Failed>(home.content.value)
            assertEquals(8, fixture.requested.size)
        }

    private class Fixture(
        private val scope: CoroutineScope,
        sources: List<String> = listOf("one"),
        count: Int = 8,
    ) {
        val settings = MutableStateFlow(AppSettings.Default)
        val preferences = FakeBoardPreferences()
        val followed = MutableStateFlow<Set<FollowedBoard>>(emptySet())
        val source = MutableStateFlow<ProviderId?>(null)
        val firstRun = MutableStateFlow(FirstRunState(initialized = true, needed = false))
        val directory =
            MutableStateFlow(
                HomeFeedDirectory(
                    boards =
                        sources.flatMap { id ->
                            (0 until count).map {
                                SiteBoard(
                                    ProviderId(id),
                                    id,
                                    Board(BoardId("b${it.toString().padStart(2, '0')}"), "Board $it"),
                                )
                            }
                        },
                    completed = sources.mapTo(mutableSetOf(), ::ProviderId),
                ),
            )
        val requested = mutableListOf<FollowedBoard>()
        var catalog: suspend (FollowedBoard) -> List<CatalogThread> = { listOf(video(it)) }
        var inFlight = 0
        var maxInFlight = 0
        private val providers =
            sources.map { id ->
                val base =
                    object : ImageBoardProvider {
                        override val metadata = ProviderMetadata(ProviderId(id), id, "https://example.org")
                        override val capabilities = ProviderCapabilities()

                        override suspend fun getBoards(): List<Board> = emptyList()

                        override suspend fun getThread(
                            board: BoardId,
                            thread: ThreadId,
                        ): Thread = error("Unused")

                        override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> {
                            val key = FollowedBoard(metadata.id, request.board)
                            requested += key
                            inFlight++
                            maxInFlight = maxOf(maxInFlight, inFlight)
                            return try {
                                catalog(key)
                            } finally {
                                inFlight--
                            }
                        }
                    }
                ViolentMediaCoverProvider(base) { settings.value.coverViolentMedia }
            }

        fun key(
            source: String,
            board: Int,
        ) = FollowedBoard(ProviderId(source), BoardId("b${board.toString().padStart(2, '0')}"))

        fun start(rawSettings: Flow<AppSettings> = settings) =
            HomeFeed(providers, preferences, rawSettings, followed, directory, source, firstRun, scope) {
                true
            }
    }

    companion object {
        private fun SiteBoard.key() = FollowedBoard(provider, board.id)

        private fun video(
            board: FollowedBoard,
            id: Long = 7,
        ) = CatalogThread(
            key = ThreadKey(board.provider, board.board, ThreadId(id)),
            originalPost =
                Post(
                    PostId(id),
                    board.board,
                    ThreadId(id),
                    true,
                    subject = "NSFL clip",
                    comment = PostComment.Empty,
                    attachments =
                        persistentListOf(
                            MediaAttachment(
                                "clip$id",
                                "clip.mp4",
                                "mp4",
                                MediaType.VIDEO,
                                "https://example.org/${board.provider.value}/${board.board.value}/$id.mp4",
                                "https://example.org/thumb.jpg",
                            ),
                        ),
                ),
            stats = ThreadStats(),
        )
    }
}
