package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.tokens.NextRadius

@Composable
internal fun FeedHeader(
    subtitle: String,
    filter: String?,
    onClearFilter: () -> Unit,
    sortLabel: String? = null,
    onSort: () -> Unit = {},
    headerContent: @Composable () -> Unit = {},
    query: String = "",
    onQueryChange: (String) -> Unit = {},
) {
    Column {
        ScreenTitle(text = stringResource(R.string.next_feed_title), subtitle = subtitle)
        Column(Modifier.padding(horizontal = 0.dp)) {
            headerContent()
            Gap(12)
            SchematicSearch(query, onQueryChange, "Sift through your threads")
            Gap(8)
        }
        // Primary destinations live in DestinationPill and refreshing is pull-to-refresh; the header keeps sort only.
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER - 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (sortLabel != null) {
                InlineAction("$sortLabel ▾", onClick = onSort)
            }
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
internal fun FeedGridCell(
    row: FeedRow,
    seed: Int,
    onClick: (FeedRow) -> Unit,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
    activityText: @Composable (FeedRow) -> String = { it.activity },
    // Off wherever a heading above the card already names the board: a board's catalog, or a feed
    // grouped by board.
    showBoard: Boolean = true,
) {
    if (row.muted) {
        CollapsedFeedRow(row = row, modifier = modifier.padding(GRID_CELL_PADDING), onClick = onClick)
        return
    }
    Column(
        modifier =
            modifier
                .padding(GRID_CELL_PADDING)
                .clip(RoundedCornerShape(GRID_TILE_RADIUS))
                .background(next.raised)
                .nextClickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.next_open_thread),
                ) { onClick(row) },
    ) {
        val tile = Modifier.fillMaxWidth().mediaTileSize(row)
        if (row.hasPreview && thumbnail != null) {
            thumbnail(row, tile)
        } else if (row.hasPreview) {
            MediaTile(modifier = tile, seed = seed, radius = GRID_TILE_RADIUS)
        } else {
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
        // One inset for every line of text, so the board, subject and counts share a left edge.
        Column(modifier = Modifier.padding(horizontal = GRID_TEXT_INSET)) {
            Gap(10)
            if (showBoard) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BoardDot(row.board, size = 7.dp)
                    WidthSpacer(6)
                    Text(
                        text = row.board,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (row.read) next.muted else boardHue(row.board),
                    )
                }
                Gap(3)
            }
            MetaLine(activityText(row), color = next.faint)
            Gap(6)
            Text(
                text = row.subject,
                fontSize = 15.5.sp,
                lineHeight = 20.sp,
                letterSpacing = (-0.15).sp,
                fontWeight = if (row.read) FontWeight.Normal else FontWeight.SemiBold,
                color = if (row.read) next.muted else next.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Gap(6)
            if (row.excerpt.isNotBlank()) {
                MetaLine(row.excerpt, maxLines = 2)
                Gap(6)
            }
            val threadInfo =
                if (row.threadNumber.isNotBlank()) "#${row.threadNumber} · ${rowCounts(row)}" else rowCounts(row)
            MetaLine(threadInfo, maxLines = 2)
        }
        Gap(12)
    }
}

/**
 * Sizes a grid tile to its media's own aspect ratio, so the thumbnail can be drawn whole instead of
 * centre-cropped into a fixed-height box. Extreme ratios are clamped to keep a single tile from
 * dwarfing the screen; the image inside still fits without cropping, letterboxed if needed.
 */
private fun Modifier.mediaTileSize(row: FeedRow): Modifier =
    if (row.hasPreview && row.mediaAspectRatio > 0f) {
        aspectRatio(row.mediaAspectRatio.coerceIn(MIN_TILE_ASPECT, MAX_TILE_ASPECT))
    } else {
        height(FALLBACK_TILE_HEIGHT)
    }

private const val MIN_TILE_ASPECT = 0.5f
private const val MAX_TILE_ASPECT = 3f
private val FALLBACK_TILE_HEIGHT = 240.dp

/** A muted thread stays reachable, but loses its preview and metadata until the reader opens it. */
@Composable
private fun CollapsedFeedRow(
    row: FeedRow,
    modifier: Modifier = Modifier,
    showBoard: Boolean = true,
    onClick: (FeedRow) -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NextRadius.tight))
                .nextClickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.next_open_thread),
                ) { onClick(row) }
                .padding(horizontal = GUTTER, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBoard) {
            BoardDot(row.board, size = 5.dp)
            WidthSpacer(7)
            Text(
                text = row.board,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = next.muted,
            )
            WidthSpacer(9)
        }
        Text(
            text = row.subject,
            modifier = Modifier.weight(1f),
            fontSize = 13.5.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Normal,
            color = next.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun rowCounts(row: FeedRow): String {
    val replies = pluralStringResource(R.plurals.next_row_replies, row.replies, row.replies)
    val files = pluralStringResource(R.plurals.next_row_files, row.media, row.media)
    return if (row.unread > 0) {
        stringResource(R.string.next_row_counts_unread, replies, row.unread)
    } else {
        stringResource(R.string.next_row_counts, replies, files)
    }
}
