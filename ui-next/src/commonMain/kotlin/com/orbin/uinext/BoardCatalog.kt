package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_board_media
import com.orbin.uinext.resources.social_feed_muted
import com.orbin.uinext.resources.tiktok_collection_threads
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.stringResource

@Composable
fun BoardScreen(
    board: String,
    description: String,
    itemCount: Int,
    rowAt: (Int) -> FeedRow?,
    rowKey: (Int) -> Any = { index -> rowAt(index)?.id ?: "catalog-pending-$index" },
    modifier: Modifier = Modifier,
    sortLabel: String? = null,
    onSort: () -> Unit = {},
    onOpenMedia: (() -> Unit)? = null,
    showRail: Boolean = true,
    onOpenRow: (FeedRow) -> Unit = {},
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)? = null,
    hideRailOnScroll: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    // A caller-selected column count (including tablet preferences) wins over the three-column
    // default. Large text uses two columns when the caller has not selected a count.
    columns: Int? = null,
) {
    val gridState = rememberLazyGridState()
    val railVisible =
        if (!hideRailOnScroll) {
            true
        } else {
            scrollingUp({ gridState.firstVisibleItemIndex }, { gridState.firstVisibleItemScrollOffset })
        }
    LaunchedEffect(railVisible) { onChromeVisibleChange(railVisible) }
    val showCompactTitle by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 ||
                gridState.firstVisibleItemScrollOffset > 64
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NextScaffold(
            where = board.takeIf { showRail },
            modifier = Modifier.fillMaxSize(),
            railVisible = railVisible,
        ) { bottomPad ->
            val header: @Composable () -> Unit = {
                val first = if (itemCount > 0) rowAt(0) else null
                Column {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = board,
                            style =
                                NextType.titleLarge.copy(
                                    fontSize = 17.sp,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = next.ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() },
                        )
                        Gap(16)
                        BoardIcon(
                            siteId = first?.site.orEmpty(),
                            boardId = first?.board ?: board,
                            title = description,
                            modifier = Modifier.size(72.dp),
                        )
                        Text(
                            text = description,
                            style = NextType.bodyMedium,
                            color = next.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        if (!first?.site.isNullOrBlank()) {
                            Text(sourceLabel(first.site), style = NextType.footnote, color = next.muted)
                        }
                        Text(
                            itemCount.toString(),
                            style =
                                NextType.titleLarge.copy(
                                    fontSize = 18.sp,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = next.ink,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            stringResource(Res.string.tiktok_collection_threads),
                            style = NextType.footnote,
                            color = next.muted,
                        )
                        Gap(16)
                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            if (onOpenMedia != null) {
                                InlineAction(stringResource(Res.string.next_board_media), onClick = onOpenMedia)
                            }
                            if (sortLabel != null) {
                                InlineAction("$sortLabel ▾", onClick = onSort)
                            }
                        }
                    }
                    Hairline()
                }
            }
            val insets = Modifier.fillMaxSize().contentInsets()
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns ?: if (LocalDensity.current.fontScale > 1.3f) 2 else 3),
                state = gridState,
                modifier = insets,
                contentPadding = PaddingValues(bottom = bottomPad.calculateBottomPadding()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                fullWidthItem { header() }
                items(
                    count = itemCount,
                    key = rowKey,
                    contentType = { "thread" },
                ) { index ->
                    rowAt(index)?.let { row ->
                        BoardPreviewCell(row, onOpenRow, thumbnail)
                    }
                }
            }
        }
        CompactTitleBar(
            title = board,
            visible = showCompactTitle,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun BoardPreviewCell(
    row: FeedRow,
    onOpen: (FeedRow) -> Unit,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().nextClickable(role = Role.Button) { onOpen(row) }) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(next.raised),
            contentAlignment = Alignment.Center,
        ) {
            if (row.hasPreview && !row.muted && thumbnail != null) {
                thumbnail(row, Modifier.fillMaxSize())
            } else {
                Column(
                    Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BoardIcon(row.site, row.board, title = row.boardTitle, modifier = Modifier.size(48.dp))
                    Text(
                        if (row.muted) stringResource(Res.string.social_feed_muted) else row.subject,
                        style = NextType.bodyMedium,
                        color = next.ink,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(ReaderIcons.ChatFilled, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(
                    rowCounts(row),
                    style = NextType.footnote.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            row.subject,
            style = NextType.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = if (row.read) next.muted else next.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
        )
    }
}

@Composable
internal fun PendingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PendingBar(width = 54.dp, height = 11.dp)
            Gap(9)
            PendingBar(width = 240.dp, height = 15.dp)
            Gap(8)
            PendingBar(width = 120.dp, height = 11.dp)
        }
    }
}

@Composable
internal fun PendingBar(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier =
            Modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(3.dp))
                .background(next.hairline),
    )
}
