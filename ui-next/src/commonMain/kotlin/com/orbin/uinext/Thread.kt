package com.orbin.uinext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_post_collapse
import com.orbin.uinext.resources.next_post_collapsed
import com.orbin.uinext.resources.next_post_collapsed_state
import com.orbin.uinext.resources.next_post_expand
import com.orbin.uinext.resources.next_post_expanded_state
import com.orbin.uinext.resources.next_row_replies
import com.orbin.uinext.resources.next_spoiler_reveal
import com.orbin.uinext.resources.next_thread_download_all
import com.orbin.uinext.resources.next_thread_files
import com.orbin.uinext.resources.next_thread_jump_bottom
import com.orbin.uinext.resources.next_thread_jump_top
import com.orbin.uinext.resources.next_thread_jump_unread
import com.orbin.uinext.resources.next_thread_posts
import com.orbin.uinext.resources.next_thread_posts_count
import com.orbin.uinext.resources.next_thread_save_page
import com.orbin.uinext.resources.next_thread_share
import com.orbin.uinext.resources.next_thread_watch
import com.orbin.uinext.resources.next_thread_watching
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

enum class ThreadLayout {
    POSTS,
    FILES,
}

data class Post(
    val number: String,
    val time: String,
    val body: String,
    val depth: Int = 0,
    val hasMedia: Boolean = false,
    val replies: Int = 0,
    val spoiler: Boolean = false,
    val id: String = number,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    subject: String,
    board: String,
    posts: List<Post>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    watching: Boolean = false,
    layout: ThreadLayout = ThreadLayout.POSTS,
    onLayoutChange: (ThreadLayout) -> Unit = {},
    files: List<MediaCell> = emptyList(),
    fileColumns: Int = 3,
    onOpenFile: (MediaCell) -> Unit = {},
    fileTile: (@Composable (MediaCell, Modifier) -> Unit)? = null,
    collapsed: Set<String> = emptySet(),
    onToggleCollapse: (Post) -> Unit = {},
    onWatch: () -> Unit = {},
    onDownloadAll: () -> Unit = {},
    onSavePage: (() -> Unit)? = null,
    onShare: () -> Unit = {},
    onPostClick: (Post) -> Unit = {},
    listState: LazyListState? = null,
    scrollToPostId: String? = null,
    firstUnreadPostId: String? = null,
    onScrollConsumed: () -> Unit = {},
    body: (@Composable (Post) -> Unit)? = null,
    media: (@Composable (Post, Modifier) -> Unit)? = null,
) {
    val state = listState ?: rememberLazyListState()
    val scope = rememberCoroutineScope()
    val fileRows = remember(files, fileColumns) { files.chunked(fileColumns) }
    val showCompactTitle by remember {
        derivedStateOf {
            state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 64
        }
    }
    LaunchedEffect(scrollToPostId, posts) {
        val target = posts.indexOfFirst { it.id == scrollToPostId }
        if (scrollToPostId != null && target >= 0) {
            state.animateScrollToItem(target + 1)
            onScrollConsumed()
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = next.background,
            contentColor = next.ink,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                if (layout == ThreadLayout.POSTS && posts.size > 1) {
                    val jumpShape = RoundedCornerShape(NextRadius.tight)
                    Row(
                        modifier =
                            Modifier.padding(
                                end = 4.dp,
                                bottom = 12.dp + bottomInset(),
                            ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExtendedFloatingActionButton(
                            onClick = { scope.launch { state.animateScrollToItem(0) } },
                            shape = jumpShape,
                            containerColor = next.elevated,
                            contentColor = next.ink,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
                            modifier = Modifier.border(1.dp, next.hairline, jumpShape),
                        ) {
                            Text(
                                text = stringResource(Res.string.next_thread_jump_top),
                                style = NextType.labelMedium,
                            )
                        }
                        if (firstUnreadPostId != null) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    val target = posts.indexOfFirst { it.id == firstUnreadPostId }
                                    if (target >= 0) scope.launch { state.animateScrollToItem(target + 1) }
                                },
                                shape = jumpShape,
                                containerColor = next.accent,
                                contentColor = next.onAccent,
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.next_thread_jump_unread),
                                    style = NextType.labelMedium,
                                )
                            }
                        }
                        ExtendedFloatingActionButton(
                            onClick = { scope.launch { state.animateScrollToItem(posts.size) } },
                            shape = jumpShape,
                            containerColor = next.elevated,
                            contentColor = next.ink,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
                            modifier = Modifier.border(1.dp, next.hairline, jumpShape),
                        ) {
                            Text(
                                text = stringResource(Res.string.next_thread_jump_bottom),
                                style = NextType.labelMedium,
                            )
                        }
                    }
                }
            },
        ) { scaffoldPadding ->
            LazyColumn(
                state = state,
                modifier = Modifier.padding(scaffoldPadding).contentInsets(),
                contentPadding =
                    PaddingValues(
                        // Room for the jump pill plus a margin, so the last post can scroll fully clear.
                        bottom = THREAD_JUMP_CLEARANCE + bottomInset(),
                    ),
            ) {
                item {
                    Row(
                        modifier = Modifier.padding(start = GUTTER, top = 26.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BoardDot(board, size = 6.dp)
                        WidthSpacer(7)
                        Text(
                            text = board,
                            style = NextType.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = boardHue(board),
                        )
                        WidthSpacer(8)
                        val countText =
                            pluralStringResource(
                                Res.plurals.next_thread_posts_count,
                                posts.size,
                                posts.size,
                            )
                        MetaLine(
                            subtitle ?: countText,
                            color = next.faint,
                        )
                    }
                    ScreenTitle(text = subject, size = 34)
                    // Two things only: how to read the thread, and whether to be told when it moves.
                    val barShape = RoundedCornerShape(NextRadius.tight)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = GUTTER)
                                .clip(barShape)
                                .background(next.raised)
                                .border(1.dp, next.hairline, barShape)
                                .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val threadSegments =
                            listOf(
                                stringResource(Res.string.next_thread_posts),
                                stringResource(Res.string.next_thread_files),
                            )
                        val selectedSegment = if (layout == ThreadLayout.FILES) 1 else 0
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.weight(1f),
                        ) {
                            threadSegments.forEachIndexed { index, label ->
                                SegmentedButton(
                                    selected = index == selectedSegment,
                                    onClick = {
                                        onLayoutChange(
                                            if (index == 1) ThreadLayout.FILES else ThreadLayout.POSTS,
                                        )
                                    },
                                    shape =
                                        SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = threadSegments.size,
                                        ),
                                    colors =
                                        SegmentedButtonDefaults.colors(
                                            activeContainerColor = next.accent,
                                            activeContentColor = next.onAccent,
                                            inactiveContainerColor = next.raised,
                                            inactiveContentColor = next.ink,
                                            activeBorderColor = next.accent,
                                            inactiveBorderColor = next.hairline,
                                        ),
                                    label = {
                                        Text(
                                            text = label,
                                            style = NextType.labelMedium,
                                            fontWeight =
                                                if (index == selectedSegment) FontWeight.Bold else FontWeight.Medium,
                                        )
                                    },
                                )
                            }
                        }
                        WidthSpacer(8)
                        NextIconAction(
                            imageVector =
                                if (watching) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                            contentDescription =
                                if (watching) {
                                    stringResource(Res.string.next_thread_watching)
                                } else {
                                    stringResource(Res.string.next_thread_watch)
                                },
                            onClick = onWatch,
                            tint = if (watching) next.accent else next.muted,
                        )
                    }
                    if (layout == ThreadLayout.FILES) {
                        // What you do with every file belongs with the files, not above the posts.
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = GUTTER - 4.dp, top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            InlineAction(
                                label = stringResource(Res.string.next_thread_download_all),
                                onClick = onDownloadAll,
                            )
                            // The whole thread, posts and files, as a page that opens in any browser.
                            onSavePage?.let {
                                InlineAction(stringResource(Res.string.next_thread_save_page), onClick = it)
                            }
                            InlineAction(stringResource(Res.string.next_thread_share), onClick = onShare)
                        }
                    }
                    Gap(18)
                    Hairline()
                }
                if (layout == ThreadLayout.POSTS) {
                    itemsIndexed(posts, key = { _, post -> post.id }) { index, post ->
                        PostView(
                            post = post,
                            board = board,
                            seed = index,
                            collapsed = post.id in collapsed,
                            onToggleCollapse = onToggleCollapse,
                            onClick = onPostClick,
                            body = body,
                            media = media,
                        )
                    }
                } else {
                    items(
                        fileRows,
                        key = { row -> row.joinToString(separator = "|") { it.id } },
                        contentType = { "thread-files-row" },
                    ) { rowOfFiles ->
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp)) {
                            rowOfFiles.forEach { cell ->
                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .padding(2.5.dp)
                                            .clip(RoundedCornerShape(GRID_TILE_RADIUS))
                                            .nextClickable(onClick = { onOpenFile(cell) }),
                                ) {
                                    val shape = Modifier.fillMaxWidth().aspectRatio(1f)
                                    if (fileTile != null) fileTile(cell, shape) else MediaTile(modifier = shape)
                                }
                            }
                            repeat(fileColumns - rowOfFiles.size) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        CompactTitleBar(
            title = subject,
            visible = showCompactTitle,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

const val MAX_REPLY_DEPTH = 3

@Composable
private fun PostView(
    post: Post,
    board: String,
    seed: Int,
    collapsed: Boolean = false,
    onToggleCollapse: (Post) -> Unit = {},
    onClick: (Post) -> Unit = {},
    body: (@Composable (Post) -> Unit)? = null,
    media: (@Composable (Post, Modifier) -> Unit)? = null,
) {
    val depthCount = post.depth.coerceAtMost(MAX_REPLY_DEPTH)
    val depthReserve = (GUTTER + REPLY_DEPTH_BAR_WIDTH) * depthCount
    Surface(
        onClick = { onClick(post) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        shape = RoundedCornerShape(NextRadius.tight),
        border = BorderStroke(0.5.dp, if (seed == 0) next.hairline else next.hairline.copy(alpha = 0.5f)),
        color = if (seed == 0) next.raised else next.background,
        contentColor = next.ink,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = (if (depthCount == 0) GUTTER else 12.dp) + depthReserve,
                            end = GUTTER,
                            top = 10.dp,
                            bottom = 10.dp,
                        ),
            ) {
                PostMetaHeader(
                    post = post,
                    board = board,
                    isOp = seed == 0,
                    collapsed = collapsed,
                    onToggleCollapse = onToggleCollapse,
                )
                if (collapsed) return@Column
                Gap(8)
                PostContent(
                    post = post,
                    seed = seed,
                    body = body,
                    media = media,
                )
                Gap(8)
                PostActionRow(isOp = seed == 0, replies = post.replies)
            }
            if (depthCount > 0) {
                PostDepthIndicator(depthCount = depthCount, board = board)
            }
        }
    }
}

