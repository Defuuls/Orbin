package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_board_media
import com.orbin.uinext.resources.next_row_replies
import com.orbin.uinext.resources.reader_count_thousands
import com.orbin.uinext.resources.reader_muted
import com.orbin.uinext.resources.reader_pick_board
import com.orbin.uinext.resources.reader_reload
import com.orbin.uinext.resources.reader_row_meta
import com.orbin.uinext.resources.reader_row_summary
import com.orbin.uinext.resources.reader_search
import com.orbin.uinext.resources.reader_sort
import com.orbin.uinext.resources.reader_unread
import com.orbin.uinext.resources.reader_view_grid
import com.orbin.uinext.resources.reader_view_list
import com.orbin.uinext.tokens.ReaderColors
import com.orbin.uinext.tokens.ReaderRadius
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import com.orbin.uinext.tokens.ReaderType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** How a board catalog lays its threads out. The reader chooses; see docs/design-system. */
enum class CatalogMode {
    /** One row per thread: thumbnail, title, post number and date, preview, reply count. */
    LIST,

    /** Cards in as many columns as fit: a square image above a title and a reply count. */
    GRID,
}

object CatalogTags {
    const val GRID = "catalog-grid"
    const val ROW = "catalog-row"
    const val CARD = "catalog-card"
    const val PILL = "catalog-pill"
    const val PROGRESS = "catalog-progress"
}

/**
 * A board's catalog, as docs/design-system specifies it.
 *
 * List and grid share one lazy grid and one [state], so switching [mode] keeps the scroll
 * position and reloads nothing. The app bar is fixed; only the threads scroll.
 *
 * [wideWindow] is whether the window, not this pane, is wide: it selects the larger thumbnail.
 * [selectedRowId] marks the thread open beside the catalog in two panes.
 * [navigation] is Menu on a phone and in two panes; hosts that reach the catalog from another
 * screen without a drawer pass Back.
 */
@Composable
fun BoardScreen(
    board: String,
    description: String,
    itemCount: Int,
    rowAt: (Int) -> FeedRow?,
    rowKey: (Int) -> Any = { index -> rowAt(index)?.id ?: "catalog-pending-$index" },
    modifier: Modifier = Modifier,
    mode: CatalogMode = CatalogMode.LIST,
    onModeChange: ((CatalogMode) -> Unit)? = null,
    sortLabel: String? = null,
    onSort: () -> Unit = {},
    onOpenMedia: (() -> Unit)? = null,
    onOpenRow: (FeedRow) -> Unit = {},
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)? = null,
    navigation: ReaderNavigation = ReaderNavigation.NONE,
    onNavigation: () -> Unit = {},
    onPickBoard: (() -> Unit)? = null,
    onReload: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    reloading: Boolean = false,
    wideWindow: Boolean = false,
    selectedRowId: String? = null,
    state: LazyGridState = rememberLazyGridState(),
) {
    val colors = reader
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background)
                .semantics { paneTitle = board },
    ) {
        ReaderAppBar(
            title = description.ifBlank { board },
            navigation = navigation,
            onNavigation = onNavigation,
            onTitleClick = onPickBoard,
            titleClickLabel = stringResource(Res.string.reader_pick_board),
            actions = catalogActions(onReload, onSearch),
            overflow = catalogOverflow(mode, onModeChange, sortLabel, onSort, onOpenMedia),
        )
        Box(Modifier.fillMaxWidth().height(ReaderSize.progress)) {
            if (reloading) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ReaderSize.progress)
                        .background(colors.accent)
                        .testTag(CatalogTags.PROGRESS),
                )
            }
        }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            val grid = mode == CatalogMode.GRID
            val columns = if (grid) catalogGridColumns(maxWidth) else 1
            val gap = if (grid) ReaderSpace.gridGap else NO_GAP
            val thumbnailSize = if (wideWindow) ReaderSize.thumbnailWide else ReaderSize.thumbnail
            val cellMin = if (wideWindow) ReaderSize.cellMinWide else ReaderSize.cellMin
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = state,
                modifier = Modifier.fillMaxSize().testTag(CatalogTags.GRID),
                contentPadding =
                    PaddingValues(
                        start = gap,
                        top = gap,
                        end = gap,
                        bottom = gap + bottomInset(),
                    ),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                items(
                    count = itemCount,
                    key = rowKey,
                    contentType = { mode },
                ) { index ->
                    val row = rowAt(index)
                    when {
                        row == null && grid -> Box(Modifier.fillMaxWidth().aspectRatio(1f))
                        row == null -> Box(Modifier.fillMaxWidth().height(cellMin))
                        grid -> CatalogCard(row, row.id == selectedRowId, onOpenRow, thumbnail)
                        else ->
                            CatalogRow(
                                row = row,
                                selected = row.id == selectedRowId,
                                thumbnailSize = thumbnailSize,
                                cellMin = cellMin,
                                onOpen = onOpenRow,
                                thumbnail = thumbnail,
                            )
                    }
                }
            }
        }
    }
}

