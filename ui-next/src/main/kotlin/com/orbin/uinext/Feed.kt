package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    onOpenRow: (FeedRow) -> Unit = {},
    onSearch: () -> Unit = {},
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)? = null,
) {
    Surface {
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding =
                    PaddingValues(bottom = if (showRail) RAIL_HEIGHT + 28.dp else 16.dp),
            ) {
                item {
                    ScreenTitle(
                        text = "Feed",
                        subtitle = subtitle ?: "${rows.size} threads",
                    )
                }
                itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                    FeedRowView(row, seed = index, onClick = onOpenRow, thumbnail = thumbnail)
                    if (index < rows.lastIndex) Hairline(inset = true)
                }
            }
            if (showRail) {
                ContextRail(
                    where = "Feed",
                    detail = railDetail,
                    onSearch = onSearch,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
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

/**
 * A board catalog. The same row, the same rules — a catalog is a feed scoped to one board, so it
 * should not be a second layout with its own conventions, as it is today.
 *
 * The layout switcher (list / grid / image-only) and the sort order used to live as two icons in the
 * top bar of every catalog. They are one inline control here, shown once at the top of the list and
 * scrolling away with it.
 */
@Composable
fun BoardScreen(
    board: String,
    description: String,
    rows: List<FeedRow>,
    modifier: Modifier = Modifier,
) {
    Surface {
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(bottom = RAIL_HEIGHT + 28.dp)) {
                item {
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
                        InlineAction("List", accent = true)
                        WidthSpacer(4)
                        InlineAction("Grid")
                        WidthSpacer(4)
                        InlineAction("Images")
                        Box(modifier = Modifier.weight(1f))
                        InlineAction("Recent ▾")
                    }
                    Gap(12)
                }
                itemsIndexed(rows) { index, row ->
                    FeedRowView(row, seed = index + 2, showBoard = false)
                    if (index < rows.lastIndex) Hairline(inset = true)
                }
            }
            ContextRail(
                where = board,
                detail = "catalog",
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * The all-media wall: every file from every board you follow, as one continuous grid.
 *
 * The sweep's progress used to be a determinate bar plus a line of text plus a deep-scan toggle in
 * the top bar. It is one line here, above the grid, and it disappears when the sweep finishes.
 */
@Composable
fun MediaWallScreen(
    scanned: Int,
    total: Int,
    failed: Int,
    modifier: Modifier = Modifier,
) {
    Surface {
        Box(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle(text = "All media", subtitle = "Every file from every board you follow")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SweepBar(scanned = scanned, total = total)
                    WidthSpacer(12)
                    MetaLine("$scanned of $total")
                    Box(modifier = Modifier.weight(1f))
                    if (failed > 0) MetaLine("$failed unreachable", color = next.accent)
                }
                Gap(16)
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    repeat(5) { rowIndex ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            repeat(3) { column ->
                                val n = rowIndex * 3 + column
                                MediaTile(
                                    modifier = Modifier.weight(1f).height(126.dp),
                                    seed = n + rowIndex,
                                    badge = if (n % 4 == 0) "/g/" else null,
                                    radius = 10.dp,
                                )
                                if (column < 2) WidthSpacer(5)
                            }
                        }
                        Gap(5)
                    }
                }
            }
            ContextRail(
                where = "All media",
                detail = "$scanned/$total swept",
                modifier = Modifier.align(Alignment.BottomCenter),
            )
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
