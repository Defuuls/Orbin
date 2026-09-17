package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Primary Boards destination: airy grid of soft tiles on the grouped ground, with DestinationPill
 * chrome when sibling destinations are wired.
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
    val gridState = rememberLazyGridState()
    val railVisible =
        if (hideRailOnScroll) {
            scrollingUp({ gridState.firstVisibleItemIndex }, { gridState.firstVisibleItemScrollOffset })
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
            gridState.firstVisibleItemIndex > 0 ||
                gridState.firstVisibleItemScrollOffset > 64
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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(158.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize().contentInsets(),
                contentPadding =
                    PaddingValues(
                        start = NextSpace.gutterTight,
                        end = NextSpace.gutterTight,
                        top = if (showCompactTitle) COMPACT_TITLE_CLEARANCE else 0.dp,
                        bottom = bottomPad.calculateBottomPadding(),
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                fullWidthItem {
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
                items(boards, key = { it.id }) { board ->
                    BoardTileCard(board = board, onClick = { onOpenBoard(board) })
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
private fun BoardTileCard(
    board: BoardTile,
    onClick: () -> Unit,
) {
    val hue = boardHue(board.path)
    Box(
        modifier =
            Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(NextRadius.card))
                .background(
                    Brush.linearGradient(
                        listOf(
                            hue.copy(alpha = if (next.dark) 0.55f else 0.85f),
                            hue.copy(alpha = if (next.dark) 0.28f else 0.55f),
                        ),
                    ),
                ).clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            text =
                board.path
                    .trim('/')
                    .take(1)
                    .uppercase()
                    .ifEmpty { "?" },
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
            modifier = Modifier.align(Alignment.Center),
        )
        if (board.nsfw) {
            Text(
                text = stringResource(R.string.next_boards_nsfw),
                style = NextType.caption2,
                color = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(NextRadius.tight))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        ),
                    ).padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = board.path,
                style = NextType.headline,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = board.title,
                style = NextType.footnote,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
