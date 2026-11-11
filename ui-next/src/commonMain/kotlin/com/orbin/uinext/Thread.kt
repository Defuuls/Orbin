package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
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
import com.orbin.uinext.resources.next_thread_download_all
import com.orbin.uinext.resources.next_thread_files
import com.orbin.uinext.resources.next_thread_jump_bottom
import com.orbin.uinext.resources.next_thread_jump_top
import com.orbin.uinext.resources.next_thread_posts
import com.orbin.uinext.resources.next_thread_save_page
import com.orbin.uinext.resources.next_thread_watch
import com.orbin.uinext.resources.next_thread_watching
import com.orbin.uinext.resources.reader_first_new
import com.orbin.uinext.resources.reader_media_more
import com.orbin.uinext.resources.reader_new
import com.orbin.uinext.resources.reader_open_browser
import com.orbin.uinext.resources.reader_reload
import com.orbin.uinext.resources.reader_reply_link
import com.orbin.uinext.resources.reader_reveal_spoiler
import com.orbin.uinext.resources.social_feed_save
import com.orbin.uinext.resources.social_feed_saved_while_watching
import com.orbin.uinext.resources.social_feed_unsave
import com.orbin.uinext.resources.social_thread_anonymous
import com.orbin.uinext.resources.social_thread_op
import com.orbin.uinext.resources.social_thread_share
import com.orbin.uinext.tokens.ReaderRadius
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import com.orbin.uinext.tokens.ReaderType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

enum class ThreadLayout {
    POSTS,
    FILES,
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
)

object ThreadTags {
    const val LIST = "thread-list"
    const val POST = "thread-post"
    const val THUMBNAIL = "thread-post-thumbnail"
    const val UNREAD = "thread-unread-marker"
    const val STATUS = "thread-status"
    const val FILE = "thread-file"
    const val PROGRESS = "thread-progress"
}

