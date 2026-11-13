package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_boards_no_match
import com.orbin.uinext.resources.next_boards_nsfw
import com.orbin.uinext.resources.next_boards_random
import com.orbin.uinext.resources.next_boards_search
import com.orbin.uinext.resources.next_boards_thread_count
import com.orbin.uinext.resources.next_status_retry
import com.orbin.uinext.resources.reader_board_follow
import com.orbin.uinext.resources.reader_board_unfollow
import com.orbin.uinext.resources.reader_followed_boards
import com.orbin.uinext.resources.reader_menu
import com.orbin.uinext.resources.social_feed_all_boards
import com.orbin.uinext.resources.tiktok_board_follow
import com.orbin.uinext.resources.tiktok_board_following
import com.orbin.uinext.resources.tiktok_boards_title
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.ReaderType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A board directory row. Its stable [id] includes its site when more than one site is shown. */
data class BoardTile(
    val id: String,
    val path: String,
    val title: String,
    val nsfw: Boolean = false,
    val followed: Boolean = false,
    val site: String? = null,
    /** Optional catalog size from a host that already has it. Never invented. */
    val threadCount: Int? = null,
)

internal fun List<BoardTile>.sortedAlphabetically(): List<BoardTile> =
    sortedWith(
        compareBy<BoardTile, String>(String.CASE_INSENSITIVE_ORDER) { it.path }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )

/** A searchable, source-scoped directory with independent open and subscription actions. */
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
    val directory = remember(visibleBoards) { visibleBoards.filter { !it.followed } }
    val siteGroups =
        remember(directory) {
            directory
                .groupBy { tile ->
                    tile.site?.let(::sourceLabel)?.takeIf(String::isNotBlank)
                }.entries
                .sortedBy { entry -> entry.key ?: "" }
                .map { entry ->
                    (entry.key ?: "") to entry.value
                }
        }
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
    val allBoardsLabel = stringResource(Res.string.social_feed_all_boards)
    val showBottomBar = showRail && hasTabs && railVisible
    NextScaffold(
        where = boardsTitle,
        modifier = modifier.fillMaxSize(),
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
        drawAppBar = false,
    ) { bottomPad ->
        val openDrawer = LocalReaderOpenDrawer.current
        Column(
            Modifier
                .fillMaxSize()
                .background(reader.background),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                        ),
                    ),
            ) {
                BoardsHomeHeader(showMenu = openDrawer != null, onMenu = { openDrawer?.invoke() })
                BoardsSearchRow(
                    query = query,
                    onQueryChange = { query = it },
                    headerContent = headerContent,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = NextType.footnote,
                        color = next.muted,
                        modifier = Modifier.padding(horizontal = BOARD_DIRECTORY_GUTTER, vertical = 4.dp),
                    )
                }
                if (onRandom != null) {
                    InlineAction(
                        stringResource(Res.string.next_boards_random),
                        onClick = onRandom,
                        modifier = Modifier.padding(horizontal = BOARD_DIRECTORY_GUTTER),
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(reader.surfaceVariant)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                        ),
                contentPadding =
                    PaddingValues(
                        top = 8.dp,
                        bottom =
                            (if (showBottomBar) 0.dp else bottomPad.calculateBottomPadding()) +
                                NextSpace.groupGap,
                    ),
            ) {
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
                        item(key = "favorite-heading") {
                            BoardSectionTitle(stringResource(Res.string.reader_followed_boards))
                        }
                        boardRows(
                            boards = favorites,
                            keyPrefix = "favorite",
                            onOpenBoard = onOpenBoard,
                            onFollowBoard = onFollowBoard,
                            showSite = true,
                        )
                    }
                    siteGroups.forEach { (siteLabel, group) ->
                        val heading = siteLabel.ifBlank { allBoardsLabel }
                        item(key = "site-heading:$heading") { BoardSectionTitle(heading) }
                        boardRows(
                            boards = group,
                            keyPrefix = "site:$heading",
                            onOpenBoard = onOpenBoard,
                            onFollowBoard = onFollowBoard,
                            showSite = siteLabel.isBlank(),
                        )
                    }
                }
            }
            if (showBottomBar && onDestination != null) {
                OrbinBottomBar(
                    selected = NextDestination.BOARDS,
                    onSelect = onDestination,
                    destinations =
                        buildList {
                            add(NextDestination.BOARDS)
                            if (onOpenFeed != null) add(NextDestination.FEED)
                            if (onOpenSettings != null) add(NextDestination.SETTINGS)
                        },
                )
            }
        }
    }
}

