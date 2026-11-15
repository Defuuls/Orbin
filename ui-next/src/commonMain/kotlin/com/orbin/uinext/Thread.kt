package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_post_collapse
import com.orbin.uinext.resources.next_post_collapsed
import com.orbin.uinext.resources.next_post_collapsed_state
import com.orbin.uinext.resources.next_post_expand
import com.orbin.uinext.resources.next_post_expanded_state
import com.orbin.uinext.resources.next_row_counts
import com.orbin.uinext.resources.next_row_files
import com.orbin.uinext.resources.next_row_replies
import com.orbin.uinext.resources.next_spoiler_reveal
import com.orbin.uinext.resources.next_thread_back_to
import com.orbin.uinext.resources.next_thread_download_all
import com.orbin.uinext.resources.next_thread_download_title
import com.orbin.uinext.resources.next_thread_files
import com.orbin.uinext.resources.next_thread_new_posts
import com.orbin.uinext.resources.next_thread_posts
import com.orbin.uinext.resources.next_thread_save_page
import com.orbin.uinext.resources.next_thread_scroll_top
import com.orbin.uinext.resources.next_thread_watch
import com.orbin.uinext.resources.next_thread_watching
import com.orbin.uinext.resources.reader_feed_save
import com.orbin.uinext.resources.reader_feed_saved_while_watching
import com.orbin.uinext.resources.reader_feed_unsave
import com.orbin.uinext.resources.reader_media_more
import com.orbin.uinext.resources.reader_new
import com.orbin.uinext.resources.reader_open_browser
import com.orbin.uinext.resources.reader_reload
import com.orbin.uinext.resources.reader_reply_link
import com.orbin.uinext.resources.reader_thread_op
import com.orbin.uinext.resources.reader_thread_share
import com.orbin.uinext.resources.reader_view_card
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

enum class ThreadLayout {
    POSTS,
    FILES,
    CARDS,
}

enum class PostFilterTarget { TEXT, NAME, TRIPCODE, COUNTRY }

/**
 * A post as the thread screen draws it. Hosts build these once per load, already formatted.
 *
 * [fileLabel] is the file name and size of the post's media, shown after [time].
 * [mediaCount] above one puts a count badge on the thumbnail.
 * [replyNumbers] are the posts that answer this one, each shown as a link; when a host does not
 * supply them, [replies] is shown as a plain count instead.
 */
data class Post(
    val number: String,
    val time: String,
    val body: String,
    val depth: Int = 0,
    val hasMedia: Boolean = false,
    val replies: Int = 0,
    val spoiler: Boolean = false,
    val id: String = number,
    val author: String = "",
    val fileLabel: String = "",
    val mediaCount: Int = if (hasMedia) 1 else 0,
    val replyNumbers: List<String> = emptyList(),
    val stableKey: String = id,
    val imageSpoiler: Boolean = false,
    val subject: String = "",
    val branchPath: String = stableKey,
    val primaryPath: String = branchPath,
    val ancestorPaths: List<String> = emptyList(),
    val descendantIds: Set<String> = emptySet(),
    val hiddenReason: String? = null,
    val preview: Boolean = false,
    val unseen: Boolean = false,
    val posterId: String? = null,
    val posterName: String? = null,
    val posterTripcode: String? = null,
    val posterCountry: String? = null,
    val posterCapcode: String? = null,
    val hiddenByUser: Boolean = false,
    val hiddenByPoster: Boolean = false,
)

object ThreadTags {
    const val LIST = "thread-list"
    const val POST = "thread-post"
    const val CARD = "thread-post-card"
    const val THUMBNAIL = "thread-post-thumbnail"
    const val UNREAD = "thread-unread-marker"
    const val STATUS = "thread-status"
    const val FILE = "thread-file"
    const val FILE_DETAILS = "thread-file-details"
    const val PROGRESS = "thread-progress"
    const val NEW_PILL = "thread-new-pill"
    const val BACK_PILL = "thread-back-pill"
}

/**
 * A fixed app bar over stacked post headers, comments, and inline media.
 *
 * The list always starts with one header item (the [subtitle] line, or nothing), so the post at
 * index n of [posts] is list item n + 1. Hosts that save and restore the reading position rely
 * on that.
 *
 * [wideWindow] is whether the window, not this pane, is wide: it selects the larger reader text.
 * [media] fills the bounded media slot below the comment. Hosts keep ownership of fetching,
 * decoding, playback, and opening the viewer.
 */
