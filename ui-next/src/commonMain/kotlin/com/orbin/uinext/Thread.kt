package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.orbin.uinext.resources.next_thread_save_page
import com.orbin.uinext.resources.next_thread_watch
import com.orbin.uinext.resources.next_thread_watching
import com.orbin.uinext.resources.social_feed_save
import com.orbin.uinext.resources.social_feed_saved_while_watching
import com.orbin.uinext.resources.social_feed_unsave
import com.orbin.uinext.resources.social_thread_anonymous
import com.orbin.uinext.resources.social_thread_back
import com.orbin.uinext.resources.social_thread_op
import com.orbin.uinext.resources.social_thread_share
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
    val author: String = "",
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
    onSavePage: (() -> Unit)? = null,
    onShare: () -> Unit = {},
    onPostClick: (Post) -> Unit = {},
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
) {
    val state = listState ?: rememberLazyListState()
    val filesState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val fileRows = remember(files, fileColumns) { files.chunked(fileColumns) }
    LaunchedEffect(scrollToPostId, posts, layout) {
        if (layout != ThreadLayout.POSTS) return@LaunchedEffect
        val target = posts.indexOfFirst { it.id == scrollToPostId }
        if (scrollToPostId != null && target >= 0) {
            if (posts[target].id in collapsed) onToggleCollapse(posts[target])
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
            topBar = {
                Column(Modifier.fillMaxWidth().contentInsets().background(next.background)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onBack != null) {
                            NextIconAction(
                                imageVector = ReaderIcons.Back,
                                contentDescription = stringResource(Res.string.social_thread_back),
                                onClick = onBack,
                            )
                        } else {
                            Box(Modifier.size(MIN_TOUCH_TARGET))
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = subject,
                                style =
                                    NextType.titleLarge.copy(
                                        fontSize = 17.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                color = next.ink,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text =
                                    listOf(
                                        sourceLabel(siteName),
                                        board,
                                    ).filter(String::isNotBlank).joinToString(" · "),
                                style = NextType.footnote.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                color = next.muted,
                                textAlign = TextAlign.Center,
                            )
                        }
                        NextIconAction(
                            imageVector = ReaderIcons.Share,
                            contentDescription = stringResource(Res.string.social_thread_share),
                            onClick = onShare,
                        )
                    }
                    Hairline()
                }
            },
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
                                    if (target >= 0) {
                                        if (posts[target].id in collapsed) onToggleCollapse(posts[target])
                                        scope.launch { state.animateScrollToItem(target + 1) }
                                    }
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
                state = if (layout == ThreadLayout.POSTS) state else filesState,
                modifier =
                    Modifier
                        .padding(scaffoldPadding)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                contentPadding =
                    PaddingValues(
                        // Room for the jump pill plus a margin, so the last post can scroll fully clear.
                        bottom = THREAD_JUMP_CLEARANCE + bottomInset(),
                    ),
            ) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 8.dp)) {
                        if (!subtitle.isNullOrBlank()) {
                            MetaLine(subtitle, color = next.muted)
                            Gap(4)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(Modifier.weight(1f).selectableGroup()) {
                                ThreadLayoutTab(
                                    label = stringResource(Res.string.next_thread_posts),
                                    selected = layout == ThreadLayout.POSTS,
                                    onClick = { onLayoutChange(ThreadLayout.POSTS) },
                                    modifier = Modifier.weight(1f),
                                )
                                ThreadLayoutTab(
                                    label = stringResource(Res.string.next_thread_files),
                                    selected = layout == ThreadLayout.FILES,
                                    onClick = { onLayoutChange(ThreadLayout.FILES) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            WidthSpacer(8)
                            NextIconAction(
                                imageVector =
                                    if (watching) {
                                        ReaderIcons.WatchedFilled
                                    } else {
                                        ReaderIcons.Watched
                                    },
                                contentDescription =
                                    if (watching) {
                                        stringResource(Res.string.next_thread_watching)
                                    } else {
                                        stringResource(Res.string.next_thread_watch)
                                    },
                                onClick = onWatch,
                                tint = next.ink,
                            )
                            if (onToggleSaved != null) {
                                val saveIcon = if (saved) ReaderIcons.SavedFilled else ReaderIcons.Saved
                                if (saved && watching) {
                                    val savedLabel =
                                        stringResource(Res.string.social_feed_saved_while_watching, subject)
                                    Box(
                                        modifier =
                                            Modifier.size(MIN_TOUCH_TARGET).semantics {
                                                contentDescription = savedLabel
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(saveIcon, contentDescription = null, tint = next.ink)
                                    }
                                } else {
                                    NextIconAction(
                                        imageVector = saveIcon,
                                        contentDescription =
                                            stringResource(
                                                if (saved) {
                                                    Res.string.social_feed_unsave
                                                } else {
                                                    Res.string.social_feed_save
                                                },
                                                subject,
                                            ),
                                        onClick = onToggleSaved,
                                        tint = next.ink,
                                    )
                                }
                            }
                        }
                    }
                    if (layout == ThreadLayout.FILES) {
                        // What you do with every file belongs with the files, not above the posts.
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(start = GUTTER - 4.dp, top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            InlineAction(
                                label = stringResource(Res.string.next_thread_download_all),
                                onClick = onDownloadAll,
                            )
                            // The whole thread, posts and files, as a page that opens in any browser.
                            onSavePage?.let {
                                InlineAction(stringResource(Res.string.next_thread_save_page), onClick = it)
                            }
                            InlineAction(stringResource(Res.string.social_thread_share), onClick = onShare)
                        }
                    }
                    Gap(4)
                    Hairline()
                }
                if (layout == ThreadLayout.POSTS) {
                    itemsIndexed(posts, key = { _, post -> post.id }) { index, post ->
                        if (index == 1) {
                            Text(
                                text =
                                    pluralStringResource(
                                        Res.plurals.next_row_replies,
                                        posts.size - 1,
                                        posts.size - 1,
                                    ),
                                style = NextType.headline,
                                color = next.ink,
                                modifier = Modifier.padding(horizontal = GUTTER, vertical = 12.dp),
                            )
                        }
                        PostView(
                            post = post,
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
    }
}

const val MAX_REPLY_DEPTH = 3

@Composable
private fun ThreadLayoutTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET).padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = NextType.body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                color = if (selected) next.ink else next.muted,
                textAlign = TextAlign.Center,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(if (selected) next.ink else Color.Transparent))
    }
}