/**
 * A thread, as docs/design-system specifies it: a fixed app bar over posts drawn with the cell
 * the catalog's rows use.
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
    val showingPosts = layout == ThreadLayout.POSTS

    // Expands a collapsed post before bringing it into view, so the reader lands on its text.
    val reveal: (String) -> Unit = { id ->
        val target = posts.indexOfFirst { it.id == id }
        if (target >= 0) {
            if (posts[target].id in collapsed) onToggleCollapse?.invoke(posts[target])
            scope.launch { state.animateScrollToItem(target + HEADER_ITEMS) }
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
                .background(colors.background)
                .semantics { paneTitle = subject },
    ) {
        ReaderAppBar(
            title = subject,
            subtitle = listOf(sourceLabel(siteName), board).filter(String::isNotBlank).joinToString(" · "),
            navigation = if (onBack != null) ReaderNavigation.BACK else ReaderNavigation.NONE,
            onNavigation = onBack ?: {},
            actions = threadActions(watching, onWatch, onReload),
            overflow =
                threadOverflow(
                    subject = subject,
                    showingPosts = showingPosts,
                    onLayoutChange = onLayoutChange,
                    saved = saved,
                    watching = watching,
                    onToggleSaved = onToggleSaved,
                    onShare = onShare,
                    canJump = showingPosts && posts.size > 1,
                    onTop = { scope.launch { state.animateScrollToItem(0) } },
                    onFirstNew = firstUnreadPostId?.let { id -> { reveal(id) } },
                    onBottom = { scope.launch { state.animateScrollToItem(posts.size + HEADER_ITEMS) } },
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
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            val fileColumns = catalogGridColumns(maxWidth)
            val fileRows = remember(files, fileColumns) { files.chunked(fileColumns) }
            LazyColumn(
                state = if (showingPosts) state else filesState,
                modifier = Modifier.fillMaxSize().testTag(ThreadTags.LIST),
                contentPadding = PaddingValues(bottom = bottomInset()),
            ) {
                item(key = THREAD_HEADER_KEY, contentType = THREAD_HEADER_KEY) {
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = ReaderType.meta,
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
                    }, contentType = { _, _ -> ThreadLayout.POSTS }) {
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
                                onToggleCollapse = onToggleCollapse,
                                onClick = onPostClick,
                                onReference = onPostReference,
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
    }
}

const val MAX_REPLY_DEPTH = 3

@Composable
private fun threadActions(
    watching: Boolean,
    onWatch: (() -> Unit)?,
    onReload: (() -> Unit)?,
): List<ReaderAction> =
    buildList {
        if (onReload != null) add(ReaderAction(ReaderIcons.Refresh, stringResource(Res.string.reader_reload), onReload))
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
    }

@Composable
private fun threadOverflow(
    subject: String,
    showingPosts: Boolean,
    onLayoutChange: ((ThreadLayout) -> Unit)?,
    saved: Boolean,
    watching: Boolean,
    onToggleSaved: (() -> Unit)?,
    onShare: (() -> Unit)?,
    canJump: Boolean,
    onTop: () -> Unit,
    onFirstNew: (() -> Unit)?,
    onBottom: () -> Unit,
    onDownloadAll: (() -> Unit)?,
    onSavePage: (() -> Unit)?,
    onOpenBrowser: (() -> Unit)?,
): List<ReaderMenuItem> =
    buildList {
        if (onLayoutChange != null) {
            if (showingPosts) {
                add(ReaderMenuItem(stringResource(Res.string.next_thread_files)) { onLayoutChange(ThreadLayout.FILES) })
            } else {
                add(ReaderMenuItem(stringResource(Res.string.next_thread_posts)) { onLayoutChange(ThreadLayout.POSTS) })
            }
        }
        if (onToggleSaved != null) {
            when {
                // A watched thread stays saved: say why rather than offer a control that does nothing.
                saved && watching ->
                    add(
                        ReaderMenuItem(
                            label = stringResource(Res.string.social_feed_saved_while_watching, subject),
                            enabled = false,
                            onClick = {},
                        ),
                    )
                saved ->
                    add(
                        ReaderMenuItem(stringResource(Res.string.social_feed_unsave, subject), onClick = onToggleSaved),
                    )
                else ->
                    add(
                        ReaderMenuItem(stringResource(Res.string.social_feed_save, subject), onClick = onToggleSaved),
                    )
            }
        }
        if (onShare != null) add(ReaderMenuItem(stringResource(Res.string.social_thread_share), onClick = onShare))
        if (onOpenBrowser != null) {
            add(ReaderMenuItem(stringResource(Res.string.reader_open_browser), onClick = onOpenBrowser))
        }
        if (canJump) {
            add(ReaderMenuItem(stringResource(Res.string.next_thread_jump_top), onClick = onTop))
            if (onFirstNew !=
                null
            ) {
                add(ReaderMenuItem(stringResource(Res.string.reader_first_new), onClick = onFirstNew))
            }
            add(ReaderMenuItem(stringResource(Res.string.next_thread_jump_bottom), onClick = onBottom))
        }
        if (!showingPosts) {
            if (onDownloadAll != null) {
                add(ReaderMenuItem(stringResource(Res.string.next_thread_download_all), onClick = onDownloadAll))
            }
            if (onSavePage != null) {
                add(ReaderMenuItem(stringResource(Res.string.next_thread_save_page), onClick = onSavePage))
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
    onToggleCollapse: ((Post) -> Unit)?,
    onClick: ((Post) -> Unit)?,
    onReference: ((String) -> Unit)?,
    body: (@Composable (Post) -> Unit)?,
    media: (@Composable (Post, Modifier) -> Unit)?,
) {
    val colors = reader
    val indent = ReaderSpace.gap * post.depth.coerceIn(0, MAX_REPLY_DEPTH)
    val showThumbnail = post.hasMedia && !collapsed
    Column(
        Modifier
            .testTag(ThreadTags.POST)
            .fillMaxWidth()
            .background(colors.background)
            .then(if (onClick != null) Modifier.nextClickable(role = null) { onClick(post) } else Modifier)
            .semantics(mergeDescendants = true) {},
    ) {
        ReaderCellLayout(
            wideWindow = wideWindow,
            verticalPadding = ReaderSpace.postPadY,
            indent = indent,
            contentMaxWidth = ReaderSize.readingMax,
            thumbnail =
                if (showThumbnail) {
                    { thumbnailModifier -> PostThumbnail(post, media, thumbnailModifier) }
                } else {
                    null
                },
        ) { contentModifier ->
            Column(contentModifier) {
                PostHeader(post, original, collapsed, onToggleCollapse)
                if (!collapsed) {
                    PostBody(post, body)
                    PostReplies(post, onReference)
                }
            }
        }
        ReaderHairline(Modifier.padding(start = indent))
    }
}

@Composable
private fun PostThumbnail(
    post: Post,
    media: (@Composable (Post, Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = reader
    var revealed by rememberSaveable(post.stableKey) { mutableStateOf(false) }
    val covered = post.imageSpoiler && !revealed
    Box(
        modifier
            .testTag(ThreadTags.THUMBNAIL)
            .clip(RoundedCornerShape(ReaderRadius.small))
            .background(colors.surfaceVariant),
    ) {
        if (covered) {
            ReaderSpoilerCover(onReveal = { revealed = true }, modifier = Modifier.fillMaxSize())
        } else {
            media?.invoke(post, Modifier.fillMaxSize())
        }
        if (post.mediaCount > 1) {
            Text(
                text = stringResource(Res.string.reader_media_more, post.mediaCount - 1),
                style = ReaderType.meta.copy(textDirection = TextDirection.Ltr),
                color = colors.ink,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .background(colors.surfaceVariant)
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
                .heightIn(min = ReaderSize.touchTarget)
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
                ).padding(bottom = ReaderSpace.gapSmall),
        verticalArrangement = Arrangement.spacedBy(ReaderSpace.line),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ReaderSpace.gapSmall)) {
            Text(
                text = post.author.ifBlank { stringResource(Res.string.social_thread_anonymous) },
                style = ReaderType.postAuthor,
                color = colors.author,
            )
            Text(text = post.number, style = ReaderType.meta, color = colors.inkMuted)
            if (original) {
                Text(
                    text = stringResource(Res.string.social_thread_op),
                    style = ReaderType.meta,
                    color = colors.inkMuted,
                )
            }
        }
        val details = listOf(post.time, post.fileLabel).filter(String::isNotBlank).joinToString(" · ")
        if (details.isNotEmpty()) {
            Text(
                text = details,
                style = ReaderType.meta,
                color = colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        if (collapsed) {
            Text(
                text = stringResource(Res.string.next_post_collapsed),
                style = ReaderType.meta,
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
                    .clip(RoundedCornerShape(ReaderRadius.small))
                    .background(colors.surfaceVariant)
                    .nextClickable(onClickLabel = revealLabel, onClick = { spoilerRevealed = true })
                    .padding(horizontal = ReaderSpace.gap),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text = revealLabel, style = ReaderType.label, color = colors.ink)
        }
    } else if (body != null) {
        ProvideTextStyle(ReaderType.body.copy(color = colors.ink)) { body(post) }
    } else {
        Text(text = post.body, style = ReaderType.body, color = colors.ink)
    }
}

/** The posts that answer this one, as links; or their count when the host supplies no numbers. */
@Composable
private fun PostReplies(
    post: Post,
    onReference: ((String) -> Unit)?,
) {
    val colors = reader
    if (post.replyNumbers.isNotEmpty() && onReference != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ReaderSpace.gapSmall)) {
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
                            ReaderType.meta.copy(
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
            style = ReaderType.meta,
            color = colors.inkMuted,
            modifier = Modifier.padding(top = ReaderSpace.gapSmall),
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
            style = ReaderType.meta,
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
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = ReaderSize.threadStatus).testTag(ThreadTags.STATUS),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text =
                statusText ?: stringResource(
                    Res.string.next_row_counts,
                    pluralStringResource(Res.plurals.next_row_replies, replies, replies),
                    pluralStringResource(Res.plurals.next_row_files, fileCount, fileCount),
                ),
            style = ReaderType.meta,
            color = reader.inkMuted,
        )
    }
}