@Composable
fun ThreadScreen(
    subject: String,
    board: String,
    posts: List<Post>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    watching: Boolean = false,
    layout: ThreadLayout = ThreadLayout.POSTS,
    onLayoutChange: ((ThreadLayout) -> Unit)? = null,
    files: List<MediaCell> = emptyList(),
    onOpenFile: ((MediaCell) -> Unit)? = null,
    fileTile: (@Composable (MediaCell, Modifier) -> Unit)? = null,
    collapsed: Set<String> = emptySet(),
    onToggleCollapse: ((Post) -> Unit)? = null,
    onWatch: (() -> Unit)? = null,
    onDownloadAll: (() -> Unit)? = null,
    onSavePage: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onPostClick: ((Post) -> Unit)? = null,
    listState: LazyListState? = null,
    scrollToPostId: String? = null,
    firstUnreadPostId: String? = null,
    backToLabel: String? = null,
    onBackTo: (() -> Unit)? = null,
    onScrollConsumed: () -> Unit = {},
    body: (@Composable (Post) -> Unit)? = null,
    media: (@Composable (Post, Modifier) -> Unit)? = null,
    siteName: String = "",
    onBack: (() -> Unit)? = null,
    saved: Boolean = false,
    onToggleSaved: (() -> Unit)? = null,
    onReload: (() -> Unit)? = null,
    onPostReference: ((String) -> Unit)? = null,
    wideWindow: Boolean = false,
    reloading: Boolean = false,
    onOpenBrowser: (() -> Unit)? = null,
    statusText: String? = null,
    stateContent: (@Composable () -> Unit)? = null,
    allPosts: List<Post> = posts,
    onHidePost: ((Post) -> Unit)? = null,
    onHidePosterId: ((Post) -> Unit)? = null,
    onAddPostFilter: ((Post, PostFilterTarget) -> Unit)? = null,
    onRevealPost: ((Post) -> Unit)? = null,
    onVisiblePosts: ((Set<String>) -> Unit)? = null,
    onMarkAllRead: (() -> Unit)? = null,
    onMediaFilterChange: (MediaKindFilter) -> Unit = {},
    unreadPostIds: Set<String> = emptySet(),
    externalPostId: String? = null,
) {
    var detailPostId by rememberSaveable(subject, board, siteName) { mutableStateOf<String?>(null) }
    val detailPost = detailPostId?.let { id -> allPosts.firstOrNull { it.id == id } }
    var detailById by rememberSaveable(subject, board, siteName) { mutableStateOf(false) }
    var postMenu by remember { mutableStateOf<Post?>(null) }
    var mediaFilter by rememberSaveable(subject, board, siteName) { mutableStateOf(MediaKindFilter.ALL) }
    LaunchedEffect(externalPostId) { if (externalPostId != null) detailPostId = null }
    val displayedPosts =
        detailPost?.let { root ->
            allPosts
                .distinctBy { it.id }
                .filter { post ->
                    if (detailById) post.posterId == root.posterId else post.id in root.descendantIds
                }.map {
                    if (detailById) {
                        it.copy(
                            depth = 0,
                            hiddenReason = null,
                            preview = false,
                        )
                    } else {
                        it.copy(depth = 0)
                    }
                }
        } ?: posts
    val detailState = rememberLazyListState()
    val colors = reader
    val rootState = listState ?: rememberLazyListState()
    val state = if (detailPost == null) rootState else detailState
    val filesState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun openDetail(
        post: Post,
        byId: Boolean,
    ) {
        detailPostId = post.id
        detailById = byId
        scope.launch { detailState.scrollToItem(0) }
    }
    val showingPosts = layout != ThreadLayout.FILES
    val cardMode = layout == ThreadLayout.CARDS
    val closeDetail: () -> Unit = { detailPostId = null }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = detailPost != null,
        onBackCompleted = closeDetail,
    )
    val visibleCallback by rememberUpdatedState(onVisiblePosts)
    LaunchedEffect(state, displayedPosts, showingPosts, collapsed) {
        if (!showingPosts) return@LaunchedEffect
        val byKey = displayedPosts.associateBy { it.stableKey }
        snapshotFlow { state.layoutInfo.visibleItemsInfo.mapNotNull { byKey[it.key.toString()] } }
            .collectLatest { visible ->
                delay(180)
                val read =
                    visible.filter {
                        it.hiddenReason == null &&
                            !it.preview &&
                            it.branchPath !in collapsed &&
                            it.id !in collapsed
                    }
                visibleCallback?.invoke(read.mapTo(mutableSetOf()) { it.id })
            }
    }
    val input = LocalReaderInputState.current
    val effectiveBack = if (detailPost != null) closeDetail else onBack

    // Expands a collapsed post before bringing it into view, so the reader lands on its text.
    val reveal: (String) -> Unit = { id ->
        val target = displayedPosts.indexOfFirst { it.id == id }
        if (target < 0 ||
            displayedPosts.getOrNull(target)?.preview == true ||
            displayedPosts.getOrNull(target)?.hiddenReason != null
        ) {
            onPostReference?.invoke(id)
        } else {
            if (displayedPosts[target].branchPath in collapsed ||
                displayedPosts[target].id in collapsed
            ) {
                onToggleCollapse?.invoke(displayedPosts[target])
            }
            scope.launch { state.animateScrollToItem(target + HEADER_ITEMS) }
        }
    }
    var returnToId by rememberSaveable { mutableStateOf<String?>(null) }
    var returnToNumber by rememberSaveable { mutableStateOf<String?>(null) }
    var returnToPath by rememberSaveable { mutableStateOf<String?>(null) }
    var returnToOffset by rememberSaveable { mutableStateOf(0) }
    var handledReference by remember { mutableStateOf<String?>(null) }
    var returnDetailPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var returnDetailById by rememberSaveable { mutableStateOf(false) }
    val openReference: ((String) -> Unit)? =
        onPostReference?.let { outer ->
            { id ->
                if (detailPost != null && displayedPosts.none { it.id == id }) {
                    returnDetailPostId = detailPostId
                    returnDetailById = detailById
                    detailPostId = null
                }
                val visible = displayedPosts.getOrNull((state.firstVisibleItemIndex - HEADER_ITEMS).coerceAtLeast(0))
                if (visible != null && visible.id != id) {
                    returnToId = visible.id
                    returnToNumber = visible.number
                    returnToPath = visible.branchPath
                    returnToOffset = state.firstVisibleItemScrollOffset
                }
                handledReference = id
                outer(id)
            }
        }
    val resolvedBackLabel =
        backToLabel
            ?: returnToNumber?.let { number ->
                val raw = number.removePrefix("No.").trim()
                stringResource(Res.string.next_thread_back_to, raw)
            }
    val resolvedBackTo =
        onBackTo
            ?: returnToId?.let { id ->
                {
                    if (returnDetailPostId != null) {
                        detailPostId = returnDetailPostId
                        detailById = returnDetailById
                        returnDetailPostId = null
                    } else {
                        val pathIndex = displayedPosts.indexOfFirst { it.branchPath == returnToPath }
                        val target = if (pathIndex >= 0) pathIndex else displayedPosts.indexOfFirst { it.id == id }
                        if (target >=
                            0
                        ) {
                            scope.launch { state.animateScrollToItem(target + HEADER_ITEMS, returnToOffset) }
                        } else {
                            reveal(id)
                        }
                    }
                    returnToId = null
                    returnToNumber = null
                }
            }
    LaunchedEffect(scrollToPostId, displayedPosts, layout) {
        if (!showingPosts) return@LaunchedEffect
        if (scrollToPostId == null) {
            handledReference = null
            return@LaunchedEffect
        }
        if (handledReference != scrollToPostId) {
            val source = displayedPosts.getOrNull((state.firstVisibleItemIndex - HEADER_ITEMS).coerceAtLeast(0))
            if (source != null && source.id != scrollToPostId) {
                returnToId = source.id
                returnToNumber = source.number
                returnToPath = source.branchPath
                returnToOffset = state.firstVisibleItemScrollOffset
            }
            handledReference = scrollToPostId
        }
        if (detailPost != null &&
            displayedPosts.none { it.id == scrollToPostId } &&
            allPosts.any { it.id == scrollToPostId }
        ) {
            returnDetailPostId = detailPostId
            returnDetailById = detailById
            detailPostId = null
            return@LaunchedEffect
        }
        val target = displayedPosts.indexOfFirst { it.id == scrollToPostId }
        if (target >= 0) {
            if (displayedPosts[target].branchPath in collapsed ||
                displayedPosts[target].id in collapsed
            ) {
                onToggleCollapse?.invoke(displayedPosts[target])
            }
            state.animateScrollToItem(target + HEADER_ITEMS)
            onScrollConsumed()
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background)
                .readerKeyboardShortcuts(onBack = effectiveBack, onRefresh = onReload)
                .readerBackSwipe(effectiveBack)
                .onPreviewKeyEvent { event ->
                    val editingOrCommand = input.textEntryActive || event.isMetaPressed || event.isCtrlPressed
                    if (event.type != KeyEventType.KeyDown || editingOrCommand) {
                        false
                    } else {
                        when (event.key) {
                            Key.J, Key.DirectionDown -> {
                                scope.launch {
                                    state.animateScrollToItem(
                                        (state.firstVisibleItemIndex + 1).coerceAtMost(displayedPosts.size),
                                    )
                                }
                                true
                            }
                            Key.K, Key.DirectionUp -> {
                                scope.launch {
                                    state.animateScrollToItem(
                                        (state.firstVisibleItemIndex - 1).coerceAtLeast(0),
                                    )
                                }
                                true
                            }
                            Key.Escape -> {
                                if (detailPost !=
                                    null
                                ) {
                                    closeDetail()
                                    true
                                } else if (onBack != null) {
                                    onBack()
                                    true
                                } else {
                                    false
                                }
                            }
                            Key.R ->
                                if ((event.isMetaPressed || event.isCtrlPressed) &&
                                    onReload != null
                                ) {
                                    onReload()
                                    true
                                } else {
                                    false
                                }
                            else -> false
                        }
                    }
                }.semantics { paneTitle = subject },
    ) {
        ReaderAppBar(
            title = detailPost?.let { if (detailById) "ID:${it.posterId}" else "Replies to ${it.number}" } ?: subject,
            subtitle = listOf(sourceLabel(siteName), board).filter(String::isNotBlank).joinToString(" · "),
            navigation = if (onBack != null || detailPost != null) ReaderNavigation.BACK else ReaderNavigation.NONE,
            onNavigation = if (detailPost != null) closeDetail else onBack ?: {},
            onTitleTap = { scope.launch { state.animateScrollToItem(0) } },
            titleTapLabel = stringResource(Res.string.next_thread_scroll_top),
            actions =
                threadActions(
                    watching,
                    layout,
                    onWatch,
                    onLayoutChange.takeIf { detailPost == null },
                    onDownloadAll,
                ),
            overflow =
                threadOverflow(
                    subject = subject,
                    showingPosts = showingPosts,
                    layout = layout,
                    onLayoutChange = onLayoutChange,
                    onReload = onReload,
                    saved = saved,
                    watching = watching,
                    onToggleSaved = onToggleSaved,
                    onShare = onShare,
                    onDownloadAll = onDownloadAll,
                    onSavePage = onSavePage,
                    onOpenBrowser = onOpenBrowser,
                ) +
                    listOfNotNull(
                        onMarkAllRead?.let { ReaderMenuItem("Mark all read", icon = ReaderIcons.Check, onClick = it) },
                    ),
        )
        Box(Modifier.fillMaxWidth().height(ReaderSize.progress)) {
            if (reloading) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ReaderSize.progress)
                        .background(colors.accent)
                        .testTag(ThreadTags.PROGRESS),
                )
            }
        }
        if (stateContent != null) {
            stateContent()
            return@Column
        }
        val unreadFrom =
            firstUnreadPostId?.let { id -> displayedPosts.indexOfFirst { it.id == id } }?.takeIf { it > 0 }
        val newCount =
            if (unreadPostIds.isNotEmpty()) {
                unreadPostIds.size
            } else {
                unreadFrom?.let {
                    displayedPosts.size -
                        it
                }
            }
        ReaderFastScrollThumb(state = state, enabled = showingPosts, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                val fileColumns = catalogGridColumns(maxWidth)
                val visibleFiles = remember(files, mediaFilter) { files.filter { mediaFilter.shows(it.kind) } }
                val fileRows = remember(visibleFiles, fileColumns) { visibleFiles.chunked(fileColumns) }
                val cardImageHeight = orbinCatalogCardSpec(maxWidth).imageHeight
                val postImageWidth = (maxWidth - 28.dp).coerceAtLeast(1.dp)
                val viewportHeight = maxHeight
                LazyColumn(
                    state = if (showingPosts) state else filesState,
                    modifier = Modifier.fillMaxSize().testTag(ThreadTags.LIST),
                    verticalArrangement = if (cardMode) Arrangement.spacedBy(ORBIN_CATALOG_GAP) else Arrangement.Top,
                    contentPadding =
                        when {
                            cardMode ->
                                PaddingValues(
                                    top = ORBIN_CATALOG_GAP,
                                    bottom = bottomInset() + ORBIN_CATALOG_GAP,
                                )
                            showingPosts -> PaddingValues(bottom = bottomInset())
                            else ->
                                PaddingValues(
                                    start = ReaderSpace.albumPadding,
                                    end = ReaderSpace.albumPadding,
                                    top = ReaderSpace.albumPadding,
                                    bottom = bottomInset() + ReaderSpace.albumPadding,
                                )
                        },
                ) {
                    item(key = THREAD_HEADER_KEY, contentType = THREAD_HEADER_KEY) {
                        if (!showingPosts) {
                            MediaFilterPills(
                                filter = mediaFilter,
                                images = files.count { it.kind == MediaKind.IMAGE },
                                videos = files.count { it.kind == MediaKind.VIDEO },
                                onChange = {
                                    mediaFilter = it
                                    onMediaFilterChange(it)
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                total = files.size,
                            )
                        }
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = OrbinType.meta.withReaderFont(),
                                color = colors.inkMuted,
                                modifier =
                                    Modifier.fillMaxWidth().padding(
                                        horizontal = ReaderSpace.gutter,
                                        vertical = ReaderSpace.gapSmall,
                                    ),
                            )
                        }
                    }
                    if (showingPosts) {
                        itemsIndexed(displayedPosts, key = {
                            _,
                            post,
                            ->
                            post.stableKey
                        }, contentType = { _, _ -> layout }) {
                            index,
                            post,
                            ->
                            val hasHideActions = onHidePost != null || onHidePosterId != null || onAddPostFilter != null
                            val hasPostDetails = post.descendantIds.isNotEmpty() || !post.posterId.isNullOrBlank()
                            Column(Modifier.fillMaxWidth()) {
                                if (index > 0 && post.id == firstUnreadPostId) UnreadMarker()
                                PostCell(
                                    post = post,
                                    original = post.id == allPosts.firstOrNull()?.id,
                                    collapsed = post.branchPath in collapsed || post.id in collapsed,
                                    wideWindow = wideWindow,
                                    cardImageHeight = if (cardMode) cardImageHeight else null,
                                    inlineImageHeight =
                                        readerPostMediaHeight(
                                            postImageWidth - ReaderSpace.gap * post.depth.coerceIn(0, MAX_REPLY_DEPTH),
                                            viewportHeight,
                                        ),
                                    onToggleCollapse = onToggleCollapse,
                                    onClick = onPostClick,
                                    onMenu =
                                        if (hasHideActions || hasPostDetails) {
                                            { postMenu = it }
                                        } else {
                                            null
                                        },
                                    onReveal = onRevealPost,
                                    onShowId = { openDetail(it, true) },
                                    onReplies = { openDetail(it, false) },
                                    unreadReplies = post.descendantIds.count { it in unreadPostIds },
                                    onReference = openReference,
                                    body = body,
                                    media = media,
                                )
                            }
                        }
                        item(key = THREAD_STATUS_KEY, contentType = THREAD_STATUS_KEY) {
                            ThreadStatus(
                                replies = maxOf(0, displayedPosts.distinctBy { it.id }.size - 1),
                                fileCount = displayedPosts.distinctBy { it.id }.sumOf { it.mediaCount },
                                statusText = statusText,
                            )
                        }
                    } else {
                        items(
                            fileRows,
                            key = { row -> row.joinToString(separator = "|") { it.id } },
                            contentType = { ThreadLayout.FILES },
                        ) { rowOfFiles ->
                            FileRow(rowOfFiles, fileColumns, onOpenFile, fileTile)
                        }
                    }
                }
            }
            if (showingPosts) {
                ThreadJumpPills(
                    newLabel =
                        newCount?.let { count ->
                            pluralStringResource(Res.plurals.next_thread_new_posts, count, count)
                        },
                    onNew = firstUnreadPostId?.let { id -> { reveal(id) } },
                    backLabel = resolvedBackLabel,
                    onBackTo = resolvedBackTo,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
    postMenu?.let { post ->
        val actions =
            buildList {
                if (post.descendantIds.isNotEmpty()) {
                    add(NextSheetAction("Replies to ${post.number}", { openDetail(post, false) }, ReaderIcons.Chat))
                }
                if (!post.posterId.isNullOrBlank()) {
                    add(NextSheetAction("Posts by ID:${post.posterId}", { openDetail(post, true) }, ReaderIcons.Info))
                }
                if (post.id != allPosts.firstOrNull()?.id) {
                    onHidePost?.let { hide ->
                        add(
                            NextSheetAction(if (post.hiddenByUser) "Stop hiding this post" else "Hide this post", {
                                hide(post)
                            }, ReaderIcons.Hide),
                        )
                    }
                }
                if (!post.posterId.isNullOrBlank()) {
                    onHidePosterId?.let { hide ->
                        val label =
                            if (post.hiddenByPoster) "Stop hiding ID:${post.posterId}" else "Hide ID:${post.posterId}"
                        add(
                            NextSheetAction(label, {
                                hide(post)
                            }, ReaderIcons.Hide),
                        )
                    }
                }
                onAddPostFilter?.let { filter ->
                    if (post.body.isNotBlank()) {
                        add(
                            NextSheetAction("Hide posts with this text", {
                                filter(post, PostFilterTarget.TEXT)
                            }, ReaderIcons.Hide),
                        )
                    }
                    if (!post.posterName.isNullOrBlank() &&
                        !post.posterName.equals("Anonymous", true)
                    ) {
                        add(
                            NextSheetAction(
                                "Hide posts by ${post.posterName}",
                                { filter(post, PostFilterTarget.NAME) },
                                ReaderIcons.Hide,
                            ),
                        )
                    }
                    if (!post.posterTripcode.isNullOrBlank()) {
                        add(
                            NextSheetAction("Hide posts by ${post.posterTripcode}", {
                                filter(post, PostFilterTarget.TRIPCODE)
                            }, ReaderIcons.Hide),
                        )
                    }
                    if (!post.posterCountry.isNullOrBlank()) {
                        add(
                            NextSheetAction("Hide posts from ${post.posterCountry}", {
                                filter(post, PostFilterTarget.COUNTRY)
                            }, ReaderIcons.Hide),
                        )
                    }
                }
            }
        if (actions.isNotEmpty()) OrbinActionSheet(post.number, actions, onDismiss = { postMenu = null })
    }
}

@Composable
private fun ThreadJumpPills(
    newLabel: String?,
    onNew: (() -> Unit)?,
    backLabel: String?,
    onBackTo: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val hasNewPill = newLabel != null && onNew != null
    val hasBackPill = backLabel != null && onBackTo != null
    if (!hasNewPill && !hasBackPill) return
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                    ),
                ).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (backLabel != null && onBackTo != null) {
            OrbinPill(backLabel, Modifier.testTag(ThreadTags.BACK_PILL), PillVariant.Filled, onClick = onBackTo)
        }
        if (newLabel != null && onNew != null) {
            OrbinPill(newLabel, Modifier.testTag(ThreadTags.NEW_PILL), PillVariant.Filled, onClick = onNew)
        }
    }
}

