package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_boards_no_match
import com.orbin.uinext.resources.next_boards_nsfw
import com.orbin.uinext.resources.next_boards_random
import com.orbin.uinext.resources.next_boards_search
import com.orbin.uinext.resources.next_boards_thread_count
import com.orbin.uinext.resources.next_status_retry
import com.orbin.uinext.resources.reader_board_favorite
import com.orbin.uinext.resources.reader_board_follow
import com.orbin.uinext.resources.reader_board_open
import com.orbin.uinext.resources.reader_board_unfavorite
import com.orbin.uinext.resources.reader_board_unfollow
import com.orbin.uinext.resources.reader_boards_title
import com.orbin.uinext.resources.reader_favorite_boards
import com.orbin.uinext.resources.reader_feed_all_boards
import com.orbin.uinext.resources.reader_followed_boards
import com.orbin.uinext.resources.reader_menu
import com.orbin.uinext.resources.reader_open_thread_link
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.OrbinType
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
    /** Independent shortcut membership; favoriting never changes subscriptions. */
    val favorite: Boolean = false,
)

internal data class BoardDirectorySections(
    val favorites: List<BoardTile>,
    val followed: List<BoardTile>,
    val directory: List<BoardTile>,
)

internal fun boardDirectorySections(boards: List<BoardTile>): BoardDirectorySections =
    BoardDirectorySections(
        favorites = boards.filter { it.favorite },
        followed = boards.filter { it.followed && !it.favorite },
        directory = boards.filter { !it.followed && !it.favorite },
    )

object BoardsTags {
    const val HEADER = "boards-home-header"
}

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
    downloadBadge: Int = 0,
    onOpenLink: (() -> Unit)? = null,
    onFavoriteBoard: ((BoardTile, Boolean) -> Unit)? = null,
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
    val sections = remember(visibleBoards) { boardDirectorySections(visibleBoards) }
    val favorites = sections.favorites
    val directory = sections.directory
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
    val boardsTitle = stringResource(Res.string.reader_boards_title)
    val allBoardsLabel = stringResource(Res.string.reader_feed_all_boards)
    val showChrome = showRail && hasTabs && !LocalOrbinNavigationAtSide.current
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
        val density = LocalDensity.current
        var barHeightPx by remember { mutableIntStateOf(0) }
        val barHeight = with(density) { barHeightPx.toDp() }
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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BoardsHomeHeader(
                    subtitle = subtitle ?: boardsTitle,
                    showMenu = openDrawer != null,
                    onMenu = { openDrawer?.invoke() },
                    onOpenLink = onOpenLink,
                )
                BoardsSearchRow(
                    query = query,
                    onQueryChange = { query = it },
                    headerContent = headerContent,
                )
                if (onRandom != null) {
                    InlineAction(
                        stringResource(Res.string.next_boards_random),
                        onClick = onRandom,
                        modifier = Modifier.padding(horizontal = BOARD_DIRECTORY_GUTTER),
                    )
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxSize()
                            .align(Alignment.TopCenter)
                            .background(reader.background)
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                            ),
                    contentPadding =
                        PaddingValues(
                            top = 8.dp,
                            bottom =
                                (
                                    if (showChrome && onDestination != null) {
                                        barHeight
                                    } else {
                                        bottomPad.calculateBottomPadding()
                                    }
                                ) + NextSpace.groupGap,
                        ),
                ) {
                    if (visibleBoards.isEmpty()) {
                        item(key = "boards-empty") {
                            Text(
                                (
                                    emptyMessage?.takeIf { boards.isEmpty() && query.isBlank() }
                                        ?: stringResource(Res.string.next_boards_no_match)
                                ),
                                style = OrbinType.body,
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
                                BoardSectionTitle(stringResource(Res.string.reader_favorite_boards))
                            }
                            boardRows(
                                boards = favorites,
                                keyPrefix = "favorite",
                                onOpenBoard = onOpenBoard,
                                onFollowBoard = onFollowBoard,
                                onFavoriteBoard = onFavoriteBoard,
                                showSite = true,
                            )
                        }
                        if (sections.followed.isNotEmpty()) {
                            item(
                                key = "followed-heading",
                            ) { BoardSectionTitle(stringResource(Res.string.reader_followed_boards)) }
                            boardRows(
                                boards = sections.followed,
                                keyPrefix = "followed",
                                onOpenBoard = onOpenBoard,
                                onFollowBoard = onFollowBoard,
                                onFavoriteBoard = onFavoriteBoard,
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
                                onFavoriteBoard = onFavoriteBoard,
                                showSite = siteLabel.isBlank(),
                            )
                        }
                    }
                }
                if (showChrome && onDestination != null) {
                    OrbinBottomBar(
                        selected = NextDestination.BOARDS,
                        onSelect = onDestination,
                        destinations =
                            buildList {
                                add(NextDestination.BOARDS)
                                if (onOpenFeed != null) add(NextDestination.FEED)
                                if (onOpenDownloads != null) add(NextDestination.DOWNLOADS)
                                if (onOpenSettings != null) add(NextDestination.SETTINGS)
                            },
                        downloadBadge = downloadBadge,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .onSizeChanged { barHeightPx = it.height }
                                .offset { IntOffset(0, if (railVisible) 0 else barHeightPx) }
                                .then(if (railVisible) Modifier else Modifier.clearAndSetSemantics {}),
                    )
                }
            } // Box
        }
    }
}

