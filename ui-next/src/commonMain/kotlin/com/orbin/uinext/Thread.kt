package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_post_collapse
import com.orbin.uinext.resources.next_post_collapsed
import com.orbin.uinext.resources.next_post_collapsed_state
import com.orbin.uinext.resources.next_post_expand
import com.orbin.uinext.resources.next_post_expanded_state
import com.orbin.uinext.resources.next_spoiler_reveal
import com.orbin.uinext.resources.next_thread_download_all
import com.orbin.uinext.resources.next_thread_files
import com.orbin.uinext.resources.next_thread_jump_bottom
import com.orbin.uinext.resources.next_thread_jump_top
import com.orbin.uinext.resources.next_thread_jump_unread
import com.orbin.uinext.resources.next_thread_posts
import com.orbin.uinext.resources.next_thread_share
import com.orbin.uinext.resources.next_thread_watch
import com.orbin.uinext.resources.next_thread_watching
import com.orbin.uinext.tokens.NextRadius
import kotlinx.coroutines.launch
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
    LaunchedEffect(scrollToPostId, posts) {
        val target = posts.indexOfFirst { it.id == scrollToPostId }
        if (scrollToPostId != null && target >= 0) {
            state.animateScrollToItem(target + 1)
            onScrollConsumed()
        }
    }
    Surface {
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                state = state,
                modifier = Modifier.contentInsets(),
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
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.2.sp,
                            color = boardHue(board),
                        )
                        WidthSpacer(8)
                        MetaLine(subtitle ?: "${posts.size} posts", color = next.faint)
                    }
                    ScreenTitle(text = subject, size = 26)
                    // Two things only: how to read the thread, and whether to be told when it moves.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlatformSegments(
                            labels =
                                listOf(
                                    stringResource(Res.string.next_thread_posts),
                                    stringResource(Res.string.next_thread_files),
                                ),
                            selected = if (layout == ThreadLayout.FILES) 1 else 0,
                            onSelect = { index ->
                                onLayoutChange(
                                    if (index ==
                                        1
                                    ) {
                                        ThreadLayout.FILES
                                    } else {
                                        ThreadLayout.POSTS
                                    },
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
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
                        if (index < posts.lastIndex) Hairline(inset = true)
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
                                            .clip(RoundedCornerShape(14.dp))
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
            if (layout == ThreadLayout.POSTS && posts.size > 1) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = GUTTER,
                                bottom = 12.dp + bottomInset(),
                            )
                            // A solid pill, so the jumps stay legible over the posts they float on.
                            .nextElevatedSurface(RoundedCornerShape(NextRadius.pill))
                            .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    InlineAction(
                        label = stringResource(Res.string.next_thread_jump_top),
                        onClick = { scope.launch { state.animateScrollToItem(0) } },
                    )
                    if (firstUnreadPostId != null) {
                        InlineAction(
                            label = stringResource(Res.string.next_thread_jump_unread),
                            accent = true,
                            onClick = {
                                val target = posts.indexOfFirst { it.id == firstUnreadPostId }
                                if (target >= 0) scope.launch { state.animateScrollToItem(target + 1) }
                            },
                        )
                    }
                    InlineAction(
                        label = stringResource(Res.string.next_thread_jump_bottom),
                        onClick = { scope.launch { state.animateScrollToItem(posts.size) } },
                    )
                }
            }
        }
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
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = GUTTER, vertical = 5.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(next.raised)
                .nextClickable(onClick = { onClick(post) }),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = (if (depthCount == 0) GUTTER else 13.dp) + depthReserve,
                        end = GUTTER,
                        top = 15.dp,
                        bottom = 15.dp,
                    ),
        ) {
            val expandLabel = stringResource(Res.string.next_post_expand)
            val collapseLabel = stringResource(Res.string.next_post_collapse)
            val collapsedState = stringResource(Res.string.next_post_collapsed_state)
            val expandedState = stringResource(Res.string.next_post_expanded_state)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .nextClickable(
                            onClickLabel = if (collapsed) expandLabel else collapseLabel,
                            onClick = { onToggleCollapse(post) },
                        ).semantics { stateDescription = if (collapsed) collapsedState else expandedState },
            ) {
                MetaLine(post.number, color = next.faint)
                WidthSpacer(8)
                MetaLine(post.time, color = next.faint)
                if (post.replies > 0) {
                    WidthSpacer(8)
                    Pill("${post.replies} replies", tint = next.muted)
                }
                if (collapsed) {
                    WidthSpacer(8)
                    MetaLine(stringResource(Res.string.next_post_collapsed), color = next.faint)
                }
            }
            if (collapsed) return@Column
            Gap(8)
            if (post.spoiler) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = SPOILER_SCRIM)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(Res.string.next_spoiler_reveal),
                        fontSize = 12.5.sp,
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
                    fontSize = 15.5.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Normal,
                    color = next.ink.copy(alpha = 0.90f),
                )
            }
            if (post.hasMedia) {
                Gap(12)
                val tile = Modifier.fillMaxWidth()
                if (media != null) {
                    media(post, tile)
                } else {
                    MediaTile(
                        modifier = tile.aspectRatio(DEFAULT_POST_MEDIA_ASPECT_RATIO),
                        seed = seed + 1,
                        radius = 16.dp,
                    )
                }
            }
        }
        if (depthCount > 0) {
            Row(modifier = Modifier.matchParentSize().padding(top = 14.dp, bottom = 14.dp)) {
                repeat(depthCount) {
                    Box(
                        modifier =
                            Modifier
                                .padding(start = GUTTER)
                                .fillMaxHeight()
                                .width(REPLY_DEPTH_BAR_WIDTH)
                                .clip(RoundedCornerShape(1.dp))
                                .background(boardHue(board).copy(alpha = 0.30f)),
                    )
                }
            }
        }
    }
}

private const val SPOILER_SCRIM = 0.88f
private const val DEFAULT_POST_MEDIA_ASPECT_RATIO = 16f / 9f
private val THREAD_JUMP_CLEARANCE = 84.dp
private val REPLY_DEPTH_BAR_WIDTH = 2.dp