const val MAX_REPLY_DEPTH = 3

@Composable
private fun threadActions(
    watching: Boolean,
    layout: ThreadLayout,
    onWatch: (() -> Unit)?,
    onLayoutChange: ((ThreadLayout) -> Unit)?,
    onDownloadAll: (() -> Unit)?,
): List<ReaderAction> =
    buildList {
        if (onLayoutChange != null) {
            val target =
                when (layout) {
                    ThreadLayout.POSTS -> ThreadLayout.FILES
                    ThreadLayout.FILES -> ThreadLayout.CARDS
                    ThreadLayout.CARDS -> ThreadLayout.POSTS
                }
            // The icon shows the layout on screen now; the spoken action names the one a tap switches to.
            val icon =
                when (layout) {
                    ThreadLayout.FILES -> ReaderIcons.Album
                    ThreadLayout.CARDS -> Icons.Filled.ViewAgenda
                    ThreadLayout.POSTS -> Icons.AutoMirrored.Filled.ViewList
                }
            add(
                ReaderAction(
                    icon = icon,
                    label = stringResource(threadLayoutLabel(target)),
                    onClick = { onLayoutChange(target) },
                    state = stringResource(threadLayoutLabel(layout)),
                ),
            )
        }
        if (onWatch != null) {
            add(
                ReaderAction(
                    icon = if (watching) ReaderIcons.SavedFilled else ReaderIcons.Saved,
                    label =
                        stringResource(
                            if (watching) Res.string.next_thread_watching else Res.string.next_thread_watch,
                        ),
                    onClick = onWatch,
                ),
            )
        }
        if (onDownloadAll != null) {
            add(
                ReaderAction(
                    icon = ReaderIcons.Download,
                    label = stringResource(Res.string.next_thread_download_title),
                    onClick = onDownloadAll,
                ),
            )
        }
    }

