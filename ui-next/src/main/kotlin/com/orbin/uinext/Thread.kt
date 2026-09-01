package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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
    showRail: Boolean = true,
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
    onClassicReader: (() -> Unit)? = null,
    onSearch: () -> Unit = {},
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
                        bottom =
                            (if (showRail) RAIL_HEIGHT + THREAD_JUMP_CLEARANCE else THREAD_JUMP_CLEARANCE) +
                                bottomInset(),
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
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER - 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        InlineAction(
                            label =
                                if (watching) {
                                    stringResource(R.string.next_thread_watching)
                                } else {
                                    stringResource(R.string.next_thread_watch)
                                },
                            accent = watching,
                            onClick = onWatch,
                        )
                        InlineAction(
                            label = stringResource(R.string.next_thread_files),
                            accent = layout == ThreadLayout.FILES,
                            onClick = {
                                onLayoutChange(
                                    if (layout == ThreadLayout.FILES) ThreadLayout.POSTS else ThreadLayout.FILES,
                                )
                            },
                        )
                        InlineAction(
                            label = stringResource(R.string.next_thread_download_all),
                            onClick = onDownloadAll,
                        )
                        InlineAction(stringResource(R.string.next_thread_share), onClick = onShare)
                        if (onClassicReader != null) {
                            InlineAction(
                                label = stringResource(R.string.next_thread_classic_reader),
                                onClick = onClassicReader,
                            )
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
                    items(files.chunked(fileColumns)) { rowOfFiles ->
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp)) {
                            rowOfFiles.forEach { cell ->
                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .padding(2.5.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onOpenFile(cell) },
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
                                end = GUTTER - 4.dp,
                                bottom = (if (showRail) RAIL_HEIGHT + 30.dp else 12.dp) + bottomInset(),
                            ),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    InlineAction(
                        label = stringResource(R.string.next_thread_jump_top),
                        onClick = { scope.launch { state.animateScrollToItem(0) } },
                    )
                    if (firstUnreadPostId != null) {
                        InlineAction(
                            label = stringResource(R.string.next_thread_jump_unread),
                            accent = true,
                            onClick = {
                                val target = posts.indexOfFirst { it.id == firstUnreadPostId }
                                if (target >= 0) scope.launch { state.animateScrollToItem(target + 1) }
                            },
                        )
                    }
                    InlineAction(
                        label = stringResource(R.string.next_thread_jump_bottom),
                        onClick = { scope.launch { state.animateScrollToItem(posts.size) } },
                    )
                }
            }
            if (showRail) {
                ContextRail(
                    where = subject.take(26),
                    detail = board,
                    onSearch = onSearch,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clickable { onClick(post) },
    ) {
        repeat(post.depth.coerceAtMost(MAX_REPLY_DEPTH)) {
            Box(
                modifier =
                    Modifier
                        .padding(start = GUTTER, top = 14.dp, bottom = 14.dp)
                        .fillMaxHeight()
                        .width(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(boardHue(board).copy(alpha = 0.30f)),
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(
                        start = if (post.depth == 0) GUTTER else 13.dp,
                        end = GUTTER,
                        top = 15.dp,
                        bottom = 15.dp,
                    ),
        ) {
            val expandLabel = stringResource(R.string.next_post_expand)
            val collapseLabel = stringResource(R.string.next_post_collapse)
            val collapsedState = stringResource(R.string.next_post_collapsed_state)
            val expandedState = stringResource(R.string.next_post_expanded_state)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .clickable(
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
                    MetaLine(stringResource(R.string.next_post_collapsed), color = next.faint)
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
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = SPOILER_SCRIM)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(R.string.next_spoiler_reveal),
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
                        radius = 14.dp,
                    )
                }
            }
        }
    }
}

private const val SPOILER_SCRIM = 0.88f
private const val DEFAULT_POST_MEDIA_ASPECT_RATIO = 16f / 9f
private val THREAD_JUMP_CLEARANCE = 62.dp