@Composable
private fun BoardsHomeHeader(
    subtitle: String,
    showMenu: Boolean,
    onMenu: () -> Unit,
    onOpenLink: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BOARD_DIRECTORY_GUTTER, vertical = 8.dp)
            .heightIn(min = 46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrbinLogo(size = 38.dp, contentDescription = null)
        Column(
            Modifier.weight(1f).padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                OrbinStyle.WORDMARK,
                style =
                    OrbinType.appBarTitle.withReaderFont().copy(
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.6).sp,
                    ),
                color = reader.ink,
                modifier =
                    Modifier.testTag(BoardsTags.HEADER).clearAndSetSemantics {
                        contentDescription = OrbinStyle.WORDMARK
                        heading()
                    },
            )
            Text(
                subtitle,
                style = OrbinType.footnote.withReaderFont().copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                color = reader.inkSecondary,
            )
        }
        if (onOpenLink != null) {
            NextIconAction(
                imageVector = Icons.Outlined.Link,
                contentDescription = stringResource(Res.string.reader_open_thread_link),
                onClick = onOpenLink,
                tint = reader.ink,
            )
        }
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
    Column(
        Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(horizontal = BOARD_DIRECTORY_GUTTER, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        headerContent()
        SchematicSearch(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(Res.string.next_boards_search),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
    }
}

private fun LazyListScope.boardRows(
    boards: List<BoardTile>,
    keyPrefix: String,
    onOpenBoard: (BoardTile) -> Unit,
    onFollowBoard: ((BoardTile, Boolean) -> Unit)?,
    onFavoriteBoard: ((BoardTile, Boolean) -> Unit)?,
    showSite: Boolean,
) {
    itemsIndexed(boards, key = { _, board -> "$keyPrefix:${board.id}" }) { index, board ->
        BoardListRow(
            board = board,
            onClick = { onOpenBoard(board) },
            onFollow = onFollowBoard?.let { follow -> { checked -> follow(board, checked) } },
            onFavorite = onFavoriteBoard?.let { favorite -> { checked -> favorite(board, checked) } },
            showSite = showSite,
            first = index == 0,
            last = index == boards.lastIndex,
        )
    }
}

@Composable
private fun BoardSectionTitle(label: String) {
    Text(
        label,
        style =
            OrbinType.bodyMedium.withReaderFont().copy(
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp,
            ),
        color = next.ink,
        modifier =
            Modifier
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 28.dp,
                    bottom = 12.dp,
                ).semantics { heading() },
    )
}

