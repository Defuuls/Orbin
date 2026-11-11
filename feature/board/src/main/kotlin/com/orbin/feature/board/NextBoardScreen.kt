package com.orbin.feature.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.orbin.core.model.isWideWindow
import com.orbin.media.image.MediaThumbnail
import com.orbin.uinext.BoardScreen
import com.orbin.uinext.CatalogMode
import com.orbin.uinext.FeedRow
import com.orbin.uinext.LocalReaderOpenDrawer
import com.orbin.uinext.NextDestination
import com.orbin.uinext.NextPullToRefresh
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ReaderBoardPicker
import com.orbin.uinext.ReaderCatalogSearch
import com.orbin.uinext.ReaderCatalogSkeleton
import com.orbin.uinext.ReaderEmptyState
import com.orbin.uinext.ReaderErrorState
import com.orbin.uinext.ReaderNavigation
import com.orbin.uinext.ReaderRefreshFailure
import com.orbin.uinext.ReaderScaffold
import com.orbin.core.model.CatalogMode as StoredCatalogMode

/** A paged catalog keeps its frame and loaded rows through refresh failures. */
@Composable
fun NextBoardScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenMedia: ((board: String) -> Unit)? = null,
    selectedThread: Long? = null,
    viewModel: BoardViewModel = hiltViewModel(),
    onDestination: ((NextDestination) -> Unit)? = null,
    onOpenBoard: ((provider: String, board: String, title: String) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    picker: BoardPickerViewModel = hiltViewModel(),
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(viewModel, lifecycle) {
        val observer =
            LifecycleEventObserver { _, _ ->
                viewModel.setCatalogVisible(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            }
        lifecycle.addObserver(observer)
        viewModel.setCatalogVisible(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.setCatalogVisible(false)
        }
    }
    val threads = viewModel.preparedCatalog.collectAsLazyPagingItems()
    val catalogItemKey = threads.itemKey { prepared -> prepared.row.id }
    val visitedThreadIds by viewModel.visitedThreadIds.collectAsStateWithLifecycle()
    val watchedUnread by viewModel.watchedUnread.collectAsStateWithLifecycle()
    val catalogSort by viewModel.catalogSort.collectAsStateWithLifecycle()
    val retainedRefreshFailure by viewModel.catalogRefreshFailure.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val boardOptions by picker.options.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val uriHandler = LocalUriHandler.current
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var pickerVisible by rememberSaveable { mutableStateOf(false) }
    val board = "/${viewModel.boardId}/"
    val snapshot = threads.itemSnapshotList
    val byThreadId = remember(snapshot) { snapshot.items.associate { it.thread.key.thread.value to it.thread } }
    val rows =
        remember(snapshot, visitedThreadIds, watchedUnread, board, threads.itemCount) {
            List(threads.itemCount) { index ->
                snapshot.getOrNull(index)?.let { prepared ->
                    val threadId = prepared.thread.key.thread.value
                    prepared.row.copy(read = threadId in visitedThreadIds, unread = watchedUnread[threadId] ?: 0)
                }
            }
        }
    val matching = remember(rows, query) { catalogMatchingIndices(rows, query) }
    val rowFor: (Int) -> FeedRow? = { index ->
        val sourceIndex = matching[index]
        // Accessing the Paging item triggers prefetch without rebuilding the presentation.
        if (sourceIndex in 0 until threads.itemCount) threads[sourceIndex]
        rows.getOrNull(sourceIndex)
    }
    val mode = if (settings.catalogMode == StoredCatalogMode.GRID) CatalogMode.GRID else CatalogMode.LIST
    val wide = isWideWindow(configuration.screenWidthDp, configuration.screenHeightDp)
    val load = boardCatalogLoadState(threads.itemCount, threads.loadState.refresh)
    val failure = (load.failure ?: retainedRefreshFailure)?.catalogFailure()
    val browserUrl = picker.boardWebUrl(viewModel.providerId, viewModel.boardId)

    NextTheme {
        ReaderScaffold(
            title = board,
            modifier = modifier,
            destination = NextDestination.BOARDS,
            onDestination = onDestination,
            onBack = onBack.takeIf { onDestination == null },
            drawAppBar = false,
        ) {
            val openDrawer = LocalReaderOpenDrawer.current
            Box(Modifier.fillMaxSize()) {
                NextPullToRefresh(isRefreshing = load.isRefreshing, onRefresh = threads::refresh) {
                    BoardScreen(
                        board = board,
                        description = viewModel.title,
                        itemCount = matching.size,
                        rowAt = rowFor,
                        rowKey = { index -> catalogItemKey(matching[index]) },
                        sortLabel = catalogSort.label,
                        onSort = viewModel::cycleCatalogSort,
                        navigation =
                            when {
                                openDrawer != null -> ReaderNavigation.MENU
                                onBack != null -> ReaderNavigation.BACK
                                else -> ReaderNavigation.NONE
                            },
                        onNavigation = openDrawer ?: onBack ?: {},
                        onPickBoard =
                            onOpenBoard?.let {
                                {
                                    picker.refresh()
                                    pickerVisible = true
                                }
                            },
                        onSearch = {
                            searchVisible = !searchVisible
                            if (!searchVisible) query = ""
                        },
                        onOpenBrowser = browserUrl?.let { url -> { uriHandler.openUri(url) } },
                        onOpenMedia = onOpenMedia?.let { open -> { open(viewModel.boardId) } },
                        onOpenRow = { row ->
                            row.threadId()?.let { id ->
                                onOpenThread(
                                    viewModel.providerId,
                                    viewModel.boardId,
                                    id,
                                    byThreadId[id]?.originalPost?.subject ?: "No.$id",
                                )
                            }
                        },
                        mode = mode,
                        onModeChange = { selected ->
                            viewModel.setCatalogMode(
                                if (selected ==
                                    CatalogMode.GRID
                                ) {
                                    StoredCatalogMode.GRID
                                } else {
                                    StoredCatalogMode.LIST
                                },
                            )
                        },
                        onReload = threads::refresh,
                        reloading = load.isRefreshing,
                        wideWindow = wide,
                        selectedRowId = selectedThread?.let { "${viewModel.providerId}/${viewModel.boardId}/$it" },
                        headerContent =
                            if (searchVisible) {
                                {
                                    ReaderCatalogSearch(query, { query = it }, {
                                        query = ""
                                        searchVisible = false
                                    })
                                }
                            } else {
                                null
                            },
                        stateContent =
                            when (load.content) {
                                BoardCatalogContent.LOADING -> {
                                    { ReaderCatalogSkeleton(mode = mode, wideWindow = wide, showProgress = false) }
                                }
                                BoardCatalogContent.ERROR -> {
                                    { ReaderErrorState(failure!!.readerCause, threads::retry) }
                                }
                                BoardCatalogContent.EMPTY -> {
                                    {
                                        ReaderEmptyState(
                                            stringResource(R.string.next_board_empty),
                                            stringResource(R.string.board_empty_hint),
                                            threads::refresh,
                                        )
                                    }
                                }
                                BoardCatalogContent.CONTENT ->
                                    if (matching.isEmpty()) {
                                        {
                                            ReaderEmptyState(
                                                stringResource(R.string.board_no_matches),
                                                stringResource(R.string.board_no_matches_hint),
                                            )
                                        }
                                    } else {
                                        null
                                    }
                            },
                        thumbnail = { row, tileModifier ->
                            row.threadId()?.let { id ->
                                byThreadId[id]?.originalPost?.attachments?.firstOrNull()?.let { attachment ->
                                    MediaThumbnail(
                                        // The shared catalog slot owns spoiler reveal before composing media.
                                        attachment = attachment.copy(isSpoiler = false),
                                        fullResolution = false,
                                        contentScale = ContentScale.Crop,
                                        cornerRadius = 0.dp,
                                        modifier = tileModifier,
                                    )
                                }
                            }
                        },
                    )
                }
                if (failure != null &&
                    (load.content == BoardCatalogContent.CONTENT || load.content == BoardCatalogContent.EMPTY)
                ) {
                    ReaderRefreshFailure(
                        failure.readerCause,
                        threads::refresh,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        if (pickerVisible && onOpenBoard != null) {
            ReaderBoardPicker(
                boards = boardOptions,
                onSelect = { selected ->
                    pickerVisible = false
                    onOpenBoard(selected.providerId, selected.boardId, selected.title)
                },
                onDismiss = { pickerVisible = false },
                selectedProvider = viewModel.providerId,
                selectedBoard = viewModel.boardId,
            )
        }
    }
}

/** Searches only the loaded catalog, retaining placeholders while the query is empty. */
internal fun catalogMatchingIndices(
    rows: List<FeedRow?>,
    query: String,
): List<Int> {
    val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return rows.indices.filter { index ->
        terms.isEmpty() ||
            rows[index]?.let { row ->
                terms.all { term -> "${row.subject} ${row.excerpt}".contains(term, ignoreCase = true) }
            } ==
            true
    }
}

/** The thread number encoded in a row's id, or null if the id is not one this screen made. */
private fun FeedRow.threadId(): Long? = id.substringAfterLast('/').toLongOrNull()