@Composable
private fun PostMetaHeader(
    post: Post,
    board: String,
    isOp: Boolean,
    collapsed: Boolean,
    onToggleCollapse: (Post) -> Unit,
) {
    val expandLabel = stringResource(Res.string.next_post_expand)
    val collapseLabel = stringResource(Res.string.next_post_collapse)
    val collapsedState = stringResource(Res.string.next_post_collapsed_state)
    val expandedState = stringResource(Res.string.next_post_expanded_state)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = MIN_TOUCH_TARGET)
                .nextClickable(
                    onClickLabel = if (collapsed) expandLabel else collapseLabel,
                    onClick = { onToggleCollapse(post) },
                ).semantics { stateDescription = if (collapsed) collapsedState else expandedState }
                .padding(vertical = 4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(if (isOp) 28.dp else 22.dp)
                    .clip(CircleShape)
                    .then(
                        if (isOp) {
                            Modifier.border(1.5.dp, InstagramStoryGradient, CircleShape).padding(2.dp)
                        } else {
                            Modifier
                        },
                    ).clip(CircleShape)
                    .background(boardHue(board)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isOp) "OP" else post.number.takeLast(2),
                style = NextType.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        if (isOp) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(NextRadius.control))
                    .background(next.accentSoft)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "Author",
                    style = NextType.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = next.accent,
                )
            }
        }
        Text(
            text = post.number,
            style = NextType.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isOp) next.accent else next.ink,
        )
        MetaLine(post.time, color = next.faint)
        if (post.replies > 0) {
            val repliesText =
                pluralStringResource(
                    Res.plurals.next_row_replies,
                    post.replies,
                    post.replies,
                )
            Pill(repliesText, tint = next.muted)
        }
        if (collapsed) {
            MetaLine(stringResource(Res.string.next_post_collapsed), color = next.faint)
        }
    }
}