@Composable
private fun BoardListRow(
    board: BoardTile,
    onClick: () -> Unit,
    onFollow: ((Boolean) -> Unit)? = null,
    onFavorite: ((Boolean) -> Unit)? = null,
    showSite: Boolean = true,
    first: Boolean = true,
    last: Boolean = true,
) {
    val identity = board.site?.let { "${sourceLabel(it)} · ${board.path}" } ?: board.path
    val openLabel = stringResource(Res.string.reader_board_open, identity)
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val shape =
        RoundedCornerShape(
            topStart = if (first) 20.dp else 0.dp,
            topEnd = if (first) 20.dp else 0.dp,
            bottomStart = if (last) 20.dp else 0.dp,
            bottomEnd = if (last) 20.dp else 0.dp,
        )
    // Each keyed row remains lazy; adjacent surfaces form one rounded Neko-style group.
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BOARD_DIRECTORY_GUTTER)
            .padding(bottom = if (last) 12.dp else 0.dp)
            .clip(shape)
            .background(reader.surfaceVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = MIN_TOUCH_TARGET)
                        .semantics { contentDescription = openLabel }
                        .nextClickable(role = Role.Button, onClickLabel = openLabel, onClick = onClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BoardIcon(
                    siteId = board.site ?: board.id.substringBefore('/'),
                    boardId = board.path,
                    title = board.title,
                    modifier = Modifier.size(BOARD_THUMBNAIL_SIZE),
                    shape = RoundedCornerShape(13.dp),
                    size = BOARD_THUMBNAIL_SIZE,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        board.title,
                        style =
                            OrbinType.cardTitle.withReaderFont().copy(
                                fontSize = 15.5.sp,
                                lineHeight = 21.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        color = reader.ink,
                        maxLines = if (largeText) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val metadata =
                        listOfNotNull(
                            board.path,
                            board.site?.let(::sourceLabel)?.takeIf { showSite && it.isNotBlank() },
                            board.threadCount?.let { count -> threadCountLabel(count) },
                            stringResource(Res.string.next_boards_nsfw).takeIf { board.nsfw },
                        ).joinToString(" · ")
                    Text(
                        metadata,
                        style = OrbinType.meta.withReaderFont().copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                        color = reader.inkSecondary,
                        maxLines = if (largeText) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onFavorite != null) FavoriteChip(board, onFavorite)
            if (onFollow != null) FollowChip(board, onFollow)
        }
        if (!last) ReaderHairline(Modifier.padding(start = 68.dp))
    }
}

@Composable
private fun FavoriteChip(
    board: BoardTile,
    onFavorite: (Boolean) -> Unit,
) {
    val identity = board.site?.let { "${sourceLabel(it)} · ${board.path}" } ?: board.path
    val description =
        stringResource(
            if (board.favorite) Res.string.reader_board_unfavorite else Res.string.reader_board_favorite,
            identity,
        )
    Box(
        Modifier
            .size(MIN_TOUCH_TARGET)
            .nextClickable(role = Role.Button) { onFavorite(!board.favorite) }
            .semantics {
                contentDescription = description
                selected = board.favorite
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (board.favorite) ReaderIcons.StarFilled else ReaderIcons.Star,
            contentDescription = null,
            tint = if (board.favorite) reader.accent else reader.inkSecondary,
            modifier = Modifier.size(20.dp),
        )
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
            if (board.followed) Res.string.reader_board_unfollow else Res.string.reader_board_follow,
            identity,
        )
    Box(
        Modifier
            .size(MIN_TOUCH_TARGET)
            .nextClickable(role = Role.Button) { onFollow(!board.followed) }
            .semantics {
                contentDescription = actionDescription
                selected = board.followed
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (board.followed) ReaderIcons.HeartFilled else ReaderIcons.Heart,
            contentDescription = null,
            tint = if (board.followed) Color(0xFFF472B6) else reader.inkSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun threadCountLabel(count: Int): String {
    val amount = formatThreadCount(count)
    return pluralStringResource(Res.plurals.next_boards_thread_count, count, amount)
}

/** Whole thousands use "42k"; one decimal place is kept above 10,000 ("42.1k"). */
private const val COMPACT_THREAD_COUNT_THRESHOLD = 10_000
private const val THREAD_COUNT_TENTHS_DIVISOR = 100
private const val THREAD_COUNT_DECIMAL_BASE = 10

internal fun formatThreadCount(count: Int): String {
    if (count < COMPACT_THREAD_COUNT_THRESHOLD) return count.toString()
    val tenths = count / THREAD_COUNT_TENTHS_DIVISOR
    val whole = tenths / THREAD_COUNT_DECIMAL_BASE
    val fraction = tenths % THREAD_COUNT_DECIMAL_BASE
    return if (fraction == 0) "${whole}k" else "$whole.${fraction}k"
}

private val BOARD_DIRECTORY_GUTTER = 16.dp
private val BOARD_THUMBNAIL_SIZE = 44.dp
