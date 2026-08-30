package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How the feed draws its threads.
 *
 * The same three the current feed offers, because they answer three different questions: [LIST]
 * for "what is being talked about", [GRID] for "what is being talked about, with the picture", and
 * [IMAGES] for "what has been posted", which on an imageboard is a question people genuinely open
 * the app to ask.
 */
enum class FeedLayout {
    LIST,
    GRID,
    IMAGES,
}

/**
 * One thread as it appears in the feed.
 *
 * [id] is what the list is keyed on and what a click reports back, so the screen never has to know
 * what a thread actually is. Everything else is already-formatted display text: this type is the
 * whole contract between the interface and whatever is feeding it.
 */
data class FeedRow(
    val subject: String,
    val board: String,
    val activity: String,
    val replies: Int,
    val media: Int,
    val hasPreview: Boolean = true,
    val read: Boolean = false,
    val id: String = "$board:$subject",
)

/**
 * The subscribed feed: every board you follow, merged and sorted by activity.
 *
 * What changed from the current feed, and why:
 *
 * The old row is a card — elevated, rounded, padded on four sides, separated from its neighbours by
 * a gap — carrying a thumbnail, subject, comment preview, board chip, reply count, media count, a
 * date, and a subscribe toggle. Nine pieces of information and three containers per thread. At a
 * glance you are reading furniture.
 *
 * This row is three lines and a picture. An eyebrow says where it came from and how recently, in the
 * board's own colour, so a merged feed can be sorted by eye; the subject is what you are actually
 * choosing between, so it is the only thing set in full weight; the counts sit under it where they
 * can be ignored. The preview earns its place because on an imageboard the image frequently *is* the
 * post. Everything else — subscribing, hiding, opening the board — moves to a press-and-hold on the
 * row, where it is available without being permanently displayed.
 *
 * A read thread is not dimmed into illegibility as it is now; its subject simply loses its weight
 * and its board dot goes quiet.
 */