/** One row of the Files grid: square tiles in the catalog grid's column count. */
@Composable
private fun FileRow(
    cells: List<MediaCell>,
    columns: Int,
    onOpenFile: ((MediaCell) -> Unit)?,
    fileTile: (@Composable (MediaCell, Modifier) -> Unit)?,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(
                start = ReaderSpace.gridGap,
                top = ReaderSpace.gridGap,
                end = ReaderSpace.gridGap,
            ),
        horizontalArrangement = Arrangement.spacedBy(ReaderSpace.gridGap),
    ) {
        cells.forEach { cell ->
            var revealed by rememberSaveable(cell.id) { mutableStateOf(false) }
            val covered = cell.spoiler && !revealed
            val revealLabel = stringResource(Res.string.reader_reveal_spoiler)
            Box(
                modifier =
                    Modifier
                        .testTag(ThreadTags.FILE)
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(ReaderRadius.card))
                        .background(reader.surfaceVariant)
                        .then(
                            when {
                                covered -> Modifier.nextClickable(onClickLabel = revealLabel) { revealed = true }
                                onOpenFile != null -> Modifier.nextClickable(onClick = { onOpenFile(cell) })
                                else -> Modifier
                            },
                        ),
            ) {
                if (covered) {
                    ReaderSpoilerCover(onReveal = { revealed = true }, modifier = Modifier.fillMaxSize())
                } else {
                    fileTile?.invoke(cell, Modifier.fillMaxSize())
                }
            }
        }
        // Empty cells keep a short last row's tiles the same size as the rows above.
        repeat(columns - cells.size) { Box(Modifier.weight(1f)) }
    }
}

// The list's first item is always the header, so post n is list item n + HEADER_ITEMS.
private const val HEADER_ITEMS = 1
private const val THREAD_HEADER_KEY = "thread-header"
private const val THREAD_STATUS_KEY = "thread-status"
