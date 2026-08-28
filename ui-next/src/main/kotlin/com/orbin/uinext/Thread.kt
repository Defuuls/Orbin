package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What the reader is showing: the conversation, or just its files. */
enum class ThreadLayout {
    POSTS,
    FILES,
}

/**
 * One post in a thread.
 *
 * [depth] is how far down a chain of replies this post sits, not a position in a re-ordered tree:
 * posts stay in the order they were made. [id] is what the list keys on and what a scroll request
 * names, so the screen never has to know what a post actually is.
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
)

/**
 * The thread reader.
 *
 * The current reader puts a title bar on top carrying back, refresh, watch, save, download-all,
 * export and a layout toggle, and draws every post as an elevated card in one flat chronological
 * list. Nothing in it shows which post a reply is answering except the quote link inside the text.
 *
 * Two changes here. The bar's actions become words set beneath the title, where they scroll away
 * with it instead of occupying the top of the screen for the life of the thread. And a reply is
 * indented one step per link in the chain it hangs off, with a hairline in the board's colour
 * marking each step — so the shape of a conversation is visible without opening anything.
 *
 * The indent is capped, because the point is to show structure, not to surrender the text column
 * to it; past the cap a reply stays at the deepest indent rather than marching off the edge. Order
 * is never changed: an imageboard thread is read in the order it was written, and re-ordering it
 * into a tree would cost more than the nesting buys.
 *
 * A spoiler stays blacked out until pressed, exactly as now — that behaviour is load-bearing and
 * survives unchanged.
 */
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
    onScrollConsumed: () -> Unit = {},
    body: (@Composable (Post) -> Unit)? = null,
    media: (@Composable (Post, Modifier) -> Unit)? = null,
) {
    val state = listState ?: rememberLazyListState()
    // A quote link names a post, not a row: the header occupies index 0, so the offset is applied
    // here rather than being baked into whatever asked for the jump.
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
                contentPadding =
                    PaddingValues(bottom = if (showRail) RAIL_HEIGHT + 28.dp else 16.dp),
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER - 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InlineAction(
                            label = if (watching) "Watching" else "Watch",
                            accent = watching,
                            onClick = onWatch,
                        )
                        WidthSpacer(4)
                        InlineAction(
                            label = "Files",
                            accent = layout == ThreadLayout.FILES,
                            onClick = {
                                onLayoutChange(
                                    if (layout == ThreadLayout.FILES) ThreadLayout.POSTS else ThreadLayout.FILES,
                                )
                            },
                        )
                        WidthSpacer(4)
                        InlineAction(
                            label = "Download all",
                            onClick = onDownloadAll,
                        )
                        WidthSpacer(4)
                        InlineAction("Share", onClick = onShare)
                    }
                    if (onClassicReader != null) {
                        Gap(4)
                        Box(modifier = Modifier.padding(horizontal = GUTTER - 4.dp)) {
                            InlineAction(
                                label = "Classic reader",
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
                    // The thread's files, as a wall. The same shape as All media, because it is the
                    // same question asked of one thread.
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
                            // Keeps a short last row aligned with the ones above it.
                            repeat(fileColumns - rowOfFiles.size) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
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

/** How far a reply is indented before the step stops growing. */
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
    // Intrinsic height so each depth rule runs the full height of the post it belongs to, rather
    // than being a tick at the top of it.
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
            // The quiet line is also the handle: tapping it folds the post away. The old reader
            // put this on a card header; here the line that can already be ignored is the one that
            // makes the rest ignorable.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .clickable(
                            onClickLabel = if (collapsed) "Expand post" else "Collapse post",
                            onClick = { onToggleCollapse(post) },
                        ).semantics { stateDescription = if (collapsed) "Collapsed" else "Expanded" },
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
                    MetaLine("collapsed", color = next.faint)
                }
            }
            if (collapsed) return@Column
            Gap(8)
            if (post.spoiler) {
                // The same scrim the shipped app uses: black regardless of theme, so a spoiler is
                // still hidden in dark mode rather than turning into a bright block.
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
                        text = "Spoiler — press to reveal",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
            } else if (body != null) {
                // Real posts are marked-up text — quote links, greentext, inline spoilers — so the
                // caller renders them. The plain string is what keeps this screen drawable alone.
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
                val tile = Modifier.fillMaxWidth().height(158.dp)
                if (media != null) media(post, tile) else MediaTile(modifier = tile, seed = seed + 1, radius = 14.dp)
            }
        }
    }
}

private const val SPOILER_SCRIM = 0.88f