@Composable
fun FeedScreen(
    rows: List<FeedRow>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    railDetail: String? = null,
    showRail: Boolean = true,
    layout: FeedLayout = FeedLayout.LIST,
    onLayoutChange: (FeedLayout) -> Unit = {},
    filter: String? = null,
    onClearFilter: () -> Unit = {},
    onOpenRow: (FeedRow) -> Unit = {},
    railAction: String = stringResource(R.string.next_action_search),
    onSearch: () -> Unit = {},
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)? = null,
    hideRailOnScroll: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    scrollToTopRequest: Int = 0,
) {
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    // Keyed on the request alone. With `layout` as a second key the effect re-fired on every
    // layout switch, and since the body only asks whether a request has ever been made, anyone who
    // had used scroll-to-top once was sent back to the top every time they changed layout.
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) {
            if (layout == FeedLayout.LIST) {
                listState.animateScrollToItem(0)
            } else {
                gridState.animateScrollToItem(0)
            }
        }
    }
    // Scrolling down puts the rail away and the feed edge to edge; any scroll back brings it
    // straight home. This is what the old shell's "full-screen feed" setting drove when there
    // were two bars to hide.
    val railVisible =
        if (!hideRailOnScroll) {
            true
        } else if (layout == FeedLayout.LIST) {
            scrollingUp({ listState.firstVisibleItemIndex }, { listState.firstVisibleItemScrollOffset })
        } else {
            scrollingUp({ gridState.firstVisibleItemIndex }, { gridState.firstVisibleItemScrollOffset })
        }
    LaunchedEffect(railVisible) { onChromeVisibleChange(railVisible) }

    // A thread with nothing to show would be an empty cell in a wall of pictures, which reads as a
    // broken tile rather than as a text thread. Filtered here rather than inside the grid's content
    // lambda, where it ran again on every recomposition instead of once per list.
    val withPreview = remember(rows) { rows.filter { it.hasPreview } }
    NextScaffold(
        where = stringResource(R.string.next_feed_title).takeIf { showRail },
        modifier = modifier,
        detail = railDetail,
        action = railAction,
        onSearch = onSearch,
        railVisible = railVisible,
    ) { bottomPad ->
        // The header is the list's first item rather than a band above it, so it scrolls away
        // with the content: a title tells you what you opened and stops being useful once you
        // are reading, and the layout switch is a choice you make on arriving, not a control
        // worth a permanent strip of the display.
        val header: @Composable () -> Unit = {
            FeedHeader(
                subtitle = subtitle ?: pluralStringResource(R.plurals.next_feed_thread_count, rows.size, rows.size),
                layout = layout,
                onLayoutChange = onLayoutChange,
                filter = filter,
                onClearFilter = onClearFilter,
            )
        }
        val insets = Modifier.fillMaxSize().contentInsets()
        when (layout) {
            FeedLayout.LIST ->
                LazyColumn(
                    state = listState,
                    modifier = insets,
                    contentPadding = bottomPad,
                ) {
                    item(key = FEED_HEADER_KEY) { header() }
                    itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                        FeedRowView(
                            row,
                            seed = index,
                            // The feed sorts by activity, so a refresh really does reorder the
                            // rows. Keyed lists can animate that; unanimated they teleport.
                            modifier = Modifier.animateItem(),
                            onClick = onOpenRow,
                            thumbnail = thumbnail,
                        )
                        if (index < rows.lastIndex) Hairline(inset = true)
                    }
                }

            FeedLayout.GRID ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(GRID_MIN_CELL),
                    state = gridState,
                    modifier = insets,
                    contentPadding = gridPadding(bottomPad),
                ) {
                    fullWidthItem { header() }
                    itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                        FeedGridCell(
                            row,
                            seed = index,
                            onClick = onOpenRow,
                            thumbnail = thumbnail,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

            FeedLayout.IMAGES ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(IMAGE_MIN_CELL),
                    state = gridState,
                    modifier = insets,
                    contentPadding = gridPadding(bottomPad),
                ) {
                    fullWidthItem { header() }
                    itemsIndexed(withPreview, key = { _, row -> row.id }) { index, row ->
                        FeedImageCell(row, seed = index, onClick = onOpenRow, thumbnail = thumbnail)
                    }
                }
        }
    }
}

/**
 * The title, the layout switcher, and the filter when one is active.
 *
 * The switcher was two icons in the top bar of the current feed, permanently present and
 * ambiguous. Here it is three words at the top of the list, and it scrolls away with the title.
 */
@Composable
private fun FeedHeader(
    subtitle: String,
    layout: FeedLayout,
    onLayoutChange: (FeedLayout) -> Unit,
    filter: String?,
    onClearFilter: () -> Unit,
) {
    // Its own Column: a header that relies on its caller's layout stacks on top of itself the
    // moment it is put somewhere else — which is exactly what happened when it moved into the grid.
    Column {
        ScreenTitle(text = stringResource(R.string.next_feed_title), subtitle = subtitle)
        // selectableGroup so the three read as one control with one chosen option, rather than
        // as three unrelated buttons that happen to sit together.
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup().padding(horizontal = GUTTER - 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InlineAction(
                label = stringResource(R.string.next_layout_list),
                selected = layout == FeedLayout.LIST,
                onClick = { onLayoutChange(FeedLayout.LIST) },
            )
            WidthSpacer(4)
            InlineAction(
                label = stringResource(R.string.next_layout_grid),
                selected = layout == FeedLayout.GRID,
                onClick = { onLayoutChange(FeedLayout.GRID) },
            )
            WidthSpacer(4)
            InlineAction(
                label = stringResource(R.string.next_layout_images),
                selected = layout == FeedLayout.IMAGES,
                onClick = { onLayoutChange(FeedLayout.IMAGES) },
            )
        }
        if (filter != null) {
            Gap(10)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER - 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(stringResource(R.string.next_filter_label))
                WidthSpacer(8)
                MetaLine(filter, modifier = Modifier.weight(1f))
                InlineAction(stringResource(R.string.next_filter_clear), onClick = onClearFilter)
            }
        }
        Gap(12)
        Hairline()
    }
}

