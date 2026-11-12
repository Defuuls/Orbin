package com.orbin.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.attachmentDetails
import com.orbin.core.model.comparator
import com.orbin.core.model.isWideWindow
import com.orbin.core.model.readerPaneWidths
import com.orbin.core.model.showsTwoPanes
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_catalog_empty_message
import com.orbin.ios.resources.ios_catalog_empty_title
import com.orbin.ios.resources.ios_catalog_no_matches_message
import com.orbin.ios.resources.ios_catalog_no_matches_title
import com.orbin.ios.resources.ios_reader_reload
import com.orbin.uinext.BoardScreen
import com.orbin.uinext.CatalogMode
import com.orbin.uinext.LocalReaderOpenDrawer
import com.orbin.uinext.MediaCell
import com.orbin.uinext.MediaWallScreen
import com.orbin.uinext.NextDestination
import com.orbin.uinext.NextPullToRefresh
import com.orbin.uinext.ReaderAction
import com.orbin.uinext.ReaderBoardOption
import com.orbin.uinext.ReaderBoardPicker
import com.orbin.uinext.ReaderCatalogSearch
import com.orbin.uinext.ReaderCatalogSkeleton
import com.orbin.uinext.ReaderEmptyState
import com.orbin.uinext.ReaderErrorState
import com.orbin.uinext.ReaderIcons
import com.orbin.uinext.ReaderNavigation
import com.orbin.uinext.ReaderRefreshFailure
import com.orbin.uinext.ReaderRefreshProgress
import com.orbin.uinext.ReaderScaffold
import com.orbin.uinext.ReaderTwoPane
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import com.orbin.core.model.CatalogMode as StoredCatalogMode

// A board: its catalog, its media wall, the iPad split beside a thread, and catalog tiles.

