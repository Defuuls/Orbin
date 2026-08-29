package com.orbin.uinext

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.semantics.Role
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
    railAction: String = "Search",
    onSearch: () -> Unit = {},
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)? = null,
    hideRailOnScroll: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    scrollToTopRequest: Int = 0,
) {
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    LaunchedEffect(scrollToTopRequest, layout) {
        if (scrollToTopRequest > 0) {
            if (layout == FeedLayout.LIST) listState.animateScrollToItem(0) else gridState.animateScrollToItem(0)
        }
    }
    // Scrolling down puts the rail away and the feed edge to edge; any scroll back brings it
    // straight home. This is what the old shell's "full-screen feed" setting drove when there
    // were two bars to hide.
    val railVisible =
        if (!hideRailOnScroll) {
            true
        } else if (layout == FeedLayout.LIST) {
            scrollingUp(listState)
        } else {
            scrollingUpGrid(gridState)
        }
    LaunchedEffect(railVisible) { onChromeVisibleChange(railVisible) }

    val bottomPad = (if (showRail) RAIL_HEIGHT + 28.dp else 16.dp) + bottomInset()
    Surface {
        Box(modifier = modifier.fillMaxSize()) {
            // The header is the list's first item rather than a band above it, so it scrolls away
            // with the content: a title tells you what you opened and stops being useful once you
            // are reading, and the layout switch is a choice you make on arriving, not a control
            // worth a permanent strip of the display.
            val header: @Composable () -> Unit = {
                FeedHeader(
                    subtitle = subtitle ?: "${rows.size} threads",
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
                        contentPadding = PaddingValues(bottom = bottomPad),
                    ) {
                        item(key = FEED_HEADER_KEY) { header() }
                        itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                            FeedRowView(row, seed = index, onClick = onOpenRow, thumbnail = thumbnail)
                            if (index < rows.lastIndex) Hairline(inset = true)
                        }
                    }

                FeedLayout.GRID ->
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        modifier = insets,
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottomPad),
                    ) {
                        fullWidthItem { header() }
                        itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                            FeedGridCell(row, seed = index, onClick = onOpenRow, thumbnail = thumbnail)
                        }
                    }

                FeedLayout.IMAGES ->
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = insets,
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottomPad),
                    ) {
                        fullWidthItem { header() }
                        // A thread with nothing to show would be an empty cell in a wall of
                        // pictures, which reads as a broken tile rather than as a text thread.
                        val withPreview = rows.filter { it.hasPreview }
                        itemsIndexed(withPreview, key = { _, row -> row.id }) { index, row ->
                            FeedImageCell(row, seed = index, onClick = onOpenRow, thumbnail = thumbnail)
                        }
                    }
            }
            AnimatedVisibility(
                visible = showRail && railVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ContextRail(where = "Feed", detail = railDetail, action = railAction, onSearch = onSearch)
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
        ScreenTitle(text = "Feed", subtitle = subtitle)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER - 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InlineAction(
                label = "List",
                accent = layout == FeedLayout.LIST,
                onClick = { onLayoutChange(FeedLayout.LIST) },
            )
            WidthSpacer(4)
            InlineAction(
                label = "Grid",
                accent = layout == FeedLayout.GRID,
                onClick = { onLayoutChange(FeedLayout.GRID) },
            )
            WidthSpacer(4)
            InlineAction(
                label = "Images",
                accent = layout == FeedLayout.IMAGES,
                onClick = { onLayoutChange(FeedLayout.IMAGES) },
            )
        }
        if (filter != null) {
            Gap(10)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER - 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill("filter")
                WidthSpacer(8)
                MetaLine(filter, modifier = Modifier.weight(1f))
                InlineAction("Clear", onClick = onClearFilter)
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
    showBoard: Boolean = true,
    onClick: (FeedRow) -> Unit = {},
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick(row) }
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Gap(6)
            MetaLine("${row.replies} replies  ·  ${row.media} files")
        }
        if (row.hasPreview) {
            WidthSpacer(14)
            val tile = Modifier.size(68.dp)
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
) {
    Column(
        modifier =
            Modifier
                .padding(GRID_CELL_PADDING)
                .clip(RoundedCornerShape(GRID_TILE_RADIUS))
                .clickable { onClick(row) },
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
                MetaLine("no image", color = next.faint)
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
        MetaLine("${row.replies} replies  ·  ${row.media} files")
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
    Box(
        modifier =
            Modifier
                .padding(2.5.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onClick(row) },
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

/** [scrollingUp], for the grid layouts. */
@Composable
private fun scrollingUpGrid(state: LazyGridState): Boolean {
    var lastIndex by remember { mutableIntStateOf(0) }
    var lastOffset by remember { mutableIntStateOf(0) }
    return remember {
        derivedStateOf {
            if (state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0) {
                true
            } else {
                val up =
                    if (lastIndex != state.firstVisibleItemIndex) {
                        lastIndex > state.firstVisibleItemIndex
                    } else {
                        lastOffset >= state.firstVisibleItemScrollOffset
                    }
                lastIndex = state.firstVisibleItemIndex
                lastOffset = state.firstVisibleItemScrollOffset
                up
            }
        }
    }.value
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
) {
    val bottomPad = (if (showRail) RAIL_HEIGHT + 28.dp else 16.dp) + bottomInset()
    Surface {
        Box(modifier = modifier.fillMaxSize()) {
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER - 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InlineAction(
                            label = "List",
                            accent = layout == FeedLayout.LIST,
                            onClick = { onLayoutChange(FeedLayout.LIST) },
                        )
                        WidthSpacer(4)
                        InlineAction(
                            label = "Grid",
                            accent = layout == FeedLayout.GRID,
                            onClick = { onLayoutChange(FeedLayout.GRID) },
                        )
                        WidthSpacer(4)
                        InlineAction(
                            label = "Images",
                            accent = layout == FeedLayout.IMAGES,
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
            run {
                when (layout) {
                    FeedLayout.LIST ->
                        LazyColumn(modifier = insets, contentPadding = PaddingValues(bottom = bottomPad)) {
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
                            columns = GridCells.Fixed(2),
                            modifier = insets,
                            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottomPad),
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
                            columns = GridCells.Fixed(3),
                            modifier = insets,
                            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottomPad),
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
            if (showRail) {
                ContextRail(
                    where = board,
                    detail = "catalog",
                    onSearch = onSearch,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

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
) {
    Surface {
        Box(modifier = modifier.fillMaxSize()) {
            run {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().contentInsets(),
                    contentPadding =
                        PaddingValues(
                            start = 14.dp,
                            end = 14.dp,
                            bottom = (if (showRail) RAIL_HEIGHT + 28.dp else 16.dp) + bottomInset(),
                        ),
                ) {
                    // The title and the sweep's progress are the grid's first item, so they scroll
                    // away with it. The rail keeps reporting the sweep once they have.
                    fullWidthItem {
                        Column {
                            ScreenTitle(
                                text = "All media",
                                subtitle = "Every file from every board you follow",
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
                                        MetaLine("Reading $deepScanned of $deepTotal threads")
                                    } else if (scanning) {
                                        SweepBar(scanned = scanned, total = total)
                                        WidthSpacer(12)
                                        MetaLine("$scanned of $total")
                                    }
                                    Box(modifier = Modifier.weight(1f))
                                    if (failed > 0) MetaLine("$failed unreachable", color = next.accent)
                                }
                                Gap(16)
                            }
                        }
                    }
                    itemsIndexed(cells, key = { _, cell -> cell.id }) { index, cell ->
                        Box(
                            modifier =
                                Modifier
                                    .padding(2.5.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onOpen(cell) },
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
            if (showRail) {
                ContextRail(
                    where = "All media",
                    detail = if (total > 0) "$scanned/$total swept" else null,
                    onSearch = onSearch,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
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
    railAction: String = "Feed",
    onToggle: (BoardChoice) -> Unit = {},
    onRefresh: () -> Unit = {},
    onSearch: () -> Unit = {},
) {
    val subscribed = boards.count { it.subscribed }
    Surface {
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().contentInsets(),
                contentPadding =
                    PaddingValues(bottom = (if (showRail) RAIL_HEIGHT + 28.dp else 16.dp) + bottomInset()),
            ) {
                item(key = FEED_HEADER_KEY) {
                    Column {
                        ScreenTitle(
                            text = "Boards",
                            subtitle = subtitle ?: "$subscribed of ${boards.size} in your feed",
                        )
                        Row(modifier = Modifier.padding(start = GUTTER - 4.dp)) {
                            InlineAction(label = "Refresh", onClick = onRefresh)
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
            if (showRail) {
                ContextRail(
                    where = "Boards",
                    detail = "$subscribed subscribed",
                    action = railAction,
                    onSearch = onSearch,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
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
                .clickable(role = Role.Switch) { onToggle(board) }
                .padding(horizontal = GUTTER, vertical = 13.dp),
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
            text = if (board.subscribed) "Subscribed" else "Not subscribed",
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
 */
@Composable
private fun scrollingUp(state: LazyListState): Boolean {
    var lastIndex by remember { mutableIntStateOf(0) }
    var lastOffset by remember { mutableIntStateOf(0) }
    return remember {
        derivedStateOf {
            if (state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0) {
                true
            } else {
                val up =
                    if (lastIndex != state.firstVisibleItemIndex) {
                        lastIndex > state.firstVisibleItemIndex
                    } else {
                        lastOffset >= state.firstVisibleItemScrollOffset
                    }
                lastIndex = state.firstVisibleItemIndex
                lastOffset = state.firstVisibleItemScrollOffset
                up
            }
        }
    }.value
}

/** Slightly taller than wide, which is the shape most thread images end up being. */
private const val GRID_TILE_ASPECT = 1.1f
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

/** Keyed so a layout switch keeps the header identified across the list and the two grids. */
private const val FEED_HEADER_KEY = "header"