/** Grid columns for a pane [width] wide: the width over `size.gridCardMin`, rounded down, at least one. */
fun catalogGridColumns(width: Dp): Int = maxOf(1, (width / ReaderSize.gridCardMin).toInt())

/** A reply or unread count as the pill shows it: exact below 1000, then thousands to one decimal, rounded down. */
internal fun abbreviatedCount(count: Int): AbbreviatedCount =
    if (count < COUNT_ABBREVIATION_FROM) {
        AbbreviatedCount.Exact(maxOf(0, count))
    } else {
        val tenths = count / (COUNT_ABBREVIATION_FROM / TENTHS)
        AbbreviatedCount.Thousands(whole = tenths / TENTHS, tenth = tenths % TENTHS)
    }

internal sealed interface AbbreviatedCount {
    data class Exact(
        val value: Int,
    ) : AbbreviatedCount

    data class Thousands(
        val whole: Int,
        val tenth: Int,
    ) : AbbreviatedCount
}

@Composable
private fun countLabel(count: Int): String =
    when (val shown = abbreviatedCount(count)) {
        is AbbreviatedCount.Exact -> shown.value.toString()
        is AbbreviatedCount.Thousands ->
            stringResource(
                Res.string.reader_count_thousands,
                if (shown.tenth == 0) shown.whole.toString() else "${shown.whole}.${shown.tenth}",
            )
    }

@Composable
private fun catalogActions(
    onReload: (() -> Unit)?,
    onSearch: (() -> Unit)?,
): List<ReaderAction> =
    buildList {
        if (onReload != null) add(ReaderAction(ReaderIcons.Refresh, stringResource(Res.string.reader_reload), onReload))
        if (onSearch != null) add(ReaderAction(ReaderIcons.Search, stringResource(Res.string.reader_search), onSearch))
    }

@Composable
private fun catalogOverflow(
    mode: CatalogMode,
    onModeChange: ((CatalogMode) -> Unit)?,
    sortLabel: String?,
    onSort: () -> Unit,
    onOpenMedia: (() -> Unit)?,
): List<ReaderMenuItem> =
    buildList {
        if (onModeChange != null) {
            when (mode) {
                CatalogMode.LIST ->
                    add(ReaderMenuItem(stringResource(Res.string.reader_view_grid)) { onModeChange(CatalogMode.GRID) })
                CatalogMode.GRID ->
                    add(ReaderMenuItem(stringResource(Res.string.reader_view_list)) { onModeChange(CatalogMode.LIST) })
            }
        }
        if (sortLabel != null) add(ReaderMenuItem(stringResource(Res.string.reader_sort, sortLabel), onSort))
        if (onOpenMedia != null) add(ReaderMenuItem(stringResource(Res.string.next_board_media), onOpenMedia))
    }

/** The text a screen reader gets for a thread: title, then count, then date. */
@Composable
private fun rowSummary(
    row: FeedRow,
    title: String,
): String {
    val count =
        if (row.unread > 0) {
            pluralStringResource(Res.plurals.reader_unread, row.unread, row.unread)
        } else {
            pluralStringResource(Res.plurals.next_row_replies, row.replies, row.replies)
        }
    return stringResource(Res.string.reader_row_summary, title, count, row.activity)
}

@Composable
private fun rowTitle(row: FeedRow): String = if (row.muted) stringResource(Res.string.reader_muted) else row.subject

/**
 * A thread as a list row: the shared cell of docs/design-system plus the count pill.
 *
 * At a font scale of 1.3 the pill moves under the preview; at 2.0 the thumbnail moves above the
 * text. Nothing shrinks.
 */