@Composable
private fun PostContent(
    post: Post,
    seed: Int,
    body: (@Composable (Post) -> Unit)?,
    media: (@Composable (Post, Modifier) -> Unit)?,
) {
    var spoilerRevealed by rememberSaveable(post.id) { mutableStateOf(false) }
    if (post.spoiler && !spoilerRevealed) {
        val revealLabel = stringResource(Res.string.next_spoiler_reveal)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(NextRadius.control))
                    .background(Color.Black.copy(alpha = SPOILER_SCRIM))
                    .nextClickable(
                        onClickLabel = revealLabel,
                        onClick = { spoilerRevealed = true },
                    ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = revealLabel,
                style = NextType.labelMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
    } else if (body != null) {
        body(post)
    } else {
        Text(
            text = post.body,
            style = NextType.body,
            fontWeight = FontWeight.Normal,
            color = next.ink.copy(alpha = 0.90f),
        )
    }
    if (post.hasMedia) {
        Gap(10)
        val tile = Modifier.fillMaxWidth().aspectRatio(DEFAULT_POST_MEDIA_ASPECT_RATIO)
        Box(modifier = tile.clip(RoundedCornerShape(NextRadius.control))) {
            if (media != null) {
                media(post, Modifier.matchParentSize())
            } else {
                MediaTile(
                    modifier = Modifier.matchParentSize(),
                    seed = seed + 1,
                    radius = NextRadius.control,
                )
            }
        }
    }
}

@Composable
private fun PostActionRow(
    isOp: Boolean,
    replies: Int,
) {
    var isLiked by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Reply",
            style = NextType.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = next.muted,
        )
        Spacer(Modifier.weight(1f))
        NextIconAction(
            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isLiked) "Unlike" else "Like",
            onClick = { isLiked = !isLiked },
            tint = if (isLiked) Color(0xFFED4956) else next.muted,
        )
    }
}

@Composable
private fun PostDepthIndicator(
    depthCount: Int,
    board: String,
) {
    Row(modifier = Modifier.fillMaxHeight().padding(vertical = 10.dp)) {
        repeat(depthCount) {
            Column(
                modifier = Modifier.padding(start = GUTTER).fillMaxHeight().width(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(1.5.dp)
                        .background(boardHue(board).copy(alpha = 0.25f)),
                )
            }
        }
    }
}

private const val SPOILER_SCRIM = 0.88f
private const val DEFAULT_POST_MEDIA_ASPECT_RATIO = 16f / 9f
private val THREAD_JUMP_CLEARANCE = 84.dp
private val REPLY_DEPTH_BAR_WIDTH = 2.dp
