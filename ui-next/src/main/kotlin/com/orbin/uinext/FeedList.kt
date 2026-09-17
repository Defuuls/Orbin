package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.NextType

@Composable
internal fun FeedGroupHeading(
    title: String,
    board: String,
    count: Int,
) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (title == board) board else "$title $board",
            style = NextType.title2,
            color = next.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        WidthSpacer(8)
        MetaLine("$count threads")
    }
}

@Composable
internal fun FeedListRow(
    row: FeedRow,
    seed: Int,
    onClick: (FeedRow) -> Unit,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)?,
    activityText: @Composable (FeedRow) -> String,
    first: Boolean,
    last: Boolean,
) {
    val radius = if (LocalNextPlatform.current == NextPlatform.IOS) 14.dp else 16.dp
    Column(
        Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    topStart = if (first) radius else 0.dp,
                    topEnd = if (first) radius else 0.dp,
                    bottomStart = if (last) radius else 0.dp,
                    bottomEnd = if (last) radius else 0.dp,
                ),
            ).background(next.raised)
            .nextClickable(role = Role.Button, onClick = { onClick(row) }),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            if (!row.muted) {
                val tile = Modifier.size(66.dp).clip(RoundedCornerShape(13.dp))
                when {
                    row.hasPreview && thumbnail != null -> thumbnail(row, tile)
                    row.hasPreview -> MediaTile(tile, seed = seed, radius = 13.dp)
                    else -> Box(tile.background(next.hairline))
                }
                WidthSpacer(13)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    row.subject,
                    style = NextType.headline,
                    color =
                        if (row.read ||
                            row.muted
                        ) {
                            next.muted
                        } else {
                            next.ink
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!row.muted) {
                    if (row.excerpt.isNotBlank()) {
                        Text(
                            row.excerpt,
                            style = NextType.subheadline,
                            color = next.muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Gap(4)
                    Row {
                        MetaLine("#${row.threadNumber}", color = next.accent)
                        WidthSpacer(6)
                        MetaLine("· ${rowCounts(row)} · ${activityText(row)}", modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (!last) Hairline(inset = true)
    }
}