@Composable
private fun FeedRowView(
    row: FeedRow,
    seed: Int,
    modifier: Modifier = Modifier,
    showBoard: Boolean = true,
    onClick: (FeedRow) -> Unit = {},
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                // A row is one thing to press, so it is one thing to hear: `clickable` merges the
                // descendants under it, and naming the role is what makes it announce as a button
                // rather than as an unlabelled tap target with four stray labels inside it.
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.next_open_thread),
                ) { onClick(row) }
                .padding(horizontal = GUTTER, vertical = 15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Inside a board's own catalog the board label would be the same on every row, so
            // the eyebrow is just the time there.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showBoard) {
                    BoardDot(row.board, size = if (row.read) 5.dp else 6.dp)
                    WidthSpacer(7)
                    Text(
                        text = row.board,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                        color = if (row.read) next.muted else boardHue(row.board),
                    )
                    WidthSpacer(8)
                }
                MetaLine(row.activity, color = next.faint)
            }
            Gap(7)
            Text(
                text = row.subject,
                fontSize = 16.5.sp,
                lineHeight = 22.sp,
                letterSpacing = (-0.2).sp,
                fontWeight = if (row.read) FontWeight.Normal else FontWeight.SemiBold,
                color = if (row.read) next.muted else next.ink,
                // Two lines hold a subject at the default size and cut one in half at twice it, so
                // the reader who most needs large text was the one losing the most of the title.
                maxLines = subjectLines(),
                overflow = TextOverflow.Ellipsis,
            )
            Gap(6)
            MetaLine(rowCounts(row))
        }
        if (row.hasPreview) {
            WidthSpacer(14)
            // Landscape rather than square. The tile fits the whole attachment rather than
            // cropping it, and against a square that left a wide file rendering tiny — a 16:9
            // image came out 68x38 with empty bands above and below. Widening to 88 gives it
            // 88x50 for 20dp of text column, which is the whole trade: a taller or square file
            // is drawn no smaller than before, it just sits between side gutters instead.
            //
            // 88 and not wider because the title is what pays. At 96 the subject on a typical
            // row wraps to a third line, so every row grows and fewer threads fit on screen —
            // a worse deal than the extra 8dp of picture is worth.
            val tile = Modifier.size(width = LIST_TILE_WIDTH, height = LIST_TILE_HEIGHT)
            // A real thumbnail when something supplies one; stand-in artwork when nothing does,
            // which is what keeps this screen renderable on its own.
            if (thumbnail != null) {
                thumbnail(row, tile)
            } else {
                MediaTile(modifier = tile, seed = seed, radius = 14.dp)
            }
        }
    }
}

/** A grid cell: the picture, then what the thread is about, then how busy it is. */
@Composable
private fun FeedGridCell(
    row: FeedRow,
    seed: Int,
    onClick: (FeedRow) -> Unit,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .padding(GRID_CELL_PADDING)
                .clip(RoundedCornerShape(GRID_TILE_RADIUS))
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.next_open_thread),
                ) { onClick(row) },
    ) {
        // An aspect ratio rather than a fixed height: the cell keeps its proportion as the column
        // widens on a tablet or in landscape, where a fixed height would letterbox it.
        val tile = Modifier.fillMaxWidth().aspectRatio(GRID_TILE_ASPECT)
        if (row.hasPreview && thumbnail != null) {
            thumbnail(row, tile)
        } else if (row.hasPreview) {
            MediaTile(modifier = tile, seed = seed, radius = GRID_TILE_RADIUS)
        } else {
            // A text thread keeps its cell rather than collapsing: the grid is still a list of
            // threads, and a missing picture is not a missing thread.
            Box(
                modifier =
                    tile
                        .clip(RoundedCornerShape(GRID_TILE_RADIUS))
                        .background(next.ink.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center,
            ) {
                MetaLine(stringResource(R.string.next_row_no_image), color = next.faint)
            }
        }
        Gap(12)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BoardDot(row.board, size = 6.dp)
            WidthSpacer(6)
            Text(
                text = row.board,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (row.read) next.muted else boardHue(row.board),
            )
            WidthSpacer(6)
            MetaLine(row.activity, color = next.faint)
        }
        Gap(4)
        Text(
            text = row.subject,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = (-0.1).sp,
            fontWeight = if (row.read) FontWeight.Normal else FontWeight.Medium,
            color = if (row.read) next.muted else next.ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Gap(4)
        MetaLine(rowCounts(row))
        Gap(10)
    }
}