@Composable
private fun BoardsHomeHeader(
    showMenu: Boolean,
    onMenu: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BOARD_DIRECTORY_GUTTER, vertical = 4.dp)
            .heightIn(min = MIN_TOUCH_TARGET),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrbinWordmark()
        Spacer(Modifier.weight(1f))
        if (showMenu) {
            NextIconAction(
                imageVector = ReaderIcons.Menu,
                contentDescription = stringResource(Res.string.reader_menu),
                onClick = onMenu,
                tint = reader.ink,
            )
        }
    }
}

@Composable
private fun BoardsSearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    headerContent: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BOARD_DIRECTORY_GUTTER, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SchematicSearch(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(Res.string.next_boards_search),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(OrbinRadius.search),
        )
        headerContent()
    }
}

private fun LazyListScope.boardRows(
    boards: List<BoardTile>,
    keyPrefix: String,
    onOpenBoard: (BoardTile) -> Unit,
    onFollowBoard: ((BoardTile, Boolean) -> Unit)?,
    showSite: Boolean,
) {
    items(boards, key = { "$keyPrefix:${it.id}" }) { board ->
        BoardListRow(
            board = board,
            onClick = { onOpenBoard(board) },
            onFollow = onFollowBoard?.let { follow -> { checked -> follow(board, checked) } },
            showSite = showSite,
        )
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
    showSite: Boolean = true,
) {
    OrbinCard(
        modifier =
            Modifier
                .padding(horizontal = BOARD_DIRECTORY_GUTTER, vertical = 5.dp)
                .fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        board.path,
                        style = ReaderType.cardTitle.withReaderFont(),
                        color = reader.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        board.title,
                        style = ReaderType.cardTitle.withReaderFont().copy(fontWeight = FontWeight.Medium),
                        color = reader.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                val metadata =
                    listOfNotNull(
                        board.site?.let(::sourceLabel)?.takeIf { showSite && it.isNotBlank() },
                        board.threadCount?.let { count -> threadCountLabel(count) },
                        stringResource(Res.string.next_boards_nsfw).takeIf { board.nsfw },
                    ).joinToString(" · ")
                if (metadata.isNotBlank()) {
                    Text(
                        metadata,
                        style = ReaderType.postDetails.withReaderFont(),
                        color = reader.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onFollow != null) {
                FollowChip(board = board, onFollow = onFollow)
            }
            BoardIcon(
                siteId = board.site ?: board.id.substringBefore('/'),
                boardId = board.path,
                title = board.title,
                modifier = Modifier.size(BOARD_THUMBNAIL_SIZE),
                shape = RoundedCornerShape(OrbinRadius.thumbnail),
                size = BOARD_THUMBNAIL_SIZE,
            )
        }
    }
}

@Composable
private fun FollowChip(
    board: BoardTile,
    onFollow: (Boolean) -> Unit,
) {
    val identity = board.site?.let { "${sourceLabel(it)} · ${board.path}" } ?: board.path
    val actionDescription =
        stringResource(
            if (board.followed) {
                Res.string.reader_board_unfollow
            } else {
                Res.string.reader_board_follow
            },
            identity,
        )
    val shape = RoundedCornerShape(OrbinRadius.pill)
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
}

@Composable
private fun threadCountLabel(count: Int): String {
    val amount = formatThreadCount(count)
    return pluralStringResource(Res.plurals.next_boards_thread_count, count, amount)
}

/** Whole thousands use "42k"; one decimal place is kept above 10,000 ("42.1k"). */
internal fun formatThreadCount(count: Int): String {
    if (count < 10_000) return count.toString()
    val tenths = count / 100
    val whole = tenths / 10
    val fraction = tenths % 10
    return if (fraction == 0) "${whole}k" else "$whole.${fraction}k"
}

private val BOARD_DIRECTORY_GUTTER = 16.dp
private val BOARD_THUMBNAIL_SIZE = 56.dp
