package com.orbin.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_boards_no_match
import com.orbin.uinext.resources.next_boards_nsfw
import com.orbin.uinext.resources.next_boards_random
import com.orbin.uinext.resources.next_boards_search
import com.orbin.uinext.resources.next_launchpad_boards
import com.orbin.uinext.resources.next_search_title
import com.orbin.uinext.resources.next_settings_title
import com.orbin.uinext.resources.next_status_retry
import com.orbin.uinext.resources.social_feed_all_boards
import com.orbin.uinext.resources.social_feed_favorite_add
import com.orbin.uinext.resources.social_feed_favorite_boards
import com.orbin.uinext.resources.social_feed_favorite_remove
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.stringResource

/** A board directory row. Its stable [id] includes its site when more than one site is shown. */
data class BoardTile(
    val id: String,
    val path: String,
    val title: String,
    val nsfw: Boolean = false,
    val followed: Boolean = false,
    val site: String? = null,
)

internal fun List<BoardTile>.sortedAlphabetically(): List<BoardTile> =
    sortedWith(
        compareBy<BoardTile, String>(String.CASE_INSENSITIVE_ORDER) { it.path }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )

/** A searchable, source-scoped directory with independent open and favorite actions. */
@Composable
fun BoardsScreen(
    boards: List<BoardTile>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showRail: Boolean = true,
    onOpenBoard: (BoardTile) -> Unit = {},
    onFollowBoard: ((BoardTile, Boolean) -> Unit)? = null,
    onRandom: (() -> Unit)? = null,
    onOpenFeed: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    hideRailOnScroll: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    onOpenSearch: (() -> Unit)? = null,
    headerContent: @Composable () -> Unit = {},
    onOpenSaved: (() -> Unit)? = null,
    emptyMessage: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visibleBoards =
        remember(boards, query) {
            val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            boards
                .filter { board ->
                    terms.all { term -> "${board.path} ${board.title}".contains(term, ignoreCase = true) }
                }.sortedAlphabetically()
        }
    val favorites = remember(visibleBoards) { visibleBoards.filter { it.followed } }
    val listState = rememberLazyListState()
    val railVisible =
        if (hideRailOnScroll) {
            scrollingUp({ listState.firstVisibleItemIndex }, { listState.firstVisibleItemScrollOffset })
        } else {
            true
        }
    LaunchedEffect(railVisible) { onChromeVisibleChange(railVisible) }
    val hasTabs = onOpenFeed != null || onOpenDownloads != null || onOpenSearch != null || onOpenSaved != null
    val onDestination: ((NextDestination) -> Unit)? =
        if (hasTabs) {
            { dest ->
                when (dest) {
                    NextDestination.FEED -> onOpenFeed?.invoke()
                    NextDestination.SEARCH -> onOpenSearch?.invoke()
                    NextDestination.SAVED -> onOpenSaved?.invoke()
                    NextDestination.DOWNLOADS -> onOpenDownloads?.invoke()
                    NextDestination.SETTINGS -> onOpenSettings?.invoke()
                    NextDestination.BOARDS -> Unit
                }
            }
        } else {
            null
        }
    val boardsTitle = stringResource(Res.string.next_launchpad_boards)
    val showCompactTitle by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 64 }
    }
    Box(modifier.fillMaxSize()) {
        NextScaffold(
            where = boardsTitle.takeIf { showRail && !hasTabs },
            modifier = Modifier.fillMaxSize(),
            railVisible = railVisible,
            destination = NextDestination.BOARDS.takeIf { showRail && hasTabs },
            onDestination = onDestination.takeIf { showRail },
        ) { bottomPad ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().contentInsets(),
                contentPadding = PaddingValues(bottom = bottomPad.calculateBottomPadding() + NextSpace.groupGap),
            ) {
                item(key = "boards-title") {
                    Column(Modifier.padding(horizontal = GUTTER)) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                boardsTitle,
                                style = NextType.titleLarge,
                                color = next.ink,
                                modifier = Modifier.weight(1f).testTag(NextTitleTags.LARGE).semantics { heading() },
                            )
                            if (onOpenSearch != null) {
                                NextIconAction(
                                    Icons.Outlined.Search,
                                    stringResource(Res.string.next_search_title),
                                    onOpenSearch,
                                    tint = next.ink,
                                )
                            }
                            if (onOpenSettings != null) {
                                NextIconAction(
                                    Icons.Outlined.Menu,
                                    stringResource(Res.string.next_settings_title),
                                    onOpenSettings,
                                    tint = next.ink,
                                )
                            }
                        }
                        headerContent()
                        Gap(8)
                        SchematicSearch(query, { query = it }, stringResource(Res.string.next_boards_search))
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                subtitle,
                                style = NextType.footnote,
                                color = next.muted,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        if (onRandom !=
                            null
                        ) {
                            InlineAction(stringResource(Res.string.next_boards_random), onClick = onRandom)
                        }
                    }
                }
                if (visibleBoards.isEmpty()) {
                    item(key = "boards-empty") {
                        Text(
                            (
                                emptyMessage?.takeIf { boards.isEmpty() && query.isBlank() }
                                    ?: stringResource(Res.string.next_boards_no_match)
                            ),
                            style = NextType.body,
                            color = next.muted,
                            modifier = Modifier.padding(GUTTER),
                        )
                        if (onRetry != null) {
                            InlineAction(
                                stringResource(Res.string.next_status_retry),
                                onClick = onRetry,
                                modifier = Modifier.padding(horizontal = GUTTER),
                            )
                        }
                    }
                } else {
                    if (favorites.isNotEmpty()) {
                        item(
                            key = "favorite-heading",
                        ) { BoardSectionTitle(stringResource(Res.string.social_feed_favorite_boards)) }
                        items(favorites, key = { "favorite:${it.id}" }) { board ->
                            BoardListRow(
                                board,
                                { onOpenBoard(board) },
                                onFollowBoard?.let {
                                    { checked ->
                                        it(board, checked)
                                    }
                                },
                            )
                        }
                        item(key = "favorite-divider") {
                            Gap(12)
                            Hairline()
                        }
                    }
                    item(key = "all-heading") { BoardSectionTitle(stringResource(Res.string.social_feed_all_boards)) }
                    items(visibleBoards, key = { "all:${it.id}" }) { board ->
                        BoardListRow(
                            board,
                            { onOpenBoard(board) },
                            onFollowBoard?.let {
                                { checked ->
                                    it(board, checked)
                                }
                            },
                        )
                    }
                }
            }
        }
        CompactTitleBar(boardsTitle, showCompactTitle, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun BoardSectionTitle(label: String) {
    Text(
        label,
        style = NextType.footnote,
        color = next.muted,
        modifier = Modifier.padding(start = GUTTER, end = GUTTER, top = 20.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun BoardListRow(
    board: BoardTile,
    onClick: () -> Unit,
    onFollow: ((Boolean) -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .nextClickable(role = Role.Button, onClick = onClick)
                .padding(start = GUTTER, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(board.path, style = NextType.titleMedium, color = next.ink)
            Text(board.title, style = NextType.bodyMedium, color = next.muted)
            if (board.nsfw) {
                Text(
                    stringResource(Res.string.next_boards_nsfw),
                    style = NextType.caption1,
                    color = next.muted,
                )
            }
        }
        if (onFollow != null) {
            val identity = board.site?.let { "${sourceLabel(it)} · ${board.path}" } ?: board.path
            NextIconAction(
                imageVector = if (board.followed) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription =
                    stringResource(
                        if (board.followed) {
                            Res.string.social_feed_favorite_remove
                        } else {
                            Res.string.social_feed_favorite_add
                        },
                        identity,
                    ),
                onClick = { onFollow(!board.followed) },
                tint = next.ink,
                modifier = Modifier.semantics { selected = board.followed },
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = next.faint, modifier = Modifier.size(20.dp))
    }
}
