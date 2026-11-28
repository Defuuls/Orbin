package com.orbin.app.benchmark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.orbin.app.R
import com.orbin.core.model.BoardId
import com.orbin.core.model.InlineStyle
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import com.orbin.core.model.PosterInfo
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.core.model.isWideWindow
import com.orbin.core.model.readerPaneWidths
import com.orbin.core.model.TWO_PANE_MIN_WIDTH_DP
import com.orbin.core.model.WIDE_SCREEN_MIN_DP
import com.orbin.core.model.showsTwoPanes
import com.orbin.core.reader.ReaderPostComment
import com.orbin.core.reader.ThreadPresentationIndex
import com.orbin.core.reader.preparePresentation
import com.orbin.media.video.VideoPlayer
import com.orbin.uinext.components.FeedRow
import com.orbin.uinext.components.NextTheme
import com.orbin.uinext.components.ReaderTwoPane
import com.orbin.uinext.components.ThreadInlineMediaFrame
import com.orbin.uinext.components.ThreadInlineMediaState
import com.orbin.uinext.components.rememberThreadInlineMediaState
import com.orbin.uinext.screens.BoardScreen
import com.orbin.uinext.screens.BoardScreenActions
import com.orbin.uinext.screens.BoardScreenState
import com.orbin.uinext.screens.CatalogMode
import com.orbin.uinext.screens.FeedScreen
import com.orbin.uinext.screens.FeedScreenActions
import com.orbin.uinext.screens.FeedScreenState
import com.orbin.uinext.screens.ThreadLayout
import com.orbin.uinext.screens.ThreadScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.orbin.core.model.Post as ModelPost

/**
 * A populated renderer target, compiled exclusively into benchmarkRelease/nonMinifiedRelease.
 * MainActivity and the real first-run path remain the startup benchmark target.
 */
class ReaderBenchmarkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val journey = intent.getStringExtra(JOURNEY_EXTRA) ?: CATALOG_LIST
        require(journey in JOURNEYS) { "Unknown reader benchmark journey: $journey" }
        val counters = ReaderFixtureCounters()
        lifecycleScope.launch {
            val data =
                withContext(Dispatchers.Default) {
                    counters.modelBuilds += 1
                    readerFixtureData(media = journey == THREAD)
                }
            setContent { NextTheme(darkTheme = false) { ReaderBenchmarkFixture(journey, data, counters) } }
        }
    }

    private companion object {
        const val JOURNEY_EXTRA = "readerJourney"
        const val CATALOG_LIST = "catalog-list"
        const val THREAD = "thread"
        val JOURNEYS = setOf(CATALOG_LIST, "catalog-grid", "catalog-card", "home-feed", THREAD, "files", "two-pane")
    }
}

private class ReaderFixtureCounters {
    var modelBuilds = 0

    // Read only by the semantics lambda below. SideEffect invalidates semantics, not composition.
    var catalogMounts by mutableIntStateOf(0)
    var catalogCompositions by mutableIntStateOf(0)
}

private class ReaderFixtureGeometry {
    var widthPx by mutableIntStateOf(0)
    var heightPx by mutableIntStateOf(0)
    var catalogWidthPx by mutableIntStateOf(0)
    var detailWidthPx by mutableIntStateOf(0)
}