@Composable
private fun PostView(
    post: Post,
    seed: Int,
    collapsed: Boolean = false,
    onToggleCollapse: (Post) -> Unit = {},
    onClick: (Post) -> Unit = {},
    body: (@Composable (Post) -> Unit)? = null,
    media: (@Composable (Post, Modifier) -> Unit)? = null,
) {
    Surface(
        onClick = { onClick(post) },
        modifier = Modifier.fillMaxWidth(),
        color = next.background,
        contentColor = next.ink,
    ) {
        if (seed == 0) {
            Column(Modifier.fillMaxWidth()) {
                PostMetadata(
                    post = post,
                    original = true,
                    collapsed = collapsed,
                    onToggleCollapse = onToggleCollapse,
                    modifier = Modifier.padding(horizontal = GUTTER),
                )
                if (!collapsed) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 4.dp)) {
                        PostBody(post, body)
                    }
                    if (post.hasMedia) {
                        Gap(8)
                        PostMedia(post, seed, media)
                    }
                }
                Gap(12)
                Hairline()
            }
        } else {
            val depthReserve = REPLY_INDENT * post.depth.coerceIn(0, MAX_REPLY_DEPTH)
            Row(
                modifier =
                    Modifier.fillMaxWidth().padding(
                        start = GUTTER + depthReserve,
                        end = GUTTER,
                        top = 4.dp,
                        bottom = 12.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                PostAvatar(post, Modifier.padding(top = 8.dp), compact = true)
                Column(Modifier.weight(1f)) {
                    PostMetadata(post, false, collapsed, onToggleCollapse)
                    if (!collapsed) {
                        PostBody(post, body)
                        if (post.hasMedia) {
                            Gap(8)
                            PostMedia(post, seed, media)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostMetadata(
    post: Post,
    original: Boolean,
    collapsed: Boolean,
    onToggleCollapse: (Post) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expandLabel = stringResource(Res.string.next_post_expand)
    val collapseLabel = stringResource(Res.string.next_post_collapse)
    val collapsedState = stringResource(Res.string.next_post_collapsed_state)
    val expandedState = stringResource(Res.string.next_post_expanded_state)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MIN_TOUCH_TARGET)
                .nextClickable(
                    onClickLabel = if (collapsed) expandLabel else collapseLabel,
                    onClick = { onToggleCollapse(post) },
                ).semantics { stateDescription = if (collapsed) collapsedState else expandedState },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (original) PostAvatar(post)
        Column(Modifier.weight(1f)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = post.author.ifBlank { stringResource(Res.string.social_thread_anonymous) },
                    style = NextType.body.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
                    color = next.ink,
                )
                if (original) {
                    Text(
                        text = stringResource(Res.string.social_thread_op),
                        style = NextType.footnote.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = next.muted,
                    )
                } else if (post.time.isNotBlank()) {
                    Text(
                        post.time,
                        style = NextType.footnote.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = next.muted,
                    )
                }
            }
            Text(
                text =
                    if (original) {
                        listOf(
                            post.number,
                            post.time,
                        ).filter(String::isNotBlank).joinToString(" · ")
                    } else {
                        post.number
                    },
                style = NextType.footnote.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = next.muted,
            )
            if (collapsed) {
                Text(
                    text = stringResource(Res.string.next_post_collapsed),
                    style = NextType.footnote,
                    color = next.faint,
                )
            }
        }
        if (post.replies > 0) {
            Text(
                text = pluralStringResource(Res.plurals.next_row_replies, post.replies, post.replies),
                style = NextType.footnote.copy(fontSize = 12.sp),
                color = next.muted,
            )
        }
    }
}

/** Neutral initials distinguish anonymous post rows without implying accounts or profile photos. */
@Composable
private fun PostAvatar(
    post: Post,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val name = post.author.ifBlank { stringResource(Res.string.social_thread_anonymous) }
    Box(
        modifier.size(if (compact) 28.dp else 32.dp).clip(CircleShape).background(next.raised),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            style = NextType.footnote.copy(fontWeight = FontWeight.Medium),
            color = next.muted,
        )
    }
}

@Composable
private fun PostBody(
    post: Post,
    body: (@Composable (Post) -> Unit)?,
) {
    var spoilerRevealed by rememberSaveable(post.id) { mutableStateOf(false) }
    if (post.spoiler && !spoilerRevealed) {
        val revealLabel = stringResource(Res.string.next_spoiler_reveal)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .clip(RoundedCornerShape(NextRadius.tight))
                    .background(Color.Black.copy(alpha = SPOILER_SCRIM))
                    .nextClickable(onClickLabel = revealLabel, onClick = { spoilerRevealed = true }),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = revealLabel,
                style = NextType.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    } else if (body != null) {
        body(post)
    } else {
        Text(
            text = post.body,
            style = NextType.body.copy(fontSize = 14.sp, lineHeight = 19.sp),
            color = next.ink,
        )
    }
}

@Composable
private fun PostMedia(
    post: Post,
    seed: Int,
    media: (@Composable (Post, Modifier) -> Unit)?,
) {
    if (media != null) {
        // Platform media slots own the attachment ratio and multi-file pager height.
        media(post, Modifier.fillMaxWidth())
    } else {
        MediaTile(
            modifier = Modifier.fillMaxWidth().aspectRatio(DEFAULT_POST_MEDIA_ASPECT_RATIO),
            seed = seed + 1,
            radius = 0.dp,
        )
    }
}

private const val SPOILER_SCRIM = 0.88f
private const val DEFAULT_POST_MEDIA_ASPECT_RATIO = 16f / 9f
private val THREAD_JUMP_CLEARANCE = 84.dp
private val REPLY_INDENT = 12.dp