/** An image-only cell: the picture, and the board it came from. Nothing else. */
@Composable
private fun FeedImageCell(
    row: FeedRow,
    seed: Int,
    onClick: (FeedRow) -> Unit,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)?,
) {
    val description =
        stringResource(R.string.next_image_cell_description, row.subject, row.board)
    Box(
        modifier =
            Modifier
                .padding(2.5.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.next_open_thread),
                ) { onClick(row) }
                // This cell draws a picture and a board badge and nothing else, so without a
                // description of its own a reader hears an attachment's filename and a board name
                // and cannot tell one thread from another. Stated here rather than drawn, because
                // showing nothing but the picture is the whole point of this layout.
                .semantics { contentDescription = description },
        contentAlignment = Alignment.BottomStart,
    ) {
        val tile = Modifier.fillMaxWidth().height(124.dp)
        if (thumbnail != null) thumbnail(row, tile) else MediaTile(modifier = tile, seed = seed, radius = 10.dp)
        Pill(
            text = row.board,
            tint = boardHue(row.board),
            modifier = Modifier.padding(6.dp).widthIn(max = 104.dp),
        )
    }
}

/**
 * A board catalog: the same row as the feed, the same three layouts.
 *
 * A catalog is a feed scoped to one board, so it is not a second layout with its own conventions
 * as it was before. The board label drops off the rows, because inside one board it would repeat
 * on every one of them.
 *
 * Rows arrive by index rather than as a list because the catalog is paged: reading index *n* is
 * what asks for the page containing it, and handing this screen a finished list would quietly
 * stop it ever loading a second page. [rowAt] returning null means that row has not arrived yet.
 */