private data class ReaderFixtureData(
    val rows: List<FeedRow>,
    val thread: ThreadPresentationIndex,
)

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun ReaderBenchmarkFixture(
    journey: String,
    data: ReaderFixtureData,
    counters: ReaderFixtureCounters,
) {
    val geometry = remember { ReaderFixtureGeometry() }
    val density = LocalDensity.current
    val catalogState = rememberLazyGridState()
    val threadState = rememberLazyListState()
    val inlineMedia = rememberThreadInlineMediaState("benchmark/bench/1")
    var selected by remember { mutableStateOf<FeedRow?>(null) }
    BackHandler(enabled = journey == "two-pane" && selected != null) { selected = null }
    val painter = painterResource(R.drawable.reader_benchmark_thumbnail)
    val thumbnail: @Composable (Modifier) -> Unit = { modifier ->
        Image(painter, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    }
    BoxWithConstraints(
        Modifier
            .testTag(FIXTURE_STATUS_TAG)
            .fillMaxSize()
            .onSizeChanged {
                geometry.widthPx = it.width
                geometry.heightPx = it.height
            }.semantics {
                testTagsAsResourceId = true
                val ready =
                    geometry.widthPx > 0 &&
                        geometry.heightPx > 0 &&
                        (journey == "thread" || journey == "files" || geometry.catalogWidthPx > 0) &&
                        (journey != "thread" && journey != "files" || geometry.detailWidthPx > 0) &&
                        (journey != "two-pane" || geometry.detailWidthPx > 0)
                contentDescription =
                    "journey=$journey;models=${counters.modelBuilds};mounts=${counters.catalogMounts};" +
                    "compositions=${counters.catalogCompositions};index=${catalogState.firstVisibleItemIndex};" +
                    "offset=${catalogState.firstVisibleItemScrollOffset};" +
                    "threadIndex=${threadState.firstVisibleItemIndex};" +
                    "open=${selected != null};widthPx=${geometry.widthPx};heightPx=${geometry.heightPx};" +
                    "widthDp=${geometry.widthPx / density.density};heightDp=${geometry.heightPx / density.density};" +
                    "density=${density.density};fontScale=${density.fontScale};" +
                    "catalogWidthPx=${geometry.catalogWidthPx};detailWidthPx=${geometry.detailWidthPx};ready=$ready"
            },
    ) {
        val twoPane = journey == "two-pane"
        val width = maxWidth.value.toInt()
        val height = maxHeight.value.toInt()
        val wide = isWideWindow(width, height)
        require(!twoPane || showsTwoPanes(width, height)) {
            "Two-pane benchmark needs a window at least ${TWO_PANE_MIN_WIDTH_DP}dp wide and ${WIDE_SCREEN_MIN_DP}dp high"
        }
        val catalog: @Composable () -> Unit = {
            DisposableEffect(Unit) {
                counters.catalogMounts += 1
                onDispose { }
            }
            SideEffect { counters.catalogCompositions += 1 }
            BoardScreen(
                state =
                    BoardScreenState(
                        board = "/bench/",
                        description = "Reader benchmark",
                        itemCount = data.rows.size,
                        rowAt = data.rows::getOrNull,
                        rowKey = { data.rows[it].id },
                        mode =
                            when (journey) {
                                "catalog-grid" -> CatalogMode.GRID
                                "catalog-card" -> CatalogMode.CARD
                                else -> CatalogMode.LIST
                            },
                        selectedRowId = selected?.id,
                        wideWindow = wide,
                    ),
                actions =
                    BoardScreenActions(
                        onOpenRow = { selected = it },
                    ),
                modifier = Modifier.onSizeChanged { geometry.catalogWidthPx = it.width },
                gridState = catalogState,
                thumbnail = { _, modifier -> thumbnail(modifier) },
            )
        }
        val thread: @Composable () -> Unit = {
            ThreadScreen(
                modifier = Modifier.onSizeChanged { geometry.detailWidthPx = it.width },
                subject = selected?.subject ?: "Populated benchmark thread",
                board = "/bench/",
                posts = data.thread.rowModels,
                layout = if (journey == "files") ThreadLayout.FILES else ThreadLayout.POSTS,
                files = data.thread.fileCells,
                listState = threadState,
                onBack =
                    if (twoPane) {
                        { selected = null }
                    } else {
                        null
                    },
                media = { row, modifier ->
                    val attachment =
                        data.thread.rowsById[row.id]
                            ?.post
                            ?.attachments
                            ?.firstOrNull()
                    if (attachment?.type == MediaType.VIDEO) {
                        BenchmarkInlineVideo("benchmark/bench/1/${row.stableKey}", inlineMedia, modifier, thumbnail)
                    } else {
                        thumbnail(modifier)
                    }
                },
                fileTile = { cell, modifier -> thumbnail(modifier.semantics { contentDescription = cell.id }) },
                body = { post -> ReaderPostComment(data.thread.rowsById.getValue(post.id)) },
                wideWindow = wide,
                statusText = "${data.thread.rows.size} posts · ${data.thread.fileCells.size} files",
            )
        }
        when (journey) {
            "two-pane" ->
                ReaderTwoPane(
                    catalogWidth = readerPaneWidths(width).catalogDp.dp,
                    threadOpen = selected != null,
                    listContent = catalog,
                    detailContent = {
                        Box(Modifier.fillMaxSize().onSizeChanged { geometry.detailWidthPx = it.width }) {
                            if (selected != null) thread()
                        }
                    },
                )
            "thread", "files" -> thread()
            // The redesign's Home feed in card view: the real FeedScreen with the fixture rows.
            "home-feed" ->
                FeedScreen(
                    state =
                        FeedScreenState(
                            rows = data.rows,
                            mode = CatalogMode.CARD,
                            wideWindow = wide,
                        ),
                    actions = FeedScreenActions(),
                    modifier = Modifier.onSizeChanged { geometry.catalogWidthPx = it.width },
                    thumbnail = { _, modifier -> thumbnail(modifier) },
                )
            else -> catalog()
        }
    }
}

/**
 * An inline thread video as the thread screen shows one: elected by [ThreadInlineMediaFrame] (one
 * plays, after the settle) and played muted and looping by the production [VideoPlayer], from a
 * small local clip so the measurement does not depend on the network.
 */
@Composable
private fun BenchmarkInlineVideo(
    id: String,
    state: ThreadInlineMediaState,
    modifier: Modifier,
    thumbnail: @Composable (Modifier) -> Unit,
) {
    val context = LocalContext.current
    val url = remember(context) { "android.resource://${context.packageName}/${R.raw.reader_benchmark_video}" }
    ThreadInlineMediaFrame(state, id, enabled = true, modifier = modifier) { active ->
        thumbnail(Modifier.fillMaxSize())
        if (active) {
            VideoPlayer(
                url,
                Modifier.fillMaxSize(),
                autoPlay = true,
                muted = true,
                active = true,
                loop = true,
                feedMode = true,
            )
        }
    }
}

private suspend fun readerFixtureData(media: Boolean): ReaderFixtureData =
    ReaderFixtureData(
        rows =
            List(FIXTURE_ITEMS) { index ->
                FeedRow(
                    id = "benchmark/bench/${index + 1}",
                    subject = "Benchmark thread ${index + 1}",
                    board = "/bench/",
                    activity = "2m",
                    replies = index + 3,
                    media = 4,
                    excerpt = "A populated reader catalog with prepared preview text.\n>Quoted preview line",
                    site = "Benchmark",
                    read = index % READ_INTERVAL == 0,
                    unread = if (index % UNREAD_INTERVAL == 0) 2 else 0,
                    hasPreview = true,
                )
            },
        thread = readerFixtureThread(media).preparePresentation(nowMillis = FIXTURE_TIME_MILLIS),
    )

/**
 * With [media] (the thread journey), every post is a long, selectable rich comment and every
 * [VIDEO_INTERVAL]th post carries an inline video, so scrolling measures text selection and
 * inline players as a real thread does. The files and two-pane journeys keep the short posts.
 */
private fun readerFixtureThread(media: Boolean): Thread {
    val key = ThreadKey(ProviderId("benchmark"), BoardId("bench"), ThreadId(1))
    val posts =
        List(FIXTURE_ITEMS) { index ->
            val previous = PostId(index.toLong().coerceAtLeast(1))
            ModelPost(
                id = PostId(index + 1L),
                board = key.board,
                threadId = key.thread,
                isOriginalPost = index == 0,
                subject = if (index == 0) "Populated benchmark thread" else null,
                poster = PosterInfo(name = "Reader ${index + 1}"),
                createdAtMillis = FIXTURE_TIME_MILLIS,
                comment =
                    PostComment(
                        raw = "",
                        nodes =
                            persistentListOf(
                                PostNode.Text("Prepared benchmark post ${index + 1}. "),
                                PostNode.QuoteLink(previous),
                                PostNode.LineBreak,
                                PostNode.Styled(
                                    InlineStyle.GREENTEXT,
                                    persistentListOf(PostNode.Text(">Quoted reader text")),
                                ),
                                PostNode.Text(" "),
                                PostNode.Link(
                                    "https://example.com/reader",
                                    persistentListOf(PostNode.Text("Reader link")),
                                ),
                                PostNode.Text(" "),
                                PostNode.Styled(
                                    InlineStyle.SPOILER,
                                    persistentListOf(PostNode.Text("Hidden fixture text")),
                                ),
                            ).addingAll(if (media) longReply(index) else persistentListOf()),
                    ),
                repliesTo = if (index > 0) persistentListOf(previous) else persistentListOf(),
                attachments =
                    persistentListOf(
                        MediaAttachment(
                            id = "file-$index",
                            originalFileName =
                                if (media &&
                                    index % VIDEO_INTERVAL == 1
                                ) {
                                    "clip.mp4"
                                } else {
                                    "thumbnail.png"
                                },
                            extension = if (media && index % VIDEO_INTERVAL == 1) "mp4" else "png",
                            type = if (media && index % VIDEO_INTERVAL == 1) MediaType.VIDEO else MediaType.IMAGE,
                            sourceUrl = "https://example.invalid/unused-original",
                            thumbnailUrl = "https://example.invalid/unused-thumbnail",
                            sizeBytes = FIXTURE_FILE_BYTES,
                        ),
                    ),
            )
        }
    return Thread(
        key = key,
        originalPost = posts.first(),
        replies = posts.drop(1).toPersistentList(),
        stats = ThreadStats(replyCount = FIXTURE_ITEMS - 1, imageCount = FIXTURE_ITEMS),
    )
}

/** Several paragraphs, the length of a long reply, after the short fixture comment. */
private fun longReply(index: Int) =
    persistentListOf(
        PostNode.LineBreak,
        PostNode.LineBreak,
        PostNode.Text(
            "Long benchmark reply ${index + 1}. A reader post that runs over several lines, the way long " +
                "thread replies do, so each row lays out and measures a tall block of selectable text " +
                "while the thread scrolls past it.",
        ),
        PostNode.LineBreak,
        PostNode.Styled(
            InlineStyle.GREENTEXT,
            persistentListOf(PostNode.Text(">A quoted line from the post it answers, kept green.")),
        ),
        PostNode.LineBreak,
        PostNode.Text(
            "A second paragraph adds the rest: some detail, a ",
        ),
        PostNode.Styled(InlineStyle.BOLD, persistentListOf(PostNode.Text("bold aside"))),
        PostNode.Text(
            ", and a closing thought that wraps onto another line or two on a phone-width screen " +
                "before the post ends.",
        ),
    )

private const val FIXTURE_ITEMS = 200
private const val VIDEO_INTERVAL = 3
private const val READ_INTERVAL = 3
private const val UNREAD_INTERVAL = 5
private const val FIXTURE_TIME_MILLIS = 1_700_000_000_000L
private const val FIXTURE_FILE_BYTES = 12_288L
private const val FIXTURE_STATUS_TAG = "reader-benchmark-status"
