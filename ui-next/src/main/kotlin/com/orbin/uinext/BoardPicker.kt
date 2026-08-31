package com.orbin.uinext

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BoardChoice(
    val id: String,
    val title: String,
    val subscribed: Boolean = false,
)

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

@Composable
internal fun BoardChoiceRow(
    board: BoardChoice,
    onToggle: (BoardChoice) -> Unit,
) {
    val name = "/${board.id}/"
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
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