@Composable
private fun CatalogRow(
    row: FeedRow,
    selected: Boolean,
    thumbnailSize: Dp,
    cellMin: Dp,
    onOpen: (FeedRow) -> Unit,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)?,
) {
    val colors = reader
    val fontScale = LocalDensity.current.fontScale
    val pillBelow = fontScale >= PILL_BELOW_FROM_FONT_SCALE
    val stacked = fontScale >= STACKED_FROM_FONT_SCALE
    val showThumbnail = row.hasPreview && !row.muted && thumbnail != null
    val title = rowTitle(row)
    val summary = rowSummary(row, title)
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) colors.selected else colors.background)
            .nextClickable(role = Role.Button) { onOpen(row) }
            .clearAndSetSemantics {
                contentDescription = summary
                this.selected = selected
            }.testTag(CatalogTags.ROW),
    ) {
        val thumbnailSlot: @Composable () -> Unit = {
            Box(
                Modifier
                    .size(thumbnailSize)
                    .clip(RoundedCornerShape(ReaderRadius.small))
                    .background(colors.surfaceVariant),
            ) {
                thumbnail?.invoke(row, Modifier.fillMaxSize())
            }
        }
        val text: @Composable (Modifier) -> Unit = { textModifier ->
            Column(textModifier, verticalArrangement = Arrangement.spacedBy(ReaderSpace.line)) {
                Text(
                    text = title,
                    style = ReaderType.rowTitle,
                    color = if (row.read || row.muted) colors.inkMuted else colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        if (row.threadNumber.isEmpty()) {
                            row.activity
                        } else {
                            stringResource(Res.string.reader_row_meta, row.threadNumber, row.activity)
                        },
                    style = ReaderType.meta,
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!row.muted && row.excerpt.isNotBlank()) {
                    Text(
                        text = remember(row.excerpt, colors) { previewText(row.excerpt, colors) },
                        style = ReaderType.preview,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (pillBelow) CountPill(row)
            }
        }
        val padding = Modifier.fillMaxWidth().padding(horizontal = ReaderSpace.gutter, vertical = ReaderSpace.rowPadY)
        if (stacked) {
            Column(padding, verticalArrangement = Arrangement.spacedBy(ReaderSpace.gapSmall)) {
                if (showThumbnail) thumbnailSlot()
                text(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = padding.heightIn(min = if (showThumbnail) cellMin - ReaderSpace.rowPadY * 2 else NO_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showThumbnail) {
                    Box(Modifier.align(Alignment.Top)) { thumbnailSlot() }
                    Spacer(Modifier.width(ReaderSpace.gap))
                }
                text(Modifier.weight(1f).align(Alignment.Top))
                if (!pillBelow) {
                    Spacer(Modifier.width(ReaderSpace.gapSmall))
                    CountPill(row)
                }
            }
        }
        ReaderHairline()
    }
}

/** The reply count, or for a watched thread with unread replies the unread count on the accent. */
@Composable
private fun CountPill(row: FeedRow) {
    val colors = reader
    val unread = row.unread > 0
    Box(
        modifier =
            Modifier
                .height(ReaderSize.pillHeight)
                .widthIn(min = ReaderSize.pillMinWidth)
                .clip(RoundedCornerShape(ReaderRadius.pill))
                .background(if (unread) colors.accent else colors.surfaceVariant)
                .padding(horizontal = ReaderSpace.gap)
                .testTag(CatalogTags.PILL),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = countLabel(if (unread) row.unread else row.replies),
            style = ReaderType.pill,
            color = if (unread) colors.onAccent else colors.ink,
            maxLines = 1,
        )
    }
}

/** A thread as a grid card: a square image above a title and a reply count. */
@Composable
private fun CatalogCard(
    row: FeedRow,
    selected: Boolean,
    onOpen: (FeedRow) -> Unit,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)?,
) {
    val colors = reader
    val shape = RoundedCornerShape(ReaderRadius.card)
    val title = rowTitle(row)
    val summary = rowSummary(row, title)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.selected else colors.background)
            .border(ReaderSize.hairline, colors.hairline, shape)
            .nextClickable(role = Role.Button) { onOpen(row) }
            .clearAndSetSemantics {
                contentDescription = summary
                this.selected = selected
            }.testTag(CatalogTags.CARD),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(colors.surfaceVariant)) {
            if (row.hasPreview && !row.muted) thumbnail?.invoke(row, Modifier.fillMaxSize())
        }
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = ReaderSize.gridCardText).padding(ReaderSpace.cardPad),
            verticalArrangement = Arrangement.spacedBy(ReaderSpace.line),
        ) {
            Text(
                text = title,
                style = ReaderType.cardTitle,
                color = if (row.read || row.muted) colors.inkMuted else colors.ink,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    if (row.unread > 0) {
                        pluralStringResource(Res.plurals.reader_unread, row.unread, row.unread)
                    } else {
                        pluralStringResource(Res.plurals.next_row_replies, row.replies, row.replies)
                    },
                style = ReaderType.meta,
                color = if (row.unread > 0) colors.accent else colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The opening post's text with quoted lines in `color.quote` and the rest in `color.ink`. */
internal fun previewText(
    excerpt: String,
    colors: ReaderColors,
): AnnotatedString =
    buildAnnotatedString {
        excerpt.lineSequence().filter { it.isNotBlank() }.forEachIndexed { index, line ->
            if (index > 0) append('\n')
            val color = if (line.trimStart().startsWith(QUOTE_MARK)) colors.quote else colors.ink
            withStyle(SpanStyle(color = color)) { append(line.trim()) }
        }
    }

private const val QUOTE_MARK = ">"
private const val COUNT_ABBREVIATION_FROM = 1000
private const val TENTHS = 10
private const val PILL_BELOW_FROM_FONT_SCALE = 1.3f
private const val STACKED_FROM_FONT_SCALE = 2.0f
private val NO_GAP = 0.dp

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