private fun threadLayoutLabel(layout: ThreadLayout) =
    when (layout) {
        ThreadLayout.POSTS -> Res.string.next_thread_posts
        ThreadLayout.FILES -> Res.string.next_thread_files
        ThreadLayout.CARDS -> Res.string.reader_view_card
    }

@Composable
private fun threadOverflow(
    subject: String,
    showingPosts: Boolean,
    layout: ThreadLayout,
    onLayoutChange: ((ThreadLayout) -> Unit)?,
    onReload: (() -> Unit)?,
    saved: Boolean,
    watching: Boolean,
    onToggleSaved: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onDownloadAll: (() -> Unit)?,
    onSavePage: (() -> Unit)?,
    onOpenBrowser: (() -> Unit)?,
): List<ReaderMenuItem> =
    buildList {
        if (onLayoutChange != null) {
            ThreadLayout.entries.filter { it != layout }.forEach { target ->
                val icon =
                    when (target) {
                        ThreadLayout.POSTS -> ReaderIcons.Chat
                        ThreadLayout.FILES -> ReaderIcons.Album
                        ThreadLayout.CARDS -> ReaderIcons.Boards
                    }
                add(ReaderMenuItem(stringResource(threadLayoutLabel(target)), icon = icon) { onLayoutChange(target) })
            }
        }
        if (onToggleSaved != null) {
            when {
                // A watched thread stays saved: say why rather than offer a control that does nothing.
                saved && watching ->
                    add(
                        ReaderMenuItem(
                            label = stringResource(Res.string.reader_feed_saved_while_watching, subject),
                            enabled = false,
                            icon = ReaderIcons.SavedFilled,
                            onClick = {},
                        ),
                    )
                saved ->
                    add(
                        ReaderMenuItem(
                            stringResource(Res.string.reader_feed_unsave, subject),
                            icon = ReaderIcons.SavedFilled,
                            onClick = onToggleSaved,
                        ),
                    )
                else ->
                    add(
                        ReaderMenuItem(
                            stringResource(Res.string.reader_feed_save, subject),
                            icon = ReaderIcons.Saved,
                            onClick = onToggleSaved,
                        ),
                    )
            }
        }
        if (onShare != null) {
            add(
                ReaderMenuItem(
                    stringResource(Res.string.reader_thread_share),
                    icon = ReaderIcons.Share,
                    onClick = onShare,
                ),
            )
        }
        if (onReload != null) {
            add(
                ReaderMenuItem(
                    stringResource(Res.string.reader_reload),
                    icon = ReaderIcons.Refresh,
                    onClick = onReload,
                ),
            )
        }
        if (onOpenBrowser != null) {
            add(
                ReaderMenuItem(
                    stringResource(Res.string.reader_open_browser),
                    icon = ReaderIcons.ShareForward,
                    onClick = onOpenBrowser,
                ),
            )
        }
        if (!showingPosts) {
            if (onDownloadAll != null) {
                add(
                    ReaderMenuItem(
                        stringResource(Res.string.next_thread_download_all),
                        icon = ReaderIcons.Download,
                        onClick = onDownloadAll,
                    ),
                )
            }
            if (onSavePage != null) {
                add(
                    ReaderMenuItem(
                        stringResource(Res.string.next_thread_save_page),
                        icon = ReaderIcons.Saved,
                        onClick = onSavePage,
                    ),
                )
            }
        }
    }