@Composable
fun BoardScreen(
    board: String,
    description: String,
    itemCount: Int,
    rowAt: (Int) -> FeedRow?,
    modifier: Modifier = Modifier,
    layout: FeedLayout = FeedLayout.LIST,
    onLayoutChange: (FeedLayout) -> Unit = {},
    sortLabel: String? = null,
    onSort: () -> Unit = {},
    showRail: Boolean = true,
    onOpenRow: (FeedRow) -> Unit = {},
    onSearch: () -> Unit = {},
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)? = null,
    hideRailOnScroll: Boolean = false,
) {
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    // The same hide-on-scroll the feed has. It used to be the feed's alone, so a catalog — the same
    // rows, the same layouts, documented as such — kept its rail pinned while the feed's slid away.
    val railVisible =
        if (!hideRailOnScroll) {
            true
        } else if (layout == FeedLayout.LIST) {
            scrollingUp({ listState.firstVisibleItemIndex }, { listState.firstVisibleItemScrollOffset })
        } else {
            scrollingUp({ gridState.firstVisibleItemIndex }, { gridState.firstVisibleItemScrollOffset })
        }
    NextScaffold(
        where = board.takeIf { showRail },
        modifier = modifier,
        detail = stringResource(R.string.next_rail_catalog),
        onSearch = onSearch,
        railVisible = railVisible,
    ) { bottomPad ->
        // The board's name and its layout switch scroll away with the catalogue, the same as
        // the feed's: which layout you are in is visible from the rows themselves.
        val header: @Composable () -> Unit = {
            Column {
                Row(
                    modifier = Modifier.padding(start = GUTTER, top = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoardDot(board, size = 10.dp)
                    WidthSpacer(10)
                    Text(
                        text = board,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.9).sp,
                        color = next.ink,
                    )
                }
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = next.muted,
                    modifier = Modifier.padding(start = GUTTER, top = 6.dp),
                )
                Gap(16)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectableGroup()
                            .padding(horizontal = GUTTER - 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InlineAction(
                        label = stringResource(R.string.next_layout_list),
                        selected = layout == FeedLayout.LIST,
                        onClick = { onLayoutChange(FeedLayout.LIST) },
                    )
                    WidthSpacer(4)
                    InlineAction(
                        label = stringResource(R.string.next_layout_grid),
                        selected = layout == FeedLayout.GRID,
                        onClick = { onLayoutChange(FeedLayout.GRID) },
                    )
                    WidthSpacer(4)
                    InlineAction(
                        label = stringResource(R.string.next_layout_images),
                        selected = layout == FeedLayout.IMAGES,
                        onClick = { onLayoutChange(FeedLayout.IMAGES) },
                    )
                    Box(modifier = Modifier.weight(1f))
                    if (sortLabel != null) {
                        InlineAction("$sortLabel ▾", onClick = onSort)
                    }
                }
                Gap(12)
                Hairline()
            }
        }
        val insets = Modifier.fillMaxSize().contentInsets()
        when (layout) {
            FeedLayout.LIST ->
                LazyColumn(state = listState, modifier = insets, contentPadding = bottomPad) {
                    item(key = FEED_HEADER_KEY) { header() }
                    items(itemCount) { index ->
                        val row = rowAt(index)
                        if (row != null) {
                            FeedRowView(
                                row,
                                seed = index,
                                showBoard = false,
                                onClick = onOpenRow,
                                thumbnail = thumbnail,
                            )
                        } else {
                            PendingRow()
                        }
                        if (index < itemCount - 1) Hairline(inset = true)
                    }
                }

            FeedLayout.GRID ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(GRID_MIN_CELL),
                    state = gridState,
                    modifier = insets,
                    contentPadding = gridPadding(bottomPad),
                ) {
                    fullWidthItem { header() }
                    items(itemCount) { index ->
                        rowAt(index)?.let { row ->
                            FeedGridCell(row, seed = index, onClick = onOpenRow, thumbnail = thumbnail)
                        }
                    }
                }

            FeedLayout.IMAGES ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(IMAGE_MIN_CELL),
                    state = gridState,
                    modifier = insets,
                    contentPadding = gridPadding(bottomPad),
                ) {
                    fullWidthItem { header() }
                    items(itemCount) { index ->
                        rowAt(index)?.takeIf { it.hasPreview }?.let { row ->
                            FeedImageCell(row, seed = index, onClick = onOpenRow, thumbnail = thumbnail)
                        }
                    }
                }
        }
    }
}

/**
 * How many lines a subject gets, which is not a constant.
 *
 * Enlarged type means fewer words per line, so a fixed two lines truncates more the larger the
 * setting — exactly backwards. The count rises with the scale in force, which is the app's own font
 * setting multiplied onto the system's.
 */
@Composable
private fun subjectLines(): Int {
    val scale = LocalDensity.current.fontScale
    return when {
        scale >= 1.75f -> 4
        scale >= 1.3f -> 3
        else -> 2
    }
}

/**
 * A row's two counts, as one line.
 *
 * Both halves are plurals rather than a number with an "s" stuck on it — the shipped line read
 * "1 replies" — and the joiner is a resource too, because which side the counts fall on and what
 * separates them is not the same in every language.
 */
@Composable
private fun rowCounts(row: FeedRow): String =
    stringResource(
        R.string.next_row_counts,
        pluralStringResource(R.plurals.next_row_replies, row.replies, row.replies),
        pluralStringResource(R.plurals.next_row_files, row.media, row.media),
    )

