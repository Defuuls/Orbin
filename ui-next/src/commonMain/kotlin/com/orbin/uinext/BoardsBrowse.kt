package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_boards_no_match
import com.orbin.uinext.resources.next_boards_nsfw
import com.orbin.uinext.resources.next_boards_random
import com.orbin.uinext.resources.next_boards_search
import com.orbin.uinext.resources.next_search_title
import com.orbin.uinext.resources.next_status_retry
import com.orbin.uinext.resources.social_feed_all_boards
import com.orbin.uinext.resources.social_feed_favorite_add
import com.orbin.uinext.resources.social_feed_favorite_boards
import com.orbin.uinext.resources.social_feed_favorite_remove
import com.orbin.uinext.resources.tiktok_board_follow
import com.orbin.uinext.resources.tiktok_board_following
import com.orbin.uinext.resources.tiktok_boards_title
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.stringResource

/** A board directory row. Its stable [id] includes its site when more than one site is shown. */
data class BoardTile(
    val id: String,
    val path: String,
    val title: String,
    val nsfw: Boolean = false,
    val followed: Boolean = false,
    val site: String? = null,
)

internal fun List<BoardTile>.sortedAlphabetically(): List<BoardTile> =
    sortedWith(
        compareBy<BoardTile, String>(String.CASE_INSENSITIVE_ORDER) { it.path }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )

