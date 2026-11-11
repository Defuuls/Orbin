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
import com.orbin.core.model.showsTwoPanes
import com.orbin.core.reader.ReaderPostComment
import com.orbin.core.reader.ThreadPresentationIndex
import com.orbin.core.reader.preparePresentation
import com.orbin.uinext.BoardScreen
import com.orbin.uinext.CatalogMode
import com.orbin.uinext.FeedRow
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ReaderTwoPane
import com.orbin.uinext.ThreadLayout
import com.orbin.uinext.ThreadScreen
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
                    readerFixtureData()
                }
            setContent { NextTheme(darkTheme = false) { ReaderBenchmarkFixture(journey, data, counters) } }
        }
    }

    private companion object {
        const val JOURNEY_EXTRA = "readerJourney"
        const val CATALOG_LIST = "catalog-list"
        val JOURNEYS = setOf(CATALOG_LIST, "catalog-grid", "thread", "files", "two-pane")
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
            "Two-pane benchmark needs a window at least 800dp wide and 600dp high"
        }
        val catalog: @Composable () -> Unit = {
            DisposableEffect(Unit) {
                counters.catalogMounts += 1
                onDispose { }
            }
            SideEffect { counters.catalogCompositions += 1 }
            BoardScreen(
                modifier = Modifier.onSizeChanged { geometry.catalogWidthPx = it.width },
                board = "/bench/",
                description = "Reader benchmark",
                itemCount = data.rows.size,
                rowAt = data.rows::getOrNull,
                rowKey = { data.rows[it].id },
                mode = if (journey == "catalog-grid") CatalogMode.GRID else CatalogMode.LIST,
                state = catalogState,
                onOpenRow = { selected = it },
                selectedRowId = selected?.id,
                wideWindow = wide,
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
                media = { _, modifier -> thumbnail(modifier) },
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
            else -> catalog()
        }
    }
}

private suspend fun readerFixtureData(): ReaderFixtureData =
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
        thread = readerFixtureThread().preparePresentation(nowMillis = FIXTURE_TIME_MILLIS),
    )

private fun readerFixtureThread(): Thread {
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
                            ),
                    ),
                repliesTo = if (index > 0) persistentListOf(previous) else persistentListOf(),
                attachments =
                    persistentListOf(
                        MediaAttachment(
                            id = "file-$index",
                            originalFileName = "thumbnail.png",
                            extension = "png",
                            type = MediaType.IMAGE,
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

private const val FIXTURE_ITEMS = 200
private const val READ_INTERVAL = 3
private const val UNREAD_INTERVAL = 5
private const val FIXTURE_TIME_MILLIS = 1_700_000_000_000L
private const val FIXTURE_FILE_BYTES = 12_288L
private const val FIXTURE_STATUS_TAG = "reader-benchmark-status"
