package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun FeedHeader(
    subtitle: String,
    layout: FeedLayout,
    onLayoutChange: (FeedLayout) -> Unit,
    filter: String?,
    onClearFilter: () -> Unit,
    sortLabel: String? = null,
    onSort: () -> Unit = {},
    omittedWithoutPreview: Int = 0,
    sizeValue: Float = GRID_MIN_CELL.value,
    onSizeChange: (Float) -> Unit = {},
    showSizeControl: Boolean = true,
) {
    val sizeDescription = stringResource(R.string.next_media_size_control)
    Column {
        ScreenTitle(text = stringResource(R.string.next_feed_title), subtitle = subtitle)
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup().padding(horizontal = GUTTER - 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            InlineAction(
                label = stringResource(R.string.next_layout_list),
                selected = layout == FeedLayout.LIST,
                onClick = { onLayoutChange(FeedLayout.LIST) },
            )
            InlineAction(
                label = stringResource(R.string.next_layout_grid),
                selected = layout == FeedLayout.GRID,
                onClick = { onLayoutChange(FeedLayout.GRID) },
            )
            InlineAction(
                label = stringResource(R.string.next_layout_images),
                selected = layout == FeedLayout.IMAGES,
                onClick = { onLayoutChange(FeedLayout.IMAGES) },
            )
            if (sortLabel != null) {
                InlineAction("$sortLabel ▾", onClick = onSort)
            }
        }
        if (showSizeControl) {
            Gap(8)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetaLine(stringResource(R.string.next_media_size_small))
                Slider(
                    value = sizeValue,
                    onValueChange = onSizeChange,
                    valueRange = FEED_SIZE_MIN_DP..FEED_SIZE_MAX_DP,
                    steps = FEED_SIZE_STEPS,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                            .semantics { contentDescription = sizeDescription },
                )
                MetaLine(stringResource(R.string.next_media_size_large))
            }
        }
        if (layout == FeedLayout.IMAGES && omittedWithoutPreview > 0) {
            Gap(8)
            MetaLine(
                pluralStringResource(
                    R.plurals.next_feed_images_omitted,
                    omittedWithoutPreview,
                    omittedWithoutPreview,
                ),
                modifier = Modifier.padding(horizontal = GUTTER),
                color = next.faint,
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
internal fun FeedRowView(
    row: FeedRow,
    seed: Int,
    modifier: Modifier = Modifier,
    showBoard: Boolean = true,
    onClick: (FeedRow) -> Unit = {},
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)? = null,
    activityText: @Composable (FeedRow) -> String = { it.activity },
    sizeScale: Float = 1f,
) {
    if (row.muted) {
        CollapsedFeedRow(row = row, modifier = modifier, showBoard = showBoard, onClick = onClick)
        return
    }
    val clampedScale = sizeScale.coerceIn(0.7f, 1.45f)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.next_open_thread),
                ) { onClick(row) }
                .padding(horizontal = GUTTER, vertical = (15f * clampedScale).dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
                MetaLine(activityText(row), color = next.faint)
            }
            Gap(7)
            Text(
                text = row.subject,
                fontSize = 16.5.sp,
                lineHeight = 22.sp,
                letterSpacing = (-0.2).sp,
                fontWeight = if (row.read) FontWeight.Normal else FontWeight.SemiBold,
                color = if (row.read) next.muted else next.ink,
                maxLines = subjectLines(),
                overflow = TextOverflow.Ellipsis,
            )
            Gap(6)
            MetaLine(rowCounts(row))
        }
        if (row.hasPreview) {
            WidthSpacer((14f * clampedScale).toInt())
            val tile =
                Modifier.size(
                    width = LIST_TILE_WIDTH * clampedScale,
                    height = LIST_TILE_HEIGHT * clampedScale,
                )
            if (thumbnail != null) {
                thumbnail(row, tile)
            } else {
                MediaTile(modifier = tile, seed = seed, radius = 14.dp)
            }
        }
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
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.next_open_thread),
                ) { onClick(row) },
    ) {
        val tile = Modifier.fillMaxWidth().aspectRatio(GRID_TILE_ASPECT)
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
            MetaLine(activityText(row), color = next.faint)
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

@Composable
internal fun FeedImageCell(
    row: FeedRow,
    seed: Int,
    onClick: (FeedRow) -> Unit,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)?,
    tileHeight: Dp = 124.dp,
) {
    if (row.muted) {
        CollapsedFeedRow(row = row, modifier = Modifier.padding(2.5.dp), onClick = onClick)
        return
    }
    val description = stringResource(R.string.next_image_cell_description, row.subject, row.board)
    Box(
        modifier =
            Modifier
                .padding(2.5.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.next_open_thread),
                ) { onClick(row) }
                .semantics { contentDescription = description },
        contentAlignment = Alignment.BottomStart,
    ) {
        val tile = Modifier.fillMaxWidth().height(tileHeight)
        if (thumbnail != null) thumbnail(row, tile) else MediaTile(modifier = tile, seed = seed, radius = 10.dp)
        Pill(
            text = row.board,
            tint = boardHue(row.board),
            modifier = Modifier.padding(6.dp).widthIn(max = 104.dp),
        )
    }
}

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
                .clip(RoundedCornerShape(10.dp))
                .clickable(
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
internal fun subjectLines(): Int {
    val scale = LocalDensity.current.fontScale
    return when {
        scale >= 1.75f -> 4
        scale >= 1.3f -> 3
        else -> 2
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
