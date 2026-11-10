package com.orbin.uinext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_feed_title
import com.orbin.uinext.resources.next_row_counts
import com.orbin.uinext.resources.next_row_counts_unread
import com.orbin.uinext.resources.next_row_files
import com.orbin.uinext.resources.next_row_no_image
import com.orbin.uinext.resources.next_row_replies
import com.orbin.uinext.resources.next_search_threads_prompt
import com.orbin.uinext.resources.next_settings_title
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FeedHeader(
    subtitle: String,
    headerContent: @Composable () -> Unit = {},
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    onSettings: (() -> Unit)? = null,
    twoColumns: Boolean = false,
    onToggleColumns: (() -> Unit)? = null,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = NextSpace.titleTop),
        ) {
            ScreenTitle(
                text = stringResource(Res.string.next_feed_title),
                subtitle = subtitle,
                modifier = Modifier.weight(1f),
                inset = 0.dp,
            )
            if (onToggleColumns != null) {
                val latchShape = RoundedCornerShape(NextRadius.tight)
                Box(
                    modifier =
                        Modifier
                            .clip(latchShape)
                            .background(if (twoColumns) next.accentContainer else next.raised)
                            .border(1.dp, if (twoColumns) next.accent else next.hairline, latchShape)
                            .clickable(onClick = onToggleColumns)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (twoColumns) "[2-COL]" else "[1-COL]",
                        style = NextType.telemetry,
                        color = if (twoColumns) next.accent else next.muted,
                    )
                }
            }
            if (onSettings != null) {
                NextIconAction(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(Res.string.next_settings_title),
                    onClick = onSettings,
                    tint = next.muted,
                )
            }
        }
        // Machine telemetry status bar
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NextRadius.tight))
                    .background(next.raised)
                    .border(1.dp, next.hairline, RoundedCornerShape(NextRadius.tight))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(next.accent),
                )
                Text(
                    text = "CH-01: MULTI-CHANNEL // LIVE",
                    style = NextType.telemetry,
                    color = next.accent,
                )
                Text(
                    text = "·",
                    style = NextType.telemetry,
                    color = next.hairline,
                )
                Text(
                    text = "PROVIDER: 4CHAN/LYNX",
                    style = NextType.telemetry,
                    color = next.muted,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "SIG:NOMINAL",
                    style = NextType.telemetry,
                    color = next.faint,
                )
            }
        }
        Gap(10)
        Column(Modifier.padding(horizontal = 0.dp)) {
            headerContent()
            Gap(10)
            SchematicSearch(query, onQueryChange, stringResource(Res.string.next_search_threads_prompt))
            Gap(8)
        }
        Gap(10)
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
    // How much of the opening post shows under the media.
    excerptLines: Int = 2,
    // A card as wide as the screen can show portrait media nearly whole, where a grid tile must
    // letterbox it.
    tallMedia: Boolean = false,
    // Every card the same height: media in a fixed frame (centre-cropped) and each line of text
    // reserving its space, so a feed of mixed portrait and landscape posts lines up evenly.
    uniform: Boolean = false,
) {
    if (row.muted) {
        CollapsedFeedRow(
            row = row,
            modifier = modifier.padding(if (tallMedia) GRID_CELL_PADDING else 4.dp),
            showBoard = showBoard,
            onClick = onClick,
        )
        return
    }
    Card(
        onClick = { onClick(row) },
        modifier =
            modifier
                .padding(if (tallMedia) GRID_CELL_PADDING else 4.dp)
                .semantics { role = Role.Button },
        shape = RoundedCornerShape(NextRadius.tight),
        border = BorderStroke(1.dp, next.hairline),
        colors =
            CardDefaults.cardColors(
                containerColor = next.raised,
                contentColor = next.ink,
            ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Machined top color band
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(if (row.read) next.hairline else boardHue(row.board)),
            )

            // Technical Spec Header Strip
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (tallMedia) GRID_TEXT_INSET else 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showBoard) {
                    BoardDot(row.board, size = 6.dp)
                    Text(
                        text = row.board,
                        style = NextType.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (row.read) next.muted else boardHue(row.board),
                    )
                    Text(
                        text = "·",
                        style = NextType.telemetry,
                        color = next.hairline,
                    )
                }
                if (row.threadNumber.isNotBlank()) {
                    Text(
                        text = "#${row.threadNumber}",
                        style = NextType.telemetry,
                        color = if (row.read) next.muted else next.accent,
                    )
                }
                Spacer(Modifier.weight(1f))
                MetaLine(activityText(row), color = next.faint)
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (row.unread > 0) next.accent else next.hairline.copy(alpha = 0.4f)),
                )
            }

            HorizontalDivider(thickness = 1.dp, color = next.hairline)

            val tile =
                Modifier.fillMaxWidth().let {
                    if (uniform) it.aspectRatio(UNIFORM_TILE_ASPECT) else it.mediaTileSize(row, tallMedia)
                }
            Box(modifier = tile) {
                if (row.hasPreview && thumbnail != null) {
                    thumbnail(row, Modifier.matchParentSize())
                } else if (row.hasPreview) {
                    MediaTile(modifier = Modifier.matchParentSize(), seed = seed, radius = NextRadius.tight)
                } else {
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(NextRadius.tight))
                                .background(next.ink.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        MetaLine(stringResource(Res.string.next_row_no_image), color = next.faint)
                    }
                }
                if (row.hasPreview) {
                    val reticleColor = next.accent.copy(alpha = 0.55f)
                    Canvas(Modifier.matchParentSize()) {
                        val stroke = 1.25.dp.toPx()
                        val arm = 8.dp.toPx()
                        val pad = 6.dp.toPx()
                        val w = size.width
                        val h = size.height
                        // 4 corner viewfinder brackets
                        drawLine(reticleColor, Offset(pad, pad), Offset(pad + arm, pad), stroke)
                        drawLine(reticleColor, Offset(pad, pad), Offset(pad, pad + arm), stroke)
                        drawLine(reticleColor, Offset(w - pad - arm, pad), Offset(w - pad, pad), stroke)
                        drawLine(reticleColor, Offset(w - pad, pad), Offset(w - pad, pad + arm), stroke)
                        drawLine(reticleColor, Offset(pad, h - pad - arm), Offset(pad, h - pad), stroke)
                        drawLine(reticleColor, Offset(pad, h - pad), Offset(pad + arm, h - pad), stroke)
                        drawLine(reticleColor, Offset(w - pad - arm, h - pad), Offset(w - pad, h - pad), stroke)
                        drawLine(reticleColor, Offset(w - pad, h - pad), Offset(w - pad, h - pad - arm), stroke)
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = next.hairline)

            // Spec-Sheet Body Cassette
            Column(
                modifier =
                    Modifier.padding(
                        horizontal = if (tallMedia) GRID_TEXT_INSET else 8.dp,
                        vertical = 8.dp,
                    ),
            ) {
                Text(
                    text = row.subject,
                    style = if (tallMedia) NextType.title3 else NextType.headline,
                    fontWeight = if (row.read) FontWeight.Medium else FontWeight.Bold,
                    color = if (row.read) next.muted else next.ink,
                    maxLines = if (tallMedia && !uniform) 3 else 2,
                    minLines = if (uniform) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Gap(6)
                if (uniform) {
                    MetaLine(row.excerpt, maxLines = excerptLines, minLines = excerptLines)
                    Gap(6)
                } else if (row.excerpt.isNotBlank()) {
                    MetaLine(row.excerpt, maxLines = excerptLines)
                    Gap(6)
                }
                val footerShape = RoundedCornerShape(NextRadius.tight)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(footerShape)
                            .background(next.background)
                            .border(1.dp, next.hairline, footerShape)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val countPrefix =
                        if (row.unread > 0) "${rowCounts(row)} · +${row.unread} NEW" else rowCounts(row)
                    val threadInfo =
                        if (row.threadNumber.isNotBlank()) "#${row.threadNumber} · $countPrefix" else countPrefix
                    Text(
                        text = threadInfo,
                        style = NextType.telemetry,
                        color = if (row.unread > 0) next.accent else next.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = "OPEN ↗",
                        style = NextType.telemetry,
                        fontWeight = FontWeight.Bold,
                        color = next.accent,
                    )
                }
            }
        }
    }
}