@Composable
internal fun CatalogDestination(
    browser: Browser,
    board: SiteBoard,
    // True when the caller already knows the window is wide, as the two-pane split does.
    wideWindow: Boolean = false,
    selectedThread: ThreadKey? = null,
) {
    val reload = remember(browser, board) { { browser.refreshCatalog(board) } }
    val catalog by browser.catalog.collectAsState()
    val preparedRows by browser.preparedCatalogRows.collectAsState()
    val refreshing by browser.catalogRefreshing.collectAsState()
    val refreshFailure by browser.catalogRefreshFailure.collectAsState()
    val boards by browser.boards.collectAsState()
    val settings by browser.settings.current.collectAsState()
    val sort by browser.catalogSort.collectAsState()
    val visited by remember(board) { browser.visitedThreads(board) }.collectAsState(emptySet())
    val unread by remember(board) { browser.watched.unread(board) }.collectAsState(emptyMap())
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var pickerVisible by rememberSaveable { mutableStateOf(false) }
    val threads = (catalog as? Load.Ready)?.value.orEmpty()
    val sorted = remember(threads, sort) { threads.sortedWith(sort.comparator()) }
    val rows =
        remember(sorted, preparedRows, visited, unread, query) {
            val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            sorted
                .mapNotNull { thread ->
                    val number = thread.key.thread.value
                    preparedRows[thread.key]?.copy(read = number in visited, unread = unread[number] ?: 0)
                }.filter { row ->
                    terms.all { term -> "${row.subject} ${row.excerpt}".contains(term, ignoreCase = true) }
                }
        }
    val byRow = remember(threads) { threads.associateBy { it.key.catalogRowId } }
    val availableBoards =
        remember(boards, settings.hideNsfwBoards) {
            (boards as? Load.Ready)?.value.orEmpty().filterNot { settings.hideNsfwBoards && it.board.isNsfw }
        }
    val boardOptions =
        remember(availableBoards) {
            availableBoards.map { ReaderBoardOption(it.provider.value, it.siteName, it.board.id.value, it.board.title) }
        }
    val mode = if (settings.catalogMode == StoredCatalogMode.GRID) CatalogMode.GRID else CatalogMode.LIST
    val uriHandler = LocalUriHandler.current
    val webUrl = remember(board) { browser.boardWebUrl(board) }
    ReaderScaffold(
        title = board.board.title,
        destination = NextDestination.BOARDS,
        onDestination = browser::openDestination,
        drawAppBar = false,
    ) { _ ->
        val openDrawer = LocalReaderOpenDrawer.current
        NextPullToRefresh(
            isRefreshing = refreshing,
            onRefresh = reload,
            modifier = Modifier.fillMaxSize(),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // The window's size, not the pane's, decides the thumbnail size.
                val wide = wideWindow || isWideWindow(maxWidth.value.toInt(), maxHeight.value.toInt())
                val state: (@Composable () -> Unit)? =
                    when (val load = catalog) {
                        Load.Loading -> {
                            { ReaderCatalogSkeleton(mode = mode, wideWindow = wide, showProgress = false) }
                        }
                        is Load.Failed -> {
                            {
                                ReaderErrorState(
                                    load.cause.toReaderCause(),
                                    onRetry = reload,
                                    message = load.message,
                                )
                            }
                        }
                        is Load.Ready ->
                            if (rows.isEmpty()) {
                                {
                                    ReaderEmptyState(
                                        title =
                                            stringResource(
                                                if (query.isBlank()) {
                                                    Res.string.ios_catalog_empty_title
                                                } else {
                                                    Res.string.ios_catalog_no_matches_title
                                                },
                                            ),
                                        message =
                                            stringResource(
                                                if (query.isBlank()) {
                                                    Res.string.ios_catalog_empty_message
                                                } else {
                                                    Res.string.ios_catalog_no_matches_message
                                                },
                                            ),
                                        onReload = reload,
                                    )
                                }
                            } else {
                                null
                            }
                    }
                BoardScreen(
                    wideWindow = wide,
                    navigation = ReaderNavigation.MENU,
                    onNavigation = { openDrawer?.invoke() },
                    onPickBoard = { pickerVisible = true },
                    onSearch = {
                        searchVisible = !searchVisible
                        if (!searchVisible) query = ""
                    },
                    onReload = reload,
                    reloading = refreshing || catalog is Load.Loading,
                    board = "/${board.board.id.value}/",
                    description = board.board.title,
                    itemCount = rows.size,
                    selectedRowId = selectedThread?.catalogRowId,
                    rowAt = { index -> rows.getOrNull(index) },
                    sortLabel = sort.label,
                    onSort = browser::cycleCatalogSort,
                    mode = mode,
                    onModeChange = { selected ->
                        browser.settings.setCatalogMode(
                            if (selected ==
                                CatalogMode.GRID
                            ) {
                                StoredCatalogMode.GRID
                            } else {
                                StoredCatalogMode.LIST
                            },
                        )
                    },
                    onOpenRow = { row -> byRow[row.id]?.let { browser.openThread(it.key) } },
                    onOpenMedia = { browser.open(Route.BoardMedia(board)) },
                    onOpenBrowser = webUrl?.let { url -> { uriHandler.openUri(url) } },
                    headerContent =
                        if (searchVisible) {
                            {
                                ReaderCatalogSearch(
                                    query = query,
                                    onQueryChange = { query = it },
                                    onClose = {
                                        searchVisible = false
                                        query = ""
                                    },
                                )
                            }
                        } else {
                            null
                        },
                    stateContent = state,
                    thumbnail = { row, modifier ->
                        byRow[row.id]?.originalPost?.attachments?.firstOrNull()?.let { attachment ->
                            // The shared cell invokes this slot only after its spoiler is revealed.
                            ReaderThumbnail(
                                attachment.copy(isSpoiler = false),
                                modifier,
                                sharp = mode.sharpensThumbnails,
                            )
                        }
                    },
                )
                refreshFailure?.let { failure ->
                    ReaderRefreshFailure(
                        cause = failure.cause.toReaderCause(),
                        onRetry = reload,
                        message = failure.message,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
    if (pickerVisible) {
        ReaderBoardPicker(
            boards = boardOptions,
            selectedProvider = board.provider.value,
            selectedBoard = board.board.id.value,
            onDismiss = { pickerVisible = false },
            onSelect = { option ->
                pickerVisible = false
                availableBoards
                    .firstOrNull {
                        it.provider.value == option.providerId &&
                            it.board.id.value == option.boardId
                    }?.let(browser::replaceBoard)
            },
        )
    }
}

/** A board's media wall: every file its catalog carries, through the same sweep Android's wall runs. */
@Composable
internal fun BoardMediaDestination(
    browser: Browser,
    board: SiteBoard,
) {
    val catalog by browser.catalog.collectAsState()
    val refreshing by browser.catalogRefreshing.collectAsState()
    val refreshFailure by browser.catalogRefreshFailure.collectAsState()
    val reload = remember(browser, board) { { browser.refreshCatalog(board) } }
    val label = "/${board.board.id.value}/"
    ReaderScaffold(
        title = label,
        onBack = { browser.back() },
        actions = listOf(ReaderAction(ReaderIcons.Refresh, stringResource(Res.string.ios_reader_reload), reload)),
    ) { _ ->
        NextPullToRefresh(
            isRefreshing = refreshing,
            onRefresh = reload,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    ReaderRefreshProgress(refreshing || catalog is Load.Loading)
                    when (val load = catalog) {
                        Load.Loading -> ReaderCatalogSkeleton(showProgress = false)
                        is Load.Failed ->
                            ReaderErrorState(
                                load.cause.toReaderCause(),
                                onRetry = reload,
                                message = load.message,
                            )
                        is Load.Ready -> {
                            val items = remember(load.value) { boardMediaItems(board, load.value) }
                            val byId = remember(items) { items.associateBy { "${it.key.provider.value}/${it.id}" } }
                            MediaWallScreen(
                                scanned = 1,
                                total = 1,
                                failed = 0,
                                cells =
                                    remember(items) {
                                        items.map {
                                            MediaCell(
                                                id = "${it.key.provider.value}/${it.id}",
                                                board = "${it.key.provider.value} $label",
                                                spoiler = it.attachment.isSpoiler,
                                                details = it.attachment.attachmentDetails(),
                                            )
                                        }
                                    },
                                showRail = false,
                                title = label,
                                onOpen = { cell -> byId[cell.id]?.let { browser.openThread(it.key) } },
                                tile = { cell, modifier ->
                                    byId[cell.id]?.let { item ->
                                        Box(modifier) {
                                            SharpImage(
                                                item.attachment,
                                                contentDescription = null,
                                                Modifier.fillMaxSize(),
                                                fill = Modifier.fillMaxSize(),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                refreshFailure?.let { failure ->
                    ReaderRefreshFailure(
                        failure.cause.toReaderCause(),
                        onRetry = reload,
                        message = failure.message,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

/** A board reader uses the catalog's identity while an adjacent thread opens or closes. */
internal data class BoardReaderRoute(
    val catalog: Route.Catalog,
    val thread: Route.ThreadPage? = null,
)

/** A thread is adjacent only to the immediately preceding catalog from the same site and board. */
internal fun boardReaderRoute(backStack: List<Route>): BoardReaderRoute? =
    when (val route = backStack.lastOrNull()) {
        is Route.Catalog -> BoardReaderRoute(route)
        is Route.ThreadPage -> {
            val catalog = backStack.getOrNull(backStack.lastIndex - 1) as? Route.Catalog
            catalog
                ?.takeIf { it.board.provider == route.key.provider && it.board.board.id == route.key.board }
                ?.let { BoardReaderRoute(it, route) }
        }
        else -> null
    }

private val ThreadKey.catalogRowId: String get() = "${provider.value}/${board.value}/${thread.value}"

/**
 * The same catalog state key owns both the empty and selected reader. Each thread keeps its own
 * existing key, so resizing or returning from media restores its place without another catalog load.
 */
@Composable
internal fun BoardReaderDestination(
    browser: Browser,
    route: BoardReaderRoute,
    downloads: MediaDownloads,
    pages: ThreadPages?,
    stateHolder: SaveableStateHolder,
) {
    BoardReaderFrame(
        route = route,
        stateHolder = stateHolder,
        catalogContent = { board, wideWindow, selectedThread ->
            CatalogDestination(browser, board, wideWindow, selectedThread)
        },
        threadContent = { ThreadDestination(browser, downloads, pages) },
        onDestination = browser::openDestination,
        onVisiblePanesChanged = { wide, catalogVisible, threadVisible ->
            browser.setReaderPanesVisible(route, wide, catalogVisible, threadVisible)
        },
    )
}

/** The production reader frame, with data loading and platform actions supplied by its host. */
@Composable
internal fun BoardReaderFrame(
    route: BoardReaderRoute,
    stateHolder: SaveableStateHolder,
    catalogContent: @Composable (SiteBoard, Boolean, ThreadKey?) -> Unit,
    threadContent: @Composable (Route.ThreadPage) -> Unit,
    onDestination: ((NextDestination) -> Unit)? = null,
    onVisiblePanesChanged: ((wide: Boolean, catalogVisible: Boolean, threadVisible: Boolean) -> Unit)? = null,
) {
    val reportVisiblePanes by rememberUpdatedState(onVisiblePanesChanged)
    DisposableEffect(Unit) {
        onDispose { reportVisiblePanes?.invoke(false, false, false) }
    }
    // Move the provider together with its content. A new provider at another call site would
    // initialize before the outgoing provider saves on disposal, losing the live reading state.
    val catalogPane =
        remember(stateHolder) {
            movableContentOf<
                BoardReaderRoute,
                Boolean,
                @Composable (SiteBoard, Boolean, ThreadKey?) -> Unit,
            > { reader, wideWindow, content ->
                stateHolder.SaveableStateProvider(reader.catalog.stateKey) {
                    content(reader.catalog.board, wideWindow, reader.thread?.key)
                }
            }
        }
    val threadPane =
        remember(stateHolder) {
            movableContentOf<Route.ThreadPage, @Composable (Route.ThreadPage) -> Unit> { thread, content ->
                stateHolder.SaveableStateProvider(thread.stateKey) {
                    content(thread)
                }
            }
        }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value.roundToInt()
        val height = maxHeight.value.roundToInt()
        val thread = route.thread
        val wide = showsTwoPanes(width, height)
        SideEffect { reportVisiblePanes?.invoke(wide, wide || thread == null, thread != null) }
        if (wide) {
            ReaderScaffold(
                title = route.catalog.board.board.title,
                destination = NextDestination.BOARDS,
                onDestination = onDestination,
                drawAppBar = false,
            ) { _ ->
                ThreadSplitDestination(
                    catalogWidth = readerPaneWidths(width).catalogDp.dp,
                    threadOpen = thread != null,
                    catalogContent = { catalogPane(route, true, catalogContent) },
                    threadContent = { thread?.let { threadPane(it, threadContent) } },
                )
            }
        } else if (thread == null) {
            catalogPane(route, false, catalogContent)
        } else {
            threadPane(thread, threadContent)
        }
    }
}

/** Both catalog routes use one shared split; the browser still owns loading and back navigation. */
@Composable
internal fun ThreadSplitDestination(
    catalogWidth: Dp,
    threadOpen: Boolean,
    catalogContent: @Composable () -> Unit,
    threadContent: @Composable () -> Unit,
) {
    ReaderTwoPane(
        catalogWidth = catalogWidth,
        threadOpen = threadOpen,
        modifier = Modifier.fillMaxSize(),
        listContent = catalogContent,
        detailContent = threadContent,
    )
}

/** A video whose frames this device can decode: WebM needs iOS 17.4, MP4 and MOV play anywhere. */
private val MediaAttachment.hasDecodableVideo: Boolean
    get() = type == MediaType.VIDEO && (!isWebM || supportsWebM)

/**
 * A file's picture at the size it is shown: the site's thumbnail first, so the space fills at
 * once, then, for a still image, the full picture over it. A card or a post's image is far wider
 * than a thumbnail (about 250 pixels), which stretched looks blurry; Android loads full resolution
 * there too. The thumbnail sets the size, so the full picture only ever sharpens it.
 */
@Composable
internal fun SharpImage(
    attachment: MediaAttachment,
    contentDescription: String?,
    modifier: Modifier,
    // How the thumbnail takes the space: a card's whole fixed box, or a post's width at the
    // picture's own proportions.
    fill: Modifier = Modifier.fillMaxWidth(),
    videoFrame: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
) {
    // Hovering the Apple Pencil over a picture shows it larger, without opening it.
    PencilHoverPreview(attachment, modifier) {
        AsyncImage(
            model = attachment.thumbnailUrl,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = fill,
        )
        val sharp = attachment.cardImageUrl()
        if (sharp != attachment.thumbnailUrl) {
            AsyncImage(
                model = sharp,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
            )
        }
        // A video has no full-size picture of its own: show its first frame, decoded from the file.
        if (videoFrame && attachment.hasDecodableVideo) {
            NativeVideoFrame(attachment.sourceUrl, Modifier.matchParentSize())
        }
    }
}