/** A searchable, source-scoped directory with independent open and favorite actions. */
@Composable
fun BoardsScreen(
    boards: List<BoardTile>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showRail: Boolean = true,
    onOpenBoard: (BoardTile) -> Unit = {},
    onFollowBoard: ((BoardTile, Boolean) -> Unit)? = null,
    onRandom: (() -> Unit)? = null,
    onOpenFeed: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    hideRailOnScroll: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    onOpenSearch: (() -> Unit)? = null,
    headerContent: @Composable () -> Unit = {},
    onOpenSaved: (() -> Unit)? = null,
    onOpenWatched: (() -> Unit)? = null,
    emptyMessage: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visibleBoards =
        remember(boards, query) {
            val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            boards
                .filter { board ->
                    terms.all { term -> "${board.path} ${board.title}".contains(term, ignoreCase = true) }
                }.sortedAlphabetically()
        }
    val favorites = remember(visibleBoards) { visibleBoards.filter { it.followed } }
    val listState = rememberLazyListState()
    val railVisible =
        if (hideRailOnScroll) {
            scrollingUp({ listState.firstVisibleItemIndex }, { listState.firstVisibleItemScrollOffset })
        } else {
            true
        }
    LaunchedEffect(railVisible) { onChromeVisibleChange(railVisible) }
    val hasTabs =
        onOpenFeed != null ||
            onOpenDownloads != null ||
            onOpenSearch != null ||
            onOpenSaved != null ||
            onOpenWatched != null
    val onDestination: ((NextDestination) -> Unit)? =
        if (hasTabs) {
            { dest ->
                when (dest) {
                    NextDestination.FEED -> onOpenFeed?.invoke()
                    NextDestination.SEARCH -> onOpenSearch?.invoke()
                    NextDestination.SAVED -> onOpenSaved?.invoke()
                    NextDestination.WATCHED -> onOpenWatched?.invoke()
                    NextDestination.DOWNLOADS -> onOpenDownloads?.invoke()
                    NextDestination.SETTINGS -> onOpenSettings?.invoke()
                    NextDestination.BOARDS -> Unit
                }
            }
        } else {
            null
        }
    val boardsTitle = stringResource(Res.string.tiktok_boards_title)
    val showCompactTitle by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 64 }
    }
    Box(modifier.fillMaxSize()) {
        NextScaffold(
            where = boardsTitle,
            modifier = Modifier.fillMaxSize(),
            railVisible = railVisible,
            destination = NextDestination.BOARDS.takeIf { showRail && hasTabs },
            onDestination = onDestination.takeIf { showRail },
            drawerDestinations =
                buildSet {
                    add(NextDestination.BOARDS)
                    if (onOpenFeed != null) add(NextDestination.FEED)
                    if (onOpenWatched != null) add(NextDestination.WATCHED)
                    if (onOpenSaved != null) add(NextDestination.SAVED)
                    if (onOpenSearch != null) add(NextDestination.SEARCH)
                    if (onOpenSettings != null) add(NextDestination.SETTINGS)
                },
            actions =
                listOfNotNull(
                    onOpenSearch?.let {
                        ReaderAction(
                            ReaderIcons.Search,
                            stringResource(Res.string.next_search_title),
                            it,
                        )
                    },
                ),
        ) { bottomPad ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().contentInsets(),
                contentPadding = PaddingValues(bottom = bottomPad.calculateBottomPadding() + NextSpace.groupGap),
            ) {
                item(key = "boards-title") {
                    Column(Modifier.padding(horizontal = BOARD_DIRECTORY_GUTTER)) {
                        if (!LocalReaderFramePresent.current) {
                            ScreenTitle(boardsTitle, inset = 0.dp)
                        }
                        headerContent()
                        Gap(8)
                        SchematicSearch(query, { query = it }, stringResource(Res.string.next_boards_search))
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                subtitle,
                                style = NextType.footnote,
                                color = next.muted,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        if (onRandom !=
                            null
                        ) {
                            InlineAction(stringResource(Res.string.next_boards_random), onClick = onRandom)
                        }
                    }
                }
                if (visibleBoards.isEmpty()) {
                    item(key = "boards-empty") {
                        Text(
                            (
                                emptyMessage?.takeIf { boards.isEmpty() && query.isBlank() }
                                    ?: stringResource(Res.string.next_boards_no_match)
                            ),
                            style = NextType.body,
                            color = next.muted,
                            modifier = Modifier.padding(BOARD_DIRECTORY_GUTTER),
                        )
                        if (onRetry != null) {
                            InlineAction(
                                stringResource(Res.string.next_status_retry),
                                onClick = onRetry,
                                modifier = Modifier.padding(horizontal = BOARD_DIRECTORY_GUTTER),
                            )
                        }
                    }
                } else {
                    if (favorites.isNotEmpty()) {
                        item(
                            key = "favorite-heading",
                        ) { BoardSectionTitle(stringResource(Res.string.social_feed_favorite_boards)) }
                        items(favorites, key = { "favorite:${it.id}" }) { board ->
                            BoardListRow(
                                board,
                                { onOpenBoard(board) },
                                onFollowBoard?.let {
                                    { checked ->
                                        it(board, checked)
                                    }
                                },
                            )
                        }
                        item(key = "favorite-divider") {
                            Gap(12)
                            Hairline()
                        }
                    }
                    item(key = "all-heading") { BoardSectionTitle(stringResource(Res.string.social_feed_all_boards)) }
                    items(visibleBoards, key = { "all:${it.id}" }) { board ->
                        BoardListRow(
                            board,
                            { onOpenBoard(board) },
                            onFollowBoard?.let {
                                { checked ->
                                    it(board, checked)
                                }
                            },
                        )
                    }
                }
            }
        }
        if (!hasTabs && !LocalReaderFramePresent.current) {
            CompactTitleBar(boardsTitle, showCompactTitle, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun BoardSectionTitle(label: String) {
    Text(
        label,
        style = NextType.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = next.ink,
        modifier =
            Modifier
                .padding(
                    start = BOARD_DIRECTORY_GUTTER,
                    end = BOARD_DIRECTORY_GUTTER,
                    top = 16.dp,
                    bottom = 6.dp,
                ).semantics { heading() },
    )
}

@Composable
private fun BoardListRow(
    board: BoardTile,
    onClick: () -> Unit,
    onFollow: ((Boolean) -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .nextClickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = BOARD_DIRECTORY_GUTTER, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BoardIcon(
            siteId = board.site ?: board.id.substringBefore('/'),
            boardId = board.path,
            title = board.title,
            modifier = Modifier.size(48.dp),
        )
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(board.path, style = NextType.titleMedium, color = next.ink)
            Text(board.title, style = NextType.bodyMedium, color = next.muted)
            val metadata =
                listOfNotNull(
                    board.site?.let(::sourceLabel)?.takeIf(String::isNotBlank),
                    stringResource(Res.string.next_boards_nsfw).takeIf { board.nsfw },
                ).joinToString(" · ")
            if (metadata.isNotBlank()) {
                Text(
                    metadata,
                    style = NextType.footnote,
                    color = next.muted,
                )
            }
        }
        if (onFollow != null) {
            val identity = board.site?.let { "${sourceLabel(it)} · ${board.path}" } ?: board.path
            val actionDescription =
                stringResource(
                    if (board.followed) {
                        Res.string.social_feed_favorite_remove
                    } else {
                        Res.string.social_feed_favorite_add
                    },
                    identity,
                )
            Box(
                Modifier
                    .widthIn(min = 80.dp, max = 112.dp)
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .nextClickable(role = Role.Button) { onFollow(!board.followed) }
                    .semantics {
                        contentDescription = actionDescription
                        selected = board.followed
                    },
                contentAlignment = Alignment.Center,
            ) {
                val shape = RoundedCornerShape(3.dp)
                Text(
                    stringResource(
                        if (board.followed) Res.string.tiktok_board_following else Res.string.tiktok_board_follow,
                    ),
                    style =
                        NextType.bodyMedium.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = if (board.followed) next.ink else next.onAccent,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(if (board.followed) next.background else next.accent)
                            .border(1.dp, if (board.followed) next.hairline else next.accent, shape)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        } else {
            Icon(ReaderIcons.ChevronRight, null, tint = next.faint, modifier = Modifier.size(20.dp))
        }
    }
}

private val BOARD_DIRECTORY_GUTTER = 12.dp