/**
 * Sizes a grid tile to its media's own aspect ratio, so the thumbnail can be drawn whole instead of
 * centre-cropped into a fixed-height box. Extreme ratios are clamped to keep a single tile from
 * dwarfing the screen; the image inside still fits without cropping, letterboxed if needed.
 */
private fun Modifier.mediaTileSize(
    row: FeedRow,
    tallMedia: Boolean,
): Modifier =
    if (row.hasPreview && row.mediaAspectRatio > 0f) {
        val minAspect = if (tallMedia) MIN_TALL_TILE_ASPECT else MIN_TILE_ASPECT
        aspectRatio(row.mediaAspectRatio.coerceIn(minAspect, MAX_TILE_ASPECT))
    } else {
        height(FALLBACK_TILE_HEIGHT)
    }

// Tall media is letterboxed at 4:5 in a grid, so one portrait image cannot fill the screen; the
// whole image is one tap away.
private const val MIN_TILE_ASPECT = 0.8f

// In a single column, portrait media shows whole down to 9:16, a phone screenshot; only taller is
// letterboxed.
private const val MIN_TALL_TILE_ASPECT = 0.5625f
private const val MAX_TILE_ASPECT = 3f

// The one frame every card's media fills when cards are uniform.
private const val UNIFORM_TILE_ASPECT = 4f / 3f
private val FALLBACK_TILE_HEIGHT = 240.dp

/** A muted thread stays reachable, but loses its preview and metadata until the reader opens it. */
@Composable
private fun CollapsedFeedRow(
    row: FeedRow,
    modifier: Modifier = Modifier,
    showBoard: Boolean = true,
    onClick: (FeedRow) -> Unit,
) {
    Card(
        onClick = { onClick(row) },
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { role = Role.Button },
        shape = RoundedCornerShape(NextRadius.tight),
        border = BorderStroke(1.dp, next.hairline),
        colors =
            CardDefaults.cardColors(
                containerColor = next.raised,
                contentColor = next.ink,
            ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(next.hairline),
            )
            Text(
                text = "[MUTED]",
                style = NextType.telemetry,
                color = next.muted,
            )
            if (showBoard) {
                BoardDot(row.board, size = 5.dp)
                Text(
                    text = row.board,
                    style = NextType.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = next.muted,
                )
            }
            Text(
                text = row.subject,
                modifier = Modifier.weight(1f),
                style = NextType.bodySmall,
                fontWeight = FontWeight.Normal,
                color = next.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun rowCounts(row: FeedRow): String {
    val replies = pluralStringResource(Res.plurals.next_row_replies, row.replies, row.replies)
    val files = pluralStringResource(Res.plurals.next_row_files, row.media, row.media)
    return if (row.unread > 0) {
        stringResource(Res.string.next_row_counts_unread, replies, row.unread)
    } else {
        stringResource(Res.string.next_row_counts, replies, files)
    }
}