/**
 * A post: the shared cell of docs/design-system. A reply is indented one `space.gap` for each
 * level below the post it answers, to [MAX_REPLY_DEPTH] levels.
 */
@Composable
private fun PostCell(
    post: Post,
    original: Boolean,
    collapsed: Boolean,
    wideWindow: Boolean,
    cardImageHeight: Dp?,
    inlineImageHeight: Dp,
    onToggleCollapse: ((Post) -> Unit)?,
    onClick: ((Post) -> Unit)?,
    onMenu: ((Post) -> Unit)?,
    onReveal: ((Post) -> Unit)?,
    onShowId: ((Post) -> Unit)?,
    onReplies: ((Post) -> Unit)?,
    unreadReplies: Int,
    onReference: ((String) -> Unit)?,
    body: (@Composable (Post) -> Unit)?,
    media: (@Composable (Post, Modifier) -> Unit)?,
) {
    val colors = reader
    val indent = ReaderSpace.gap * post.depth.coerceIn(0, MAX_REPLY_DEPTH)
    val showThumbnail = post.hasMedia && !collapsed
    val bodyStyle = if (wideWindow) OrbinType.postBodyWide.withReaderFont() else OrbinType.postBody.withReaderFont()
    val detailStyle =
        if (wideWindow) {
            OrbinType.postDetailsWide.withReaderFont()
        } else {
            OrbinType.postDetails
                .withReaderFont()
        }
    val hasClickAction = onClick != null || onMenu != null || onToggleCollapse != null
    val hasPlaceholderAction = post.hiddenReason != null || post.preview
    val clickModifier =
        if (hasClickAction || hasPlaceholderAction) {
            Modifier.nextClickable(role = null, onLongClick = onMenu?.let { { it(post) } }) {
                when {
                    post.hiddenReason != null -> onReveal?.invoke(post)
                    post.preview -> onReference?.invoke(post.id)
                    onClick != null -> onClick(post)
                    else -> onToggleCollapse?.invoke(post)
                }
            }
        } else {
            Modifier
        }
    if (post.hiddenReason != null || post.preview) {
        Column(
            Modifier
                .testTag(ThreadTags.POST)
                .fillMaxWidth()
                .then(
                    clickModifier,
                ).padding(start = 14.dp + indent, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(post.number, style = detailStyle, color = colors.inkMuted)
            Text(
                post.hiddenReason ?: "Reply shown in another branch",
                style = bodyStyle,
                color = colors.inkMuted,
                modifier = Modifier.testTag("thread-placeholder:${post.branchPath}"),
            )
            if (post.hiddenReason != null && onReveal != null) OrbinTextButton("Reveal post", { onReveal(post) })
            if (post.preview && onReference != null) OrbinTextButton("Go to full post", { onReference(post.id) })
            if (post.replies > 0) Text("${post.replies} replies", style = detailStyle, color = colors.accent)
            if (unreadReplies > 0) Text("$unreadReplies unread replies", style = detailStyle, color = colors.accent)
        }
        return
    }
    CompositionLocalProvider(
        LocalReaderPostBodyStyle provides bodyStyle,
        LocalTextStyle provides bodyStyle,
    ) {
        if (cardImageHeight != null) {
            OrbinCard(
                modifier =
                    Modifier
                        .padding(horizontal = ORBIN_CATALOG_GUTTER)
                        .testTag(ThreadTags.POST)
                        .fillMaxWidth()
                        .then(clickModifier)
                        .semantics(mergeDescendants = true) {},
                elevated = false,
                containerColor = if (post.unseen) colors.accent.copy(alpha = 0.08f) else null,
                shape = RoundedCornerShape(OrbinRadius.card),
            ) {
                Column(
                    Modifier
                        .testTag(ThreadTags.CARD)
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PostHeader(post, original, collapsed, onToggleCollapse, detailStyle, onShowId)
                    if (collapsed &&
                        unreadReplies > 0
                    ) {
                        Text("$unreadReplies unread replies", style = detailStyle, color = colors.accent)
                    }
                    if (!collapsed) {
                        PostBody(post, body)
                        if (showThumbnail) {
                            PostThumbnail(post, media, Modifier.fillMaxWidth().height(cardImageHeight))
                        }
                        PostReplies(post, onReference)
                        if (post.descendantIds.isNotEmpty() &&
                            onReplies != null
                        ) {
                            OrbinTextButton("${post.replies} replies", { onReplies(post) })
                        }
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .testTag(ThreadTags.POST)
                    .fillMaxWidth()
                    .background(if (post.unseen) colors.accent.copy(alpha = 0.08f) else colors.background)
                    .then(clickModifier)
                    .semantics(mergeDescendants = true) {},
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            if (post.depth > 0) {
                                val leading = (indent + 8.dp).toPx()
                                val barX = if (layoutDirection == LayoutDirection.Ltr) leading else size.width - leading
                                drawLine(
                                    color = colors.quoteLink.copy(alpha = 0.55f),
                                    start = Offset(barX, 11.dp.toPx()),
                                    end = Offset(barX, (size.height - 12.dp.toPx()).coerceAtLeast(11.dp.toPx())),
                                    strokeWidth = 2.dp.toPx(),
                                )
                            }
                        }.padding(start = 14.dp + indent, end = 14.dp, top = 11.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PostHeader(post, original, collapsed, onToggleCollapse, detailStyle, onShowId)
                    if (collapsed &&
                        unreadReplies > 0
                    ) {
                        Text("$unreadReplies unread replies", style = detailStyle, color = colors.accent)
                    }
                    if (!collapsed) {
                        PostBody(post, body)
                        if (showThumbnail) {
                            PostThumbnail(post, media, Modifier.fillMaxWidth().height(inlineImageHeight))
                        }
                        PostReplies(post, onReference)
                        if (post.descendantIds.isNotEmpty() &&
                            onReplies != null
                        ) {
                            OrbinTextButton("${post.replies} replies", { onReplies(post) })
                        }
                    }
                }
                ReaderHairline(Modifier.padding(start = 14.dp + indent, end = 14.dp))
            }
        }
    }
}

/** Inline previews stay finite even when a host offers an unbounded or very tall pane. */
internal fun readerPostMediaHeight(
    width: Dp,
    viewportHeight: Dp,
): Dp {
    val boundedWidth = if (width.isFinite && width > 0.dp) width else 360.dp
    val viewportLimit =
        if (viewportHeight.isFinite && viewportHeight > 0.dp) {
            viewportHeight * INLINE_MEDIA_HEIGHT_FRACTION
        } else {
            INLINE_MEDIA_MAX_HEIGHT_DP.dp
        }
    return minOf(boundedWidth * INLINE_MEDIA_HEIGHT_FRACTION, viewportLimit, INLINE_MEDIA_MAX_HEIGHT_DP.dp)
        .coerceAtLeast(1.dp)
}

private const val INLINE_MEDIA_HEIGHT_FRACTION = 0.75f
private const val INLINE_MEDIA_MAX_HEIGHT_DP = 400

@Composable
private fun PostThumbnail(
    post: Post,
    media: (@Composable (Post, Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = reader
    var revealed by rememberSaveable(post.stableKey, post.imageSpoiler) { mutableStateOf(false) }
    val covered = post.imageSpoiler && !revealed
    Box(
        modifier
            .testTag(ThreadTags.THUMBNAIL)
            .clip(RoundedCornerShape(OrbinRadius.compactCard))
            .background(colors.surfaceVariant),
    ) {
        if (covered) {
            ReaderSpoilerCover(onReveal = { revealed = true }, modifier = Modifier.fillMaxSize())
        } else {
            media?.invoke(post, Modifier.fillMaxSize())
        }
        if (post.mediaCount > 1) {
            val badgeShape = RoundedCornerShape(topStart = 6.dp)
            Text(
                text = stringResource(Res.string.reader_media_more, post.mediaCount - 1),
                style = OrbinType.meta.withReaderFont().copy(textDirection = TextDirection.Ltr),
                color = colors.ink,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .clip(badgeShape)
                        .background(colors.surfaceVariant)
                        .border(ReaderSize.hairline, colors.hairline, badgeShape)
                        .padding(horizontal = ReaderSpace.line),
            )
        }
    }
}

@Composable
private fun PostHeader(
    post: Post,
    original: Boolean,
    collapsed: Boolean,
    onToggleCollapse: ((Post) -> Unit)?,
    detailStyle: TextStyle,
    onShowId: ((Post) -> Unit)?,
) {
    val colors = reader
    val author =
        if (listOf(
                post.posterName,
                post.posterTripcode,
                post.posterCountry,
                post.posterCapcode,
            ).any { !it.isNullOrBlank() }
        ) {
            listOfNotNull(
                post.posterName,
                post.posterTripcode,
                post.posterCapcode,
                post.posterCountry,
            ).filter(String::isNotBlank).joinToString(" · ")
        } else {
            post.author
        }
    val expandLabel = stringResource(Res.string.next_post_expand)
    val collapseLabel = stringResource(Res.string.next_post_collapse)
    val collapsedState = stringResource(Res.string.next_post_collapsed_state)
    val expandedState = stringResource(Res.string.next_post_expanded_state)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (onToggleCollapse != null) {
                        Modifier
                            .nextClickable(
                                onClickLabel = if (collapsed) expandLabel else collapseLabel,
                                onClick = { onToggleCollapse(post) },
                            ).semantics { stateDescription = if (collapsed) collapsedState else expandedState }
                    } else {
                        Modifier
                    },
                ),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ReaderSpace.line), verticalArrangement = Arrangement.Top) {
            if (author.isNotBlank()) {
                Text(
                    text = author,
                    style = detailStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                )
            }
            if (!post.posterId.isNullOrBlank() && onShowId != null) {
                Text(
                    "ID:${post.posterId}",
                    style = detailStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accent,
                    modifier =
                        Modifier
                            .heightIn(min = MIN_TOUCH_TARGET)
                            .nextClickable(role = Role.Button) { onShowId(post) }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                )
            }
            Text(text = post.number, style = detailStyle, color = colors.inkMuted)
            if (post.time.isNotBlank()) {
                Text(text = post.time, style = detailStyle, color = colors.inkMuted)
            }
            if (original) {
                Text(
                    text = stringResource(Res.string.reader_thread_op),
                    style = detailStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accent,
                    modifier =
                        Modifier
                            .background(colors.accent.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (post.subject.isNotBlank()) {
            Text(
                post.subject,
                style = LocalReaderPostBodyStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = colors.subject,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (post.fileLabel.isNotBlank()) {
            Text(
                text = post.fileLabel,
                style = detailStyle.copy(textDecoration = TextDecoration.Underline),
                color = colors.inkMuted,
            )
        }
        if (collapsed) {
            Text(
                text = stringResource(Res.string.next_post_collapsed),
                style = detailStyle,
                color = colors.inkMuted,
            )
        }
    }
}

@Composable
private fun PostBody(
    post: Post,
    body: (@Composable (Post) -> Unit)?,
) {
    val colors = reader
    var spoilerRevealed by rememberSaveable(post.id) { mutableStateOf(false) }
    if (post.spoiler && !spoilerRevealed) {
        val revealLabel = stringResource(Res.string.next_spoiler_reveal)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ReaderSize.touchTarget)
                    .clip(RoundedCornerShape(OrbinRadius.small))
                    .background(colors.surfaceVariant)
                    .nextClickable(onClickLabel = revealLabel, onClick = { spoilerRevealed = true })
                    .padding(horizontal = ReaderSpace.gap),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text = revealLabel, style = OrbinType.label.withReaderFont(), color = colors.ink)
        }
    } else if (body != null) {
        ProvideTextStyle(LocalReaderPostBodyStyle.current.copy(color = colors.ink)) { body(post) }
    } else {
        Text(text = post.body, style = LocalReaderPostBodyStyle.current, color = colors.ink)
    }
}

/** The posts that answer this one, as links; or their count when the host supplies no numbers. */
@Composable
private fun PostReplies(
    post: Post,
    onReference: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = reader
    if (post.replyNumbers.isNotEmpty() && onReference != null) {
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(ReaderSpace.gapSmall)) {
            post.replyNumbers.forEach { number ->
                Box(
                    modifier =
                        Modifier
                            .heightIn(min = ReaderSize.touchTarget)
                            .widthIn(min = ReaderSize.touchTarget)
                            .nextClickable(role = Role.Button) { onReference(number) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.reader_reply_link, number),
                        style =
                            LocalReaderPostBodyStyle.current.copy(
                                textDecoration = TextDecoration.Underline,
                                textDirection = TextDirection.Ltr,
                            ),
                        color = colors.quoteLink,
                    )
                }
            }
        }
    } else if (post.replies > 0) {
        Text(
            text = pluralStringResource(Res.plurals.next_row_replies, post.replies, post.replies),
            style = LocalReaderPostBodyStyle.current,
            color = colors.inkMuted,
            modifier = modifier,
        )
    }
}

/** Marks the first post the reader has not seen: an accent line with the label "New" above it. */
@Composable
private fun UnreadMarker() {
    val colors = reader
    Column(Modifier.fillMaxWidth().testTag(ThreadTags.UNREAD)) {
        Text(
            text = stringResource(Res.string.reader_new),
            style = OrbinType.meta.withReaderFont(),
            color = colors.accent,
            modifier = Modifier.padding(start = ReaderSpace.gutter, top = ReaderSpace.line, bottom = ReaderSpace.line),
        )
        Box(Modifier.fillMaxWidth().height(ReaderSize.hairline).background(colors.accent))
    }
}

/** The thread's footer: how many replies and files it holds. */
@Composable
private fun ThreadStatus(
    replies: Int,
    fileCount: Int,
    statusText: String?,
) {
    val style = OrbinType.postBody.withReaderFont().copy(fontWeight = FontWeight.Medium)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ReaderSize.threadStatus)
                .testTag(ThreadTags.STATUS)
                .padding(ReaderSpace.postInset),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalTextStyle provides style) {
            Text(
                text =
                    statusText ?: stringResource(
                        Res.string.next_row_counts,
                        pluralStringResource(Res.plurals.next_row_replies, replies, replies),
                        pluralStringResource(Res.plurals.next_row_files, fileCount, fileCount),
                    ),
                style = style,
                color = reader.inkMuted,
            )
        }
    }
}

/** Clover's album cells: width plus a 32dp metadata strip, with one-dp cell margins. */
@Composable
private fun FileRow(
    cells: List<MediaCell>,
    columns: Int,
    onOpenFile: ((MediaCell) -> Unit)?,
    fileTile: (@Composable (MediaCell, Modifier) -> Unit)?,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(ReaderSpace.albumCellMargin),
        horizontalArrangement = Arrangement.spacedBy(ReaderSpace.albumCellMargin * 2),
    ) {
        cells.forEach { cell ->
            ReaderAlbumCell(
                cell = cell,
                modifier = Modifier.weight(1f),
                onOpen = onOpenFile,
                tile = fileTile,
                cellTag = ThreadTags.FILE,
                detailsTag = ThreadTags.FILE_DETAILS,
            )
        }
        // Empty cells keep a short last row's tiles the same size as the rows above.
        repeat(columns - cells.size) { Box(Modifier.weight(1f)) }
    }
}

// The list's first item is always the header, so post n is list item n + HEADER_ITEMS.
private const val HEADER_ITEMS = 1
private const val THREAD_HEADER_KEY = "thread-header"
private const val THREAD_STATUS_KEY = "thread-status"
