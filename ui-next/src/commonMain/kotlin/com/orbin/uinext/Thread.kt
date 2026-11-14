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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

enum class ThreadLayout {
    POSTS,
    FILES,
    CARDS,
}

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
 * A fixed app bar over Clover's compact post geometry, retaining Orbin's reader controls.
 *
 * The list always starts with one header item (the [subtitle] line, or nothing), so the post at
 * index n of [posts] is list item n + 1. Hosts that save and restore the reading position rely
 * on that.
 *
 * [wideWindow] is whether the window, not this pane, is wide: it selects the larger thumbnail.
 * [media] draws a post's thumbnail inside the square this screen sizes and clips; it should fill
 * the modifier it is given and crop.
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
) {
    val colors = reader
    val state = listState ?: rememberLazyListState()
    val filesState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showingPosts = layout != ThreadLayout.FILES
    val cardMode = layout == ThreadLayout.CARDS

    // Expands a collapsed post before bringing it into view, so the reader lands on its text.
    val reveal: (String) -> Unit = { id ->
        val target = posts.indexOfFirst { it.id == id }
        if (target >= 0) {
            if (posts[target].id in collapsed) onToggleCollapse?.invoke(posts[target])
            scope.launch { state.animateScrollToItem(target + HEADER_ITEMS) }
        }
    }
    var returnToId by remember { mutableStateOf<String?>(null) }
    var returnToNumber by remember { mutableStateOf<String?>(null) }
    val openReference: ((String) -> Unit)? =
        onPostReference?.let { outer ->
            { id ->
                val visible = posts.getOrNull((state.firstVisibleItemIndex - HEADER_ITEMS).coerceAtLeast(0))
                if (visible != null && visible.id != id) {
                    returnToId = visible.id
                    returnToNumber = visible.number
                }
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
                    reveal(id)
                    returnToId = null
                    returnToNumber = null
                }
            }
    LaunchedEffect(scrollToPostId, posts, layout) {
        if (!showingPosts) return@LaunchedEffect
        val target = posts.indexOfFirst { it.id == scrollToPostId }
        if (scrollToPostId != null && target >= 0) {
            if (posts[target].id in collapsed) onToggleCollapse?.invoke(posts[target])
            state.animateScrollToItem(target + HEADER_ITEMS)
            onScrollConsumed()
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(if (cardMode && !next.dark) colors.surfaceVariant else colors.background)
                .semantics { paneTitle = subject },
    ) {
        ReaderAppBar(
            title = subject,
            subtitle = listOf(sourceLabel(siteName), board).filter(String::isNotBlank).joinToString(" · "),
            navigation = if (onBack != null) ReaderNavigation.BACK else ReaderNavigation.NONE,
            onNavigation = onBack ?: {},
            onTitleTap = { scope.launch { state.animateScrollToItem(0) } },
            titleTapLabel = stringResource(Res.string.next_thread_scroll_top),
            actions = threadActions(watching, layout, onWatch, onLayoutChange, onDownloadAll),
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
            firstUnreadPostId?.let { id -> posts.indexOfFirst { it.id == id } }?.takeIf { it > 0 }
        val newCount = unreadFrom?.let { posts.size - it }
        ReaderFastScrollThumb(state = state, enabled = showingPosts, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                val fileColumns = catalogGridColumns(maxWidth)
                val fileRows = remember(files, fileColumns) { files.chunked(fileColumns) }
                val cardImageHeight = orbinCatalogCardSpec(maxWidth).imageHeight
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
                        itemsIndexed(posts, key = {
                            _,
                            post,
                            ->
                            post.stableKey
                        }, contentType = { _, _ -> layout }) {
                            index,
                            post,
                            ->
                            Column(Modifier.fillMaxWidth()) {
                                if (index > 0 && post.id == firstUnreadPostId) UnreadMarker()
                                PostCell(
                                    post = post,
                                    original = index == 0,
                                    collapsed = post.id in collapsed,
                                    wideWindow = wideWindow,
                                    cardImageHeight = if (cardMode) cardImageHeight else null,
                                    onToggleCollapse = onToggleCollapse,
                                    onClick = onPostClick,
                                    onReference = openReference,
                                    body = body,
                                    media = media,
                                )
                            }
                        }
                        item(key = THREAD_STATUS_KEY, contentType = THREAD_STATUS_KEY) {
                            ThreadStatus(
                                replies = maxOf(0, posts.size - 1),
                                fileCount = posts.sumOf { it.mediaCount },
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
            ThreadPill(label = backLabel, tag = ThreadTags.BACK_PILL, onClick = onBackTo)
        }
        if (newLabel != null && onNew != null) {
            ThreadPill(label = newLabel, tag = ThreadTags.NEW_PILL, onClick = onNew)
        }
    }
}

@Composable
private fun ThreadPill(
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    Text(
        label,
        style = OrbinType.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = reader.onAccent,
        maxLines = 1,
        modifier =
            Modifier
                .testTag(tag)
                .heightIn(min = MIN_TOUCH_TARGET)
                .clip(RoundedCornerShape(OrbinRadius.pill))
                .background(reader.accent)
                .nextClickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { contentDescription = label },
    )
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
            val icon =
                when (target) {
                    ThreadLayout.FILES -> ReaderIcons.Album
                    ThreadLayout.CARDS -> Icons.Filled.ViewAgenda
                    ThreadLayout.POSTS -> Icons.AutoMirrored.Filled.ViewList
                }
            add(
                ReaderAction(
                    icon = icon,
                    label = stringResource(threadLayoutLabel(target)),
                    onClick = { onLayoutChange(target) },
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
            add(ReaderMenuItem(stringResource(Res.string.reader_thread_share), icon = ReaderIcons.Share, onClick = onShare))
        }
        if (onReload != null) {
            add(ReaderMenuItem(stringResource(Res.string.reader_reload), icon = ReaderIcons.Refresh, onClick = onReload))
        }
        if (onOpenBrowser != null) {
            add(ReaderMenuItem(stringResource(Res.string.reader_open_browser), icon = ReaderIcons.ShareForward, onClick = onOpenBrowser))
        }
        if (!showingPosts) {
            if (onDownloadAll != null) {
                add(ReaderMenuItem(stringResource(Res.string.next_thread_download_all), icon = ReaderIcons.Download, onClick = onDownloadAll))
            }
            if (onSavePage != null) {
                add(ReaderMenuItem(stringResource(Res.string.next_thread_save_page), icon = ReaderIcons.Saved, onClick = onSavePage))
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
    onToggleCollapse: ((Post) -> Unit)?,
    onClick: ((Post) -> Unit)?,
    onReference: ((String) -> Unit)?,
    body: (@Composable (Post) -> Unit)?,
    media: (@Composable (Post, Modifier) -> Unit)?,
) {
    val colors = reader
    val indent = ReaderSpace.gap * post.depth.coerceIn(0, MAX_REPLY_DEPTH)
    val showThumbnail = post.hasMedia && !collapsed
    val inset = readerPostInset(wideWindow)
    val bodyStyle = if (wideWindow) OrbinType.postBodyWide.withReaderFont() else OrbinType.postBody.withReaderFont()
    val detailStyle =
        if (wideWindow) {
            OrbinType.postDetailsWide.withReaderFont()
        } else {
            OrbinType.postDetails
                .withReaderFont()
        }
    val clickModifier = if (onClick != null) Modifier.nextClickable(role = null) { onClick(post) } else Modifier
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
                shape = RoundedCornerShape(OrbinRadius.card),
            ) {
                Column(
                    Modifier
                        .testTag(ThreadTags.CARD)
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PostHeader(post, original, collapsed, onToggleCollapse, detailStyle)
                    if (showThumbnail) {
                        PostThumbnail(post, media, Modifier.fillMaxWidth().height(cardImageHeight))
                    }
                    if (!collapsed) {
                        PostBody(post, body)
                        PostReplies(post, onReference)
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .testTag(ThreadTags.POST)
                    .fillMaxWidth()
                    .background(colors.background)
                    .then(clickModifier)
                    .semantics(mergeDescendants = true) {},
            ) {
                ReaderPostLayout(
                    modifier =
                        if (post.depth > 0) {
                            Modifier.drawBehind {
                                val barX = indent.toPx() - 2.dp.toPx()
                                drawLine(
                                    color = colors.quoteLink.copy(alpha = 0.55f),
                                    start = Offset(barX.coerceAtLeast(0f), 0f),
                                    end = Offset(barX.coerceAtLeast(0f), size.height),
                                    strokeWidth = 2.dp.toPx(),
                                )
                            }
                        } else {
                            Modifier
                        },
                    wideWindow = wideWindow,
                    indent = indent,
                    thumbnail =
                        if (showThumbnail) {
                            { thumbnailModifier -> PostThumbnail(post, media, thumbnailModifier) }
                        } else {
                            null
                        },
                ) { contentModifier ->
                    Column(contentModifier) {
                        Box(Modifier.padding(start = inset, end = inset, top = inset)) {
                            PostHeader(post, original, collapsed, onToggleCollapse, detailStyle)
                        }
                        if (!collapsed) {
                            Box(Modifier.padding(start = inset, end = inset, top = inset)) {
                                PostBody(post, body)
                            }
                            PostReplies(post, onReference, Modifier.padding(start = inset, end = inset, top = inset))
                        }
                        Box(Modifier.height(inset))
                    }
                }
                ReaderHairline(Modifier.padding(start = indent + inset, end = inset))
            }
        }
    }
}

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
            .clip(RoundedCornerShape(OrbinRadius.compact))
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
) {
    val colors = reader
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
        if (post.subject.isNotBlank()) {
            Text(
                post.subject,
                style = LocalReaderPostBodyStyle.current.copy(fontWeight = FontWeight.SemiBold),
                color = colors.subject,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ReaderSpace.line), verticalArrangement = Arrangement.Top) {
            if (post.author.isNotBlank() && post.author != "Anonymous") {
                Text(
                    text = post.author,
                    style = LocalReaderPostBodyStyle.current.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.author,
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
                )
            }
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
