package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType

/** A board tile for the Boards destination — no app types, just presentation. */
data class BoardTile(
    val id: String,
    val path: String,
    val title: String,
    val nsfw: Boolean = false,
)

/**
 * Primary Boards destination: inset grouped list on the grouped ground (iOS Settings / Files
 * rhythm), with DestinationPill chrome when sibling destinations are wired.
 */
@Composable
fun BoardsScreen(
    boards: List<BoardTile>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showRail: Boolean = true,
    onOpenBoard: (BoardTile) -> Unit = {},
    onRandom: (() -> Unit)? = null,
    onSearch: () -> Unit = {},
    onOpenFeed: (() -> Unit)? = null,
    onOpenMedia: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    hideRailOnScroll: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val railVisible =
        if (hideRailOnScroll) {
            scrollingUp({ listState.firstVisibleItemIndex }, { listState.firstVisibleItemScrollOffset })
        } else {
            true
        }
    LaunchedEffect(railVisible) { onChromeVisibleChange(railVisible) }

    val hasTabs = onOpenFeed != null || onOpenMedia != null || onOpenSettings != null
    val onDestination: ((NextDestination) -> Unit)? =
        if (hasTabs) {
            { dest ->
                when (dest) {
                    NextDestination.FEED -> onOpenFeed?.invoke()
                    NextDestination.BOARDS -> Unit
                    NextDestination.MEDIA -> onOpenMedia?.invoke()
                    NextDestination.SETTINGS -> onOpenSettings?.invoke()
                }
            }
        } else {
            null
        }

    val boardsTitle = stringResource(R.string.next_launchpad_boards)
    val showCompactTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 64
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NextScaffold(
            where = stringResource(R.string.next_launchpad_boards).takeIf { showRail && !hasTabs },
            modifier = Modifier.fillMaxSize(),
            onSearch = onSearch,
            railVisible = railVisible,
            destination = NextDestination.BOARDS.takeIf { showRail && hasTabs },
            onDestination = onDestination.takeIf { showRail },
        ) { bottomPad ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().contentInsets(),
                contentPadding =
                    PaddingValues(
                        top = if (showCompactTitle) COMPACT_TITLE_CLEARANCE else 0.dp,
                        bottom = bottomPad.calculateBottomPadding() + NextSpace.groupGap,
                    ),
            ) {
                item(key = "boards-title") {
                    Column {
                        ScreenTitle(
                            text = stringResource(R.string.next_launchpad_boards),
                            subtitle =
                                subtitle
                                    ?: pluralStringResource(
                                        R.plurals.next_boards_count,
                                        boards.size,
                                        boards.size,
                                    ),
                        )
                        if (onRandom != null) {
                            Box(modifier = Modifier.padding(horizontal = GUTTER - 4.dp)) {
                                InlineAction(
                                    label = stringResource(R.string.next_boards_random),
                                    onClick = onRandom,
                                )
                            }
                            Gap(12)
                        }
                    }
                }
                item(key = "boards-group") {
                    GroupedSection {
                        boards.forEachIndexed { index, board ->
                            BoardListRow(board = board, onClick = { onOpenBoard(board) })
                            if (index < boards.lastIndex) GroupedDivider()
                        }
                    }
                }
            }
        }
        CompactTitleBar(
            title = boardsTitle,
            visible = showCompactTitle,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun BoardListRow(
    board: BoardTile,
    onClick: () -> Unit,
) {
    val hue = boardHue(board.path)
    val letter =
        board.path
            .trim('/')
            .take(1)
            .uppercase()
            .ifEmpty { "?" }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .nextClickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = NextSpace.rowX, vertical = NextSpace.rowY),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(NextRadius.control))
                    .background(hue.copy(alpha = if (next.dark) 0.35f else 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = letter,
                style = NextType.headline,
                color = hue,
            )
        }
        WidthSpacer(12)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = board.path,
                    style = NextType.headline,
                    color = next.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (board.nsfw) {
                    WidthSpacer(8)
                    Text(
                        text = stringResource(R.string.next_boards_nsfw),
                        style = NextType.caption2,
                        color = next.muted,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(NextRadius.tight))
                                .background(next.ink.copy(alpha = if (next.dark) 0.14f else 0.06f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = board.title,
                style = NextType.footnote,
                color = next.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        WidthSpacer(8)
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = next.faint,
            modifier = Modifier.size(22.dp),
        )
    }
}
