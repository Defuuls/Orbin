package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One thread as it appears in the feed. */
data class FeedRow(
    val subject: String,
    val board: String,
    val activity: String,
    val replies: Int,
    val media: Int,
    val hasPreview: Boolean = true,
    val read: Boolean = false,
)

/**
 * The subscribed feed: every board you follow, merged and sorted by activity.
 *
 * What changed from the current feed, and why:
 *
 * The old row is a card — elevated, rounded, padded on four sides, separated from its neighbours
 * by a gap — carrying a thumbnail, subject, comment preview, board chip, reply count, media count,
 * a date, and a subscribe toggle. Nine pieces of information and three containers per thread. At
 * a glance you are reading furniture.
 *
 * This row is the subject, one meta line, and the preview. The subject is what you are choosing
 * between; the meta line answers "from where, how busy, how recently" in one pass because those
 * three facts are always read together; the preview earns its place because on an imageboard the
 * image frequently *is* the post. Everything else — subscribing, hiding, opening the board — moves
 * to a press-and-hold on the row, where it is available without being permanently displayed.
 *
 * A read thread is not dimmed into illegibility as it is now; its subject simply loses its weight.
 */
@Composable
fun FeedScreen(
    rows: List<FeedRow>,
    modifier: Modifier = Modifier,
) {
    Surface {
        Column(modifier = modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    ScreenTitle(
                        text = "Feed",
                        subtitle = "${rows.size} threads · 7 boards",
                    )
                }
                items(rows) { row ->
                    FeedRowView(row)
                    Hairline(inset = true)
                }
            }
            ContextRail(where = "Feed", detail = "7 boards")
        }
    }
}

@Composable
private fun FeedRowView(row: FeedRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.subject,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = if (row.read) FontWeight.Normal else FontWeight.Medium,
                color =
                    MaterialTheme.colorScheme.onBackground.copy(
                        alpha = if (row.read) 0.70f else 1f,
                    ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Gap(6)
            MetaLine("${row.board}  ·  ${row.replies} replies  ·  ${row.media} files  ·  ${row.activity}")
        }
        if (row.hasPreview) {
            WidthSpacer(14)
            MediaTile(modifier = Modifier.size(56.dp))
        }
    }
}

/**
 * A board catalog. The same row, the same rules — a catalog is a feed scoped to one board, so it
 * should not be a second layout with its own conventions, as it is today.
 *
 * The layout switcher (list / grid / image-only) and the sort order used to live as two icons in
 * the top bar of every catalog. They are one inline control here, shown once at the top of the
 * list and scrolling away with it.
 */
@Composable
fun BoardScreen(
    board: String,
    description: String,
    rows: List<FeedRow>,
    modifier: Modifier = Modifier,
) {
    Surface {
        Column(modifier = modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    ScreenTitle(text = board, subtitle = description)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 4.dp),
                    ) {
                        InlineAction("List", accent = true)
                        WidthSpacer(18)
                        InlineAction("Grid")
                        WidthSpacer(18)
                        InlineAction("Images")
                        Box(modifier = Modifier.weight(1f))
                        InlineAction("Recent ▾")
                    }
                    Gap(10)
                }
                items(rows) { row ->
                    FeedRowView(row)
                    Hairline(inset = true)
                }
            }
            ContextRail(where = board, detail = "catalog")
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
        Column(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f)) {
                ScreenTitle(text = "All media", subtitle = "Every file from every board you follow")
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER)) {
                    MetaLine("Swept $scanned of $total boards")
                    Box(modifier = Modifier.weight(1f))
                    if (failed > 0) MetaLine("$failed unreachable")
                }
                Gap(12)
                Column(modifier = Modifier.fillMaxWidth()) {
                    repeat(5) { rowIndex ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            repeat(3) { column ->
                                val n = rowIndex * 3 + column
                                MediaTile(
                                    modifier = Modifier.weight(1f).height(124.dp),
                                    tone = if ((n + rowIndex) % 3 == 0) 0.14f else 0.09f,
                                    badge = if (n % 4 == 0) "/g/" else null,
                                )
                                if (column < 2) WidthSpacer(2)
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth().size(2.dp))
                    }
                }
            }
            ContextRail(where = "All media", detail = "$scanned/$total")
        }
    }
}

@Composable
internal fun EmptyBackdrop() {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
}
