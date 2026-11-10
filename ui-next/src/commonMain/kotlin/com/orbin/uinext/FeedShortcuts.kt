package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.social_feed_favorite_boards
import com.orbin.uinext.resources.social_feed_shortcut
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.stringResource

/** Shortcuts are derived from subscribed feed rows; no sample boards or remote data live here. */
@Composable
internal fun FavoriteBoardShortcuts(
    rows: List<FeedRow>,
    selectedBoard: String?,
    onSelected: (String) -> Unit,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)?,
) {
    val boards = remember(rows) { rows.distinctBy { it.sourceAndBoard } }
    if (boards.isEmpty()) return
    Column {
        Text(
            stringResource(Res.string.social_feed_favorite_boards),
            style = NextType.footnote,
            color = next.muted,
            modifier = Modifier.padding(horizontal = GUTTER, vertical = 8.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(horizontal = GUTTER),
        ) {
            items(boards, key = { it.sourceAndBoard }) { row ->
                val description = stringResource(Res.string.social_feed_shortcut, row.sourceAndBoard)
                Column(
                    modifier =
                        Modifier
                            .width(76.dp)
                            .semantics {
                                contentDescription = description
                                selected = selectedBoard == row.sourceAndBoard
                            }.nextClickable(role = Role.Button) { onSelected(row.sourceAndBoard) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(next.raised)
                                .border(
                                    if (selectedBoard ==
                                        row.sourceAndBoard
                                    ) {
                                        2.dp
                                    } else {
                                        1.dp
                                    },
                                    next.hairline,
                                    CircleShape,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (row.hasPreview && thumbnail != null) {
                            thumbnail(row, Modifier.matchParentSize())
                        } else {
                            Text(row.board.trim('/').take(3), style = NextType.titleMedium, color = next.ink)
                        }
                    }
                    Text(
                        row.board,
                        style = NextType.footnote,
                        color = next.ink,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (row.site.isNotBlank()) Text(row.site, style = NextType.caption1, color = next.muted)
                }
            }
        }
        Gap(12)
        Hairline()
    }
}
