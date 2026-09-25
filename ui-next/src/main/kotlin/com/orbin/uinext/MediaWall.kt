package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class MediaCell(
    val id: String,
    val board: String,
)

@Composable
fun MediaWallScreen(
    scanned: Int,
    total: Int,
    failed: Int,
    modifier: Modifier = Modifier,
    cells: List<MediaCell> = emptyList(),
    scanning: Boolean = false,
    deepScanning: Boolean = false,
    deepScanned: Int = 0,
    deepTotal: Int = 0,
    showRail: Boolean = true,
    showSizeControl: Boolean = false,
    onOpen: (MediaCell) -> Unit = {},
    onLongPress: ((MediaCell) -> Unit)? = null,
    // One board's wall instead of every board's: its name replaces "All media".
    title: String? = null,
    // What you saved from the wall and elsewhere: Downloads, reached from the media it came from.
    onOpenSaved: (() -> Unit)? = null,
    onOpenFeed: (() -> Unit)? = null,
    onOpenBoards: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    tile: (@Composable (MediaCell, Modifier) -> Unit)? = null,
    hideRailOnScroll: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    var imageCellSize by rememberSaveable { mutableFloatStateOf(IMAGE_MIN_CELL.value) }
    val imageCellMinSize = imageCellSize.dp
    val imageHeight = if (showSizeControl) (imageCellSize * MEDIA_TILE_HEIGHT_RATIO).dp else 124.dp
    val imageSizeDescription = stringResource(R.string.next_media_size_control)
    val railVisible =
        if (hideRailOnScroll) {
            scrollingUp({ gridState.firstVisibleItemIndex }, { gridState.firstVisibleItemScrollOffset })
        } else {
            true
        }
    LaunchedEffect(railVisible) { onChromeVisibleChange(railVisible) }
    val hasTabs = onOpenFeed != null || onOpenBoards != null || onOpenSettings != null
    val onDestination: ((NextDestination) -> Unit)? =
        if (hasTabs) {
            { dest ->
                when (dest) {
                    NextDestination.FEED -> onOpenFeed?.invoke()
                    NextDestination.BOARDS -> onOpenBoards?.invoke()
                    NextDestination.MEDIA -> Unit
                    NextDestination.SETTINGS -> onOpenSettings?.invoke()
                }
            }
        } else {
            null
        }

    val mediaTitle = title ?: stringResource(R.string.next_all_media_title)
    val showCompactTitle by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 ||
                gridState.firstVisibleItemScrollOffset > 64
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NextScaffold(
            where = mediaTitle.takeIf { showRail },
            modifier = Modifier.fillMaxSize(),
            railVisible = railVisible,
            destination = NextDestination.MEDIA.takeIf { showRail && hasTabs },
            onDestination = onDestination.takeIf { showRail },
        ) { bottomPad ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(imageCellMinSize),
                state = gridState,
                modifier = Modifier.fillMaxSize().contentInsets(),
                contentPadding = gridPadding(bottomPad, top = if (showCompactTitle) COMPACT_TITLE_CLEARANCE else 0.dp),
            ) {
                fullWidthItem {
                    Column {
                        ScreenTitle(
                            text = mediaTitle,
                            subtitle =
                                stringResource(
                                    if (title ==
                                        null
                                    ) {
                                        R.string.next_all_media_subtitle
                                    } else {
                                        R.string.next_board_media_subtitle
                                    },
                                ),
                            inset = 0.dp,
                        )
                        if (onOpenSaved != null) {
                            Box(modifier = Modifier.offset(x = (-4).dp)) {
                                InlineAction(stringResource(R.string.next_media_saved), onClick = onOpenSaved)
                            }
                            Gap(8)
                        }
                        if (showSizeControl) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MetaLine(stringResource(R.string.next_media_size_small))
                                NextSlider(
                                    value = imageCellSize,
                                    onValueChange = {
                                        imageCellSize =
                                            it.coerceIn(
                                                MEDIA_CELL_MIN_DP,
                                                MEDIA_CELL_MAX_DP,
                                            )
                                    },
                                    valueRange = MEDIA_CELL_MIN_DP..MEDIA_CELL_MAX_DP,
                                    steps = MEDIA_CELL_STEPS,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .padding(horizontal = 12.dp)
                                            .semantics { contentDescription = imageSizeDescription },
                                )
                                MetaLine(stringResource(R.string.next_media_size_large))
                            }
                            Gap(8)
                        }
                        if (scanning || deepScanning || failed > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (deepScanning) {
                                    SweepBar(scanned = deepScanned, total = deepTotal)
                                    WidthSpacer(12)
                                    MetaLine(stringResource(R.string.next_sweep_reading, deepScanned, deepTotal))
                                } else if (scanning) {
                                    SweepBar(scanned = scanned, total = total)
                                    WidthSpacer(12)
                                    MetaLine(stringResource(R.string.next_sweep_progress, scanned, total))
                                }
                                Box(modifier = Modifier.weight(1f))
                                if (failed > 0) {
                                    MetaLine(
                                        stringResource(R.string.next_sweep_unreachable, failed),
                                        color = next.accent,
                                    )
                                }
                            }
                            Gap(16)
                        }
                    }
                }
                itemsIndexed(cells, key = { _, cell -> cell.id }) { index, cell ->
                    val description =
                        stringResource(R.string.next_media_cell_description, cell.board)
                    Box(
                        modifier =
                            Modifier
                                .padding(2.5.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .nextClickable(
                                    role = Role.Button,
                                    onClickLabel = stringResource(R.string.next_open_file),
                                    onLongClick = onLongPress?.let { handler -> { handler(cell) } },
                                    onLongClickLabel = stringResource(R.string.next_media_actions),
                                    onClick = { onOpen(cell) },
                                ).semantics { contentDescription = description },
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        val shape = Modifier.fillMaxWidth().height(imageHeight)
                        if (tile != null) {
                            tile(cell, shape)
                        } else {
                            MediaTile(modifier = shape, seed = index, radius = 16.dp)
                        }
                        MediaBadge(
                            text = cell.board,
                            modifier = Modifier.padding(6.dp).widthIn(max = 104.dp),
                        )
                    }
                }
            }
        }
        CompactTitleBar(
            title = mediaTitle,
            visible = showCompactTitle,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
internal fun SweepBar(
    scanned: Int,
    total: Int,
) {
    Box(
        modifier =
            Modifier
                .width(96.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(next.hairline),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(scanned.toFloat() / total.coerceAtLeast(1))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(next.accent),
        )
    }
}

private const val MEDIA_CELL_MIN_DP = MEDIA_SIZE_MIN_DP
private const val MEDIA_CELL_MAX_DP = MEDIA_SIZE_MAX_DP
private const val MEDIA_CELL_STEPS = MEDIA_SIZE_STEPS
private const val MEDIA_TILE_HEIGHT_RATIO = 0.74f
