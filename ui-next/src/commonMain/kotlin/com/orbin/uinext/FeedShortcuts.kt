package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.resources.Res
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
    val unreadBoards =
        remember(rows) {
            rows.filter { !it.read || it.unread > 0 }.mapTo(mutableSetOf()) { it.sourceAndBoard }
        }
    if (boards.isEmpty()) return
    Column {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        ) {
            items(boards, key = { it.sourceAndBoard }) { row ->
                val description = stringResource(Res.string.social_feed_shortcut, row.sourceAndBoard)
                Column(
                    modifier =
                        Modifier
                            .width(88.dp)
                            .semantics {
                                contentDescription = description
                                selected = selectedBoard == row.sourceAndBoard
                            }.nextClickable(role = Role.Button) { onSelected(row.sourceAndBoard) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val ring =
                        if (row.sourceAndBoard in unreadBoards) {
                            Brush.sweepGradient(STORY_RING_COLORS)
                        } else {
                            Brush.linearGradient(listOf(next.hairline, next.hairline))
                        }
                    Box(
                        modifier =
                            Modifier
                                .size(84.dp)
                                .background(Color.White, CircleShape)
                                .border(2.dp, ring, CircleShape)
                                .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.matchParentSize().clip(CircleShape).background(next.raised),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (row.hasPreview && thumbnail != null) {
                                thumbnail(row, Modifier.matchParentSize())
                            } else {
                                BoardIcon(
                                    siteId = row.site,
                                    boardId = row.board,
                                    title = row.boardTitle,
                                    modifier = Modifier.matchParentSize(),
                                )
                            }
                        }
                    }
                    Text(
                        row.board,
                        style = NextType.footnote.copy(fontSize = 12.sp, lineHeight = 15.sp),
                        color = next.ink,
                        fontWeight =
                            if (selectedBoard ==
                                row.sourceAndBoard
                            ) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                        textDecoration =
                            if (selectedBoard ==
                                row.sourceAndBoard
                            ) {
                                TextDecoration.Underline
                            } else {
                                TextDecoration.None
                            },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (row.site.isNotBlank()) {
                        Text(
                            row.site,
                            style = NextType.footnote.copy(fontSize = 10.sp, lineHeight = 12.sp),
                            color = next.muted,
                        )
                    }
                }
            }
        }
    }
}

// Warm rings reflect real unread feed rows; they never imply social stories or an account.
private val STORY_RING_COLORS =
    listOf(
        Color(0xFFFFC940),
        Color(0xFFFF6A2F),
        Color(0xFFFF2487),
        Color(0xFFB52BD7),
        Color(0xFFFFC940),
    )