/** A row whose page has not arrived: the shape of a row, so the list does not jump when it does. */
@Composable
private fun PendingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PendingBar(width = 54.dp, height = 11.dp)
            Gap(9)
            PendingBar(width = 240.dp, height = 15.dp)
            Gap(8)
            PendingBar(width = 120.dp, height = 11.dp)
        }
    }
}

@Composable
private fun PendingBar(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier =
            Modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(3.dp))
                .background(next.hairline),
    )
}

/** One file on the wall. [id] is what a tap reports back; [board] tints its badge. */
data class MediaCell(
    val id: String,
    val board: String,
)

/**
 * The all-media wall: every file from every board you follow, as one continuous grid.
 *
 * The sweep's progress used to be a determinate bar, a line of text and a deep-scan toggle in the
 * top bar. It is one line here, above the grid, and it disappears when the sweep finishes — a
 * progress bar that never goes away is just decoration.
 */
@Composable
fun MediaWallScreen(
    scanned: Int,
    total: Int,
    failed: Int,
    modifier: Modifier = Modifier,
    cells: List<MediaCell> = emptyList(),
    scanning: Boolean = false,
    deepScanning: Boolean = false,
    deepScanned: Int = 0,
    deepTotal: Int = 0,
    showRail: Boolean = true,
    onOpen: (MediaCell) -> Unit = {},
    onSearch: () -> Unit = {},
    tile: (@Composable (MediaCell, Modifier) -> Unit)? = null,
    hideRailOnScroll: Boolean = false,
) {
    val gridState = rememberLazyGridState()
    val railVisible =
        if (hideRailOnScroll) {
            scrollingUp({ gridState.firstVisibleItemIndex }, { gridState.firstVisibleItemScrollOffset })
        } else {
            true
        }
    NextScaffold(
        where = stringResource(R.string.next_all_media_title).takeIf { showRail },
        modifier = modifier,
        detail =
            if (total > 0) {
                stringResource(R.string.next_rail_swept, scanned, total)
            } else {
                null
            },
        onSearch = onSearch,
        railVisible = railVisible,
    ) { bottomPad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(IMAGE_MIN_CELL),
            state = gridState,
            modifier = Modifier.fillMaxSize().contentInsets(),
            contentPadding = gridPadding(bottomPad),
        ) {
            // The title and the sweep's progress are the grid's first item, so they scroll
            // away with it. The rail keeps reporting the sweep once they have.
            fullWidthItem {
                Column {
                    ScreenTitle(
                        text = stringResource(R.string.next_all_media_title),
                        subtitle = stringResource(R.string.next_all_media_subtitle),
                    )
                    // Only while something is happening. A finished sweep has nothing to
                    // report.
                    if (scanning || deepScanning || failed > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (deepScanning) {
                                SweepBar(scanned = deepScanned, total = deepTotal)
                                WidthSpacer(12)
                                MetaLine(stringResource(R.string.next_sweep_reading, deepScanned, deepTotal))
                            } else if (scanning) {
                                SweepBar(scanned = scanned, total = total)
                                WidthSpacer(12)
                                MetaLine(stringResource(R.string.next_sweep_progress, scanned, total))
                            }
                            Box(modifier = Modifier.weight(1f))
                            if (failed > 0) {
                                MetaLine(
                                    stringResource(R.string.next_sweep_unreachable, failed),
                                    color = next.accent,
                                )
                            }
                        }
                        Gap(16)
                    }
                }
            }
            itemsIndexed(cells, key = { _, cell -> cell.id }) { index, cell ->
                val description =
                    stringResource(R.string.next_media_cell_description, cell.board)
                Box(
                    modifier =
                        Modifier
                            .padding(2.5.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(R.string.next_open_file),
                            ) {
                                onOpen(cell)
                            }
                            // Same reason as the feed's image cells: a picture and a board
                            // badge is not enough for a reader to choose between files.
                            .semantics { contentDescription = description },
                    contentAlignment = Alignment.BottomStart,
                ) {
                    val shape = Modifier.fillMaxWidth().height(124.dp)
                    if (tile != null) {
                        tile(cell, shape)
                    } else {
                        MediaTile(modifier = shape, seed = index, radius = 10.dp)
                    }
                    Pill(
                        text = cell.board,
                        tint = boardHue(cell.board),
                        modifier = Modifier.padding(6.dp).widthIn(max = 104.dp),
                    )
                }
            }
        }
    }
}

/** The sweep's progress, as a line rather than a Material progress bar with a label beside it. */
@Composable
private fun SweepBar(
    scanned: Int,
    total: Int,
) {
    Box(
        modifier =
            Modifier
                .width(96.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(next.hairline),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(scanned.toFloat() / total.coerceAtLeast(1))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(next.accent),
        )
    }
}

/**
 * One board you can put in your feed, or take out of it.
 *
 * [id] is the board's short name without the slashes — the screen draws those — and is also what a
 * toggle reports back, so the caller never has to match on the label it supplied.
 */
data class BoardChoice(
    val id: String,
    val title: String,
    val subscribed: Boolean = false,
)

/**
 * The board picker: which boards feed the feed.
 *
 * Drawn as the settings list is drawn, because it is the same act — a long list of things that are
 * on or off, where the state of the whole list should be one glance down the right-hand edge. It
 * was a Material top bar with a back arrow, a refresh icon and a checkbox per row; the checkbox in
 * particular made the list read as a form to submit rather than a set of switches that take effect
 * as you press them, which is what it has always actually been.
 *
 * The title scrolls away with the rows, like every other title here. Refresh is a word inline
 * under it rather than an icon pinned in a bar, so it is unambiguous and costs nothing once you
 * have scrolled past it — the list is not stale twice.
 */
@Composable
fun BoardPickerScreen(
    boards: List<BoardChoice>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showRail: Boolean = true,
    railAction: String = stringResource(R.string.next_feed_title),
    onToggle: (BoardChoice) -> Unit = {},
    onRefresh: () -> Unit = {},
    onSearch: () -> Unit = {},
) {
    val subscribed = remember(boards) { boards.count { it.subscribed } }
    NextScaffold(
        where = stringResource(R.string.next_boards_title).takeIf { showRail },
        modifier = modifier,
        detail = stringResource(R.string.next_rail_subscribed, subscribed),
        action = railAction,
        onSearch = onSearch,
    ) { bottomPad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().contentInsets(),
            contentPadding = bottomPad,
        ) {
            item(key = FEED_HEADER_KEY) {
                Column {
                    ScreenTitle(
                        text = stringResource(R.string.next_boards_title),
                        subtitle =
                            subtitle
                                ?: stringResource(
                                    R.string.next_boards_summary,
                                    subscribed,
                                    boards.size,
                                ),
                    )
                    Row(modifier = Modifier.padding(start = GUTTER - 4.dp)) {
                        InlineAction(label = stringResource(R.string.next_boards_refresh), onClick = onRefresh)
                    }
                    Gap(12)
                    Hairline()
                }
            }
            itemsIndexed(boards, key = { _, board -> board.id }) { index, board ->
                Column {
                    BoardChoiceRow(board = board, onToggle = onToggle)
                    if (index < boards.lastIndex) Hairline(inset = true)
                }
            }
        }
    }
}

/**
 * A board's row: its dot and name, its title under that, and where it stands on the right.
 *
 * The state is a word rather than a checkbox, and both words are spelled out. "Subscribed" against
 * a blank is a list you have to infer the meaning of; "Subscribed" against "Not subscribed" is one
 * you can read down.
 */
@Composable
private fun BoardChoiceRow(
    board: BoardChoice,
    onToggle: (BoardChoice) -> Unit,
) {
    val name = "/${board.id}/"
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // `toggleable` rather than `clickable(role = Role.Switch)`: the latter announces a
                // switch but has nowhere to put its value, so the control said what it was without
                // ever saying which way it was set. The row's own "Subscribed" / "Not subscribed"
                // text covered for that, and would have stopped covering the moment it became an
                // icon.
                .toggleable(
                    value = board.subscribed,
                    role = Role.Switch,
                    onValueChange = { onToggle(board) },
                ).padding(horizontal = GUTTER, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoardDot(name, size = 8.dp)
        WidthSpacer(12)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 15.5.sp,
                letterSpacing = (-0.1).sp,
                color = next.ink,
            )
            MetaLine(text = board.title, modifier = Modifier.padding(top = 3.dp))
        }
        WidthSpacer(12)
        Text(
            text =
                if (board.subscribed) {
                    stringResource(R.string.next_board_subscribed)
                } else {
                    stringResource(R.string.next_board_not_subscribed)
                },
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (board.subscribed) next.accent else next.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Whether the list is moving up the page, remembered across scroll events.
 *
 * Compared by item index and offset rather than by a raw delta so a fling reads as one direction
 * rather than flickering, and so the rail is always present at the top of the list.
 *
 * One function over both list and grid: these were two byte-identical copies, differing only in
 * which state type they read. The previous position is updated from an effect rather than from
 * inside the derivation — writing snapshot state while computing a derived value is a side effect
 * in a read-only context, and it only holds while exactly one caller reads it.
 */
@Composable
private fun scrollingUp(
    firstVisibleItemIndex: () -> Int,
    firstVisibleItemScrollOffset: () -> Int,
): Boolean {
    var lastIndex by remember { mutableIntStateOf(0) }
    var lastOffset by remember { mutableIntStateOf(0) }
    val up =
        remember {
            derivedStateOf {
                val index = firstVisibleItemIndex()
                val offset = firstVisibleItemScrollOffset()
                when {
                    index == 0 && offset == 0 -> true
                    lastIndex != index -> lastIndex > index
                    else -> lastOffset >= offset
                }
            }
        }.value
    LaunchedEffect(firstVisibleItemIndex(), firstVisibleItemScrollOffset()) {
        lastIndex = firstVisibleItemIndex()
        lastOffset = firstVisibleItemScrollOffset()
    }
    return up
}

private val LIST_TILE_WIDTH = 88.dp
private val LIST_TILE_HEIGHT = 68.dp

/** Slightly taller than wide, which is the shape most thread images end up being. */
private const val GRID_TILE_ASPECT = 1.1f

/**
 * The narrowest a cell may be before the grid drops a column.
 *
 * Fixed column counts are what these were, and on a phone they were right — two and three. On a
 * tablet or in landscape they drew the same two enormous cells across a display twice as wide,
 * which is a capability the screens this replaced already had: the old catalog, gallery and board
 * grids all size themselves this way. The minimums are the widths the fixed counts produced at
 * 411dp, so a phone is laid out exactly as before and anything wider gains columns instead of
 * stretching.
 */
private val GRID_MIN_CELL = 170.dp
private val IMAGE_MIN_CELL = 112.dp
private val GRID_TILE_RADIUS = 16.dp
private val GRID_CELL_PADDING = 8.dp

/**
 * A header that spans every column of a grid rather than sitting in the first cell.
 *
 * Both grids need it and neither should have to spell out the span, which is the only fiddly part
 * of putting a header inside a lazy grid rather than in a band above it.
 */
private fun LazyGridScope.fullWidthItem(content: @Composable () -> Unit) =
    item(key = FEED_HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) { content() }

/**
 * A grid's padding: the scaffold's bottom clearance, plus the side inset the tiles sit in.
 *
 * The two grids inset by 14 rather than by [GUTTER] because a tile's own 2.5–8dp of padding makes
 * up the rest, so the pictures line up with the list's text edge.
 */
private fun gridPadding(bottom: PaddingValues) =
    PaddingValues(
        start = GRID_SIDE_INSET,
        end = GRID_SIDE_INSET,
        bottom = bottom.calculateBottomPadding(),
    )

private val GRID_SIDE_INSET = 14.dp

/** Keyed so a layout switch keeps the header identified across the list and the two grids. */
private const val FEED_HEADER_KEY = "header"
