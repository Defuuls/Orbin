package com.orbin.uinext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_board_media
import com.orbin.uinext.resources.next_row_files
import com.orbin.uinext.resources.next_row_replies
import com.orbin.uinext.resources.reader_catalog_counts
import com.orbin.uinext.resources.reader_muted
import com.orbin.uinext.resources.reader_open_browser
import com.orbin.uinext.resources.reader_pick_board
import com.orbin.uinext.resources.reader_post_number
import com.orbin.uinext.resources.reader_reload
import com.orbin.uinext.resources.reader_reveal_spoiler
import com.orbin.uinext.resources.reader_row_meta
import com.orbin.uinext.resources.reader_row_summary
import com.orbin.uinext.resources.reader_search
import com.orbin.uinext.resources.reader_sort
import com.orbin.uinext.resources.reader_sort_bump_order
import com.orbin.uinext.resources.reader_sort_catalog
import com.orbin.uinext.resources.reader_sort_reply_count
import com.orbin.uinext.resources.reader_unread
import com.orbin.uinext.resources.reader_view_grid
import com.orbin.uinext.resources.reader_view_list
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderColors
import com.orbin.uinext.tokens.ReaderRatio
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** How a board catalog lays its threads out. The reader chooses; see docs/design-system. */
enum class CatalogMode {
    /** One row per thread: thumbnail, title, post number and date, preview, reply count. */
    LIST,

    /** Cards with an image half the viewport tall, in the columns [catalogGridSpec] gives, and reply/file counts. */
    GRID,
    ;

    /**
     * Whether a host's thumbnail slot should draw a still image from its original file, decoded to
     * the cell. A grid card is far wider than a provider thumbnail (about 250 pixels), which would
     * look blurry stretched across it; a list cell is thumbnail-sized and never needs the original.
     */
    val sharpensThumbnails: Boolean get() = this == GRID
}

object CatalogTags {
    const val GRID = "catalog-grid"
    const val ROW = "catalog-row"
    const val CARD = "catalog-card"
    const val PILL = "catalog-pill"
    const val COUNTS = "catalog-counts"
    const val THUMBNAIL = "catalog-thumbnail"
    const val PROGRESS = "catalog-progress"
    const val SORT_TABS = "catalog-sort-tabs"
    const val PENDING = "catalog-pending"
}

/**
 * A catalog sort the host already supports, shown as a tab.
 *
 * [id] is the host's [com.orbin.core.model.CatalogSort] name. The Catalog tab is creation
 * date — an existing option — not a new order. Bump order and reply count map 1:1.
 */
data class CatalogSortTab(
    val id: String,
    val label: String,
)

/** Ids for the three Coral catalog tabs, matching [com.orbin.core.model.CatalogSort] names. */
object CatalogSortTabs {
    const val CATALOG = "CREATION_DATE"
    const val BUMP_ORDER = "BUMP_ORDER"
    const val REPLY_COUNT = "REPLY_COUNT"
}

/** Catalog, Bump order, and Reply count — only existing [CatalogSort] values, mockup labels. */
@Composable
fun catalogSortTabs(): List<CatalogSortTab> =
    listOf(
        CatalogSortTab(CatalogSortTabs.CATALOG, stringResource(Res.string.reader_sort_catalog)),
        CatalogSortTab(CatalogSortTabs.BUMP_ORDER, stringResource(Res.string.reader_sort_bump_order)),
        CatalogSortTab(CatalogSortTabs.REPLY_COUNT, stringResource(Res.string.reader_sort_reply_count)),
    )

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
    sortTabs: List<CatalogSortTab> = emptyList(),
    selectedSortId: String? = null,
    onSelectSort: (String) -> Unit = {},
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
    onOpenBrowser: (() -> Unit)? = null,
    headerContent: (@Composable () -> Unit)? = null,
    stateContent: (@Composable () -> Unit)? = null,
) {
    val colors = reader
    val heading = catalogHeading(board, description)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    if (mode == CatalogMode.GRID && !next.dark) colors.surfaceVariant else colors.background,
                ).semantics { paneTitle = board },
    ) {
        ReaderAppBar(
            title = heading,
            navigation = navigation,
            onNavigation = onNavigation,
            onTitleClick = onPickBoard,
            titleClickLabel = stringResource(Res.string.reader_pick_board),
            actions = catalogActions(onReload, onSearch),
            overflow =
                catalogOverflow(
                    mode,
                    onModeChange,
                    sortLabel,
                    onSort,
                    onOpenMedia,
                    onOpenBrowser,
                ),
        )
        if (reloading) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ReaderSize.progress)
                    .background(colors.accent)
                    .testTag(CatalogTags.PROGRESS),
            )
        }
        if (sortTabs.isNotEmpty()) {
            CatalogSortTabRow(tabs = sortTabs, selectedId = selectedSortId, onSelect = onSelectSort)
        }
        headerContent?.invoke()
        if (stateContent != null) {
            stateContent()
            return@Column
        }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            val grid = mode == CatalogMode.GRID
            val gridSpec = if (grid) orbinCatalogGridSpec(maxWidth) else catalogGridSpec(maxWidth, maxHeight)
            val columns = if (grid) gridSpec.columns else 1
            val gap = if (grid) ORBIN_CATALOG_GAP else NO_GAP
            val outer = if (grid) ORBIN_CATALOG_GUTTER else NO_GAP
            val cellMin = if (wideWindow) ReaderSize.cellMinWide else ReaderSize.cellMin
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = state,
                modifier = Modifier.fillMaxSize().testTag(CatalogTags.GRID),
                contentPadding =
                    PaddingValues(
                        start = outer,
                        top = outer,
                        end = outer,
                        bottom = outer + bottomInset(),
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
                        row == null && grid ->
                            OrbinCard(
                                modifier = Modifier.fillMaxWidth(),
                                elevated = false,
                                containerColor = reader.surfaceVariant,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(gridSpec.imageHeight)
                                        .testTag(CatalogTags.PENDING),
                                )
                            }
                        row == null -> Box(Modifier.fillMaxWidth().height(cellMin))
                        grid ->
                            CatalogCard(
                                row = row,
                                selected = row.id == selectedRowId,
                                onOpen = onOpenRow,
                                thumbnail = thumbnail,
                                imageHeight = gridSpec.imageHeight,
                                wideWindow = wideWindow,
                            )
                        else ->
                            CatalogRow(
                                row = row,
                                selected = row.id == selectedRowId,
                                wideWindow = wideWindow,
                                onOpen = onOpenRow,
                                thumbnail = thumbnail,
                            )
                    }
                }
            }
        }
    }
}

/**
 * Columns for the thread Files grid in a pane [width] wide: the width over `size.gridCardMin`,
 * rounded down, at least one. Catalog, Home and loading grids use [catalogGridSpec] instead.
 */
fun catalogGridColumns(width: Dp): Int = maxOf(1, (width / ReaderSize.gridCardMin).toInt())

/** How a thread grid divides its viewport: [columns] cards per line, each with an image [imageHeight] tall. */
data class CatalogGridSpec(
    val columns: Int,
    val imageHeight: Dp,
)

/**
 * The thread grid for a viewport [width] by [height], measured below the app bar.
 *
 * A card's image is `ratio.gridImageHeight` (half) of the viewport tall, so two lines of images
 * fit on screen, and never shorter than `size.gridCardMin`, which keeps it usable in a short
 * split-screen window. Columns are the width over that image height, rounded down, at least one:
 * a portrait phone gets one full-width column, and a wide window gets as many as keep each image
 * at least as wide as tall.
 * An unbounded or unmeasured [height] falls back to the minimum image height.
 */
fun catalogGridSpec(
    width: Dp,
    height: Dp,
): CatalogGridSpec {
    val half = if (height.isFinite && height > 0.dp) height * ReaderRatio.gridImageHeight else ReaderSize.gridCardMin
    val imageHeight = maxOf(half, ReaderSize.gridCardMin)
    val columns = if (width.isFinite && width > 0.dp) maxOf(1, (width / imageHeight).toInt()) else 1
    return CatalogGridSpec(columns, imageHeight)
}

/**
 * Coral catalog grid: at least two [OrbinCard] columns on a phone, more on a wide pane.
 * Image height follows the column width so cards stay compact, unlike [catalogGridSpec]
 * which sizes images to half the viewport and yields a single phone column.
 */
fun orbinCatalogGridSpec(width: Dp): CatalogGridSpec {
    val fallback = ORBIN_CATALOG_MIN_CARD * 2 + ORBIN_CATALOG_GUTTER * 2 + ORBIN_CATALOG_GAP
    val usable = if (width.isFinite && width > 0.dp) width else fallback
    val columns =
        maxOf(
            1,
            (
                (usable - ORBIN_CATALOG_GUTTER * 2 + ORBIN_CATALOG_GAP) /
                    (ORBIN_CATALOG_MIN_CARD + ORBIN_CATALOG_GAP)
            ).toInt(),
        )
    val colWidth = (usable - ORBIN_CATALOG_GUTTER * 2 - ORBIN_CATALOG_GAP * (columns - 1)) / columns
    return CatalogGridSpec(columns, colWidth * ORBIN_CATALOG_IMAGE_RATIO)
}

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
    onOpenBrowser: (() -> Unit)?,
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
        if (sortLabel != null) add(ReaderMenuItem(stringResource(Res.string.reader_sort, sortLabel), onClick = onSort))
        if (onOpenMedia != null) add(ReaderMenuItem(stringResource(Res.string.next_board_media), onClick = onOpenMedia))
        if (onOpenBrowser != null) {
            add(ReaderMenuItem(stringResource(Res.string.reader_open_browser), onClick = onOpenBrowser))
        }
    }

/** One complete announcement: title, replies, files, optional unread count, then date. */
@Composable
private fun rowSummary(
    row: FeedRow,
    title: String,
): String {
    val replies = pluralStringResource(Res.plurals.next_row_replies, row.replies, row.replies)
    val files = pluralStringResource(Res.plurals.next_row_files, row.media, row.media)
    val unread = if (row.unread > 0) pluralStringResource(Res.plurals.reader_unread, row.unread, row.unread) else null
    val counts = listOfNotNull(replies, files, unread).joinToString(", ")
    return stringResource(Res.string.reader_row_summary, title, counts, row.activity)
}

@Composable
private fun rowTitle(row: FeedRow): String = if (row.muted) stringResource(Res.string.reader_muted) else row.subject

/**
 * A thread as a list row, with reply/file counts in the text column as in Clover's PostCell.
 * At a font scale of 2.0 the thumbnail moves above the text. Nothing shrinks.
 */
@Composable
internal fun CatalogRow(
    row: FeedRow,
    selected: Boolean,
    wideWindow: Boolean,
    onOpen: (FeedRow) -> Unit,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
    itemTag: String? = null,
    thumbnailTag: String? = null,
    metadata: String? = null,
    onOpenThumbnail: ((FeedRow) -> Unit)? = null,
    thumbnailClickLabel: String? = null,
    spoilerRevealed: Boolean? = null,
    onRevealSpoiler: (() -> Unit)? = null,
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
    showDivider: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    containerColor: Color? = null,
) {
    val colors = reader
    val showThumbnail = row.hasPreview && !row.muted && thumbnail != null
    var revealed by rememberSaveable(row.id) { mutableStateOf(false) }
    val covered = showThumbnail && row.imageSpoiler && !(spoilerRevealed ?: revealed)
    val reveal = { if (onRevealSpoiler != null) onRevealSpoiler() else revealed = true }
    val revealLabel = stringResource(Res.string.reader_reveal_spoiler)
    val title = rowTitle(row)
    val inset = readerPostInset(wideWindow)
    val bodyStyle = if (wideWindow) OrbinType.postBodyWide.withReaderFont() else OrbinType.postBody.withReaderFont()
    val detailStyle =
        if (wideWindow) {
            OrbinType.postDetailsWide.withReaderFont()
        } else {
            OrbinType.postDetails
                .withReaderFont()
        }
    val summary = listOfNotNull(rowSummary(row, title), metadata).joinToString(", ")
    // The tag leads the chain: clearAndSetSemantics clears the descendants after this node.
    Column(
        modifier
            .testTag(itemTag ?: CatalogTags.ROW)
            .fillMaxWidth()
            .background(if (selected) colors.selected else containerColor ?: colors.background)
            .nextClickable(
                role = Role.Button,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
            ) { onOpen(row) }
            .clearAndSetSemantics {
                contentDescription = summary
                this.selected = selected
                val actions =
                    buildList {
                        if (covered) {
                            add(
                                CustomAccessibilityAction(revealLabel) {
                                    reveal()
                                    true
                                },
                            )
                        } else if (showThumbnail && onOpenThumbnail != null && thumbnailClickLabel != null) {
                            add(
                                CustomAccessibilityAction(thumbnailClickLabel) {
                                    onOpenThumbnail(row)
                                    true
                                },
                            )
                        }
                        addAll(accessibilityActions)
                    }
                if (actions.isNotEmpty()) customActions = actions
            },
    ) {
        ReaderPostLayout(
            wideWindow = wideWindow,
            thumbnail =
                if (showThumbnail) {
                    { imageModifier ->
                        Box(
                            imageModifier
                                .testTag(thumbnailTag ?: CatalogTags.THUMBNAIL)
                                .clip(RoundedCornerShape(OrbinRadius.compact))
                                .background(colors.surfaceVariant)
                                .then(
                                    catalogThumbnailAction(
                                        covered,
                                        row,
                                        onOpenThumbnail,
                                        thumbnailClickLabel,
                                        onLongClick,
                                        onLongClickLabel,
                                    ),
                                ),
                        ) {
                            if (covered) {
                                ReaderSpoilerCover(
                                    onReveal = reveal,
                                    modifier = Modifier.fillMaxSize(),
                                    onLongClick = onLongClick,
                                    onLongClickLabel = onLongClickLabel,
                                )
                            } else {
                                thumbnail(row, Modifier.fillMaxSize())
                            }
                        }
                    }
                } else {
                    null
                },
        ) { textModifier ->
            Column(textModifier.padding(inset), verticalArrangement = Arrangement.spacedBy(inset)) {
                CatalogTitle(row, title, bodyStyle)
                Text(
                    text =
                        metadata ?: if (row.threadNumber.isEmpty()) {
                            row.activity
                        } else {
                            stringResource(Res.string.reader_row_meta, row.threadNumber, row.activity)
                        },
                    style = detailStyle,
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!row.muted && row.excerpt.isNotBlank()) {
                    Text(
                        text = remember(row.previewLines, colors) { previewText(row.previewLines, colors) },
                        style = bodyStyle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CatalogCounts(row, style = bodyStyle)
            }
        }
        if (showDivider) ReaderHairline(Modifier.padding(horizontal = inset))
    }
}

/** Clover's catalog counters are plain text; watched threads retain Orbin's unread marker. */
@Composable
private fun CatalogCounts(
    row: FeedRow,
    style: TextStyle = OrbinType.postBody,
) {
    val colors = reader
    // Match Clover's full-width TextViews so paragraph measurement and wrapping use the cell width.
    Column(
        Modifier.testTag(CatalogTags.COUNTS).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ReaderSpace.line),
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text =
                stringResource(
                    Res.string.reader_catalog_counts,
                    pluralStringResource(Res.plurals.next_row_replies, row.replies, row.replies),
                    pluralStringResource(Res.plurals.next_row_files, row.media, row.media),
                ),
            style = style.withReaderFont(),
            color = colors.inkMuted,
        )
        if (row.unread > 0) {
            Text(
                pluralStringResource(Res.plurals.reader_unread, row.unread, row.unread),
                modifier = Modifier.fillMaxWidth(),
                style = OrbinType.meta.withReaderFont(),
                color = colors.accent,
            )
        }
    }
}

/** A thread as a grid card: identity, subject, an inset thumbnail, then reply and file counts. */
@Composable
internal fun CatalogCard(
    row: FeedRow,
    selected: Boolean,
    onOpen: (FeedRow) -> Unit,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)?,
    imageHeight: Dp,
    modifier: Modifier = Modifier,
    itemTag: String? = null,
    thumbnailTag: String? = null,
    metadata: String? = null,
    onOpenThumbnail: ((FeedRow) -> Unit)? = null,
    thumbnailClickLabel: String? = null,
    spoilerRevealed: Boolean? = null,
    onRevealSpoiler: (() -> Unit)? = null,
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
    wideWindow: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val colors = reader
    val title = rowTitle(row)
    val bodyStyle = if (wideWindow) OrbinType.postBodyWide.withReaderFont() else OrbinType.postBody.withReaderFont()
    val summary = listOfNotNull(rowSummary(row, title), metadata).joinToString(", ")
    val showThumbnail = row.hasPreview && !row.muted && thumbnail != null
    var revealed by rememberSaveable(row.id) { mutableStateOf(false) }
    val covered = showThumbnail && row.imageSpoiler && !(spoilerRevealed ?: revealed)
    val reveal = { if (onRevealSpoiler != null) onRevealSpoiler() else revealed = true }
    val revealLabel = stringResource(Res.string.reader_reveal_spoiler)
    val numberLabel =
        if (row.threadNumber.isNotEmpty()) {
            stringResource(Res.string.reader_post_number, row.threadNumber)
        } else {
            ""
        }
    val actions =
        buildList {
            if (covered) {
                add(
                    CustomAccessibilityAction(revealLabel) {
                        reveal()
                        true
                    },
                )
            } else if (showThumbnail && onOpenThumbnail != null && thumbnailClickLabel != null) {
                add(
                    CustomAccessibilityAction(thumbnailClickLabel) {
                        onOpenThumbnail(row)
                        true
                    },
                )
            }
            addAll(accessibilityActions)
        }
    val titleMaxLines = 2
    OrbinCard(
        modifier =
            modifier
                .testTag(itemTag ?: CatalogTags.CARD)
                .fillMaxWidth()
                .semantics {
                    contentDescription = summary
                    this.selected = selected
                    if (actions.isNotEmpty()) customActions = actions
                }.nextClickable(
                    role = Role.Button,
                    onClickLabel = summary,
                    onLongClick = onLongClick,
                    onLongClickLabel = onLongClickLabel,
                    onClick = { onOpen(row) },
                ),
        containerColor = if (selected) colors.selected else null,
        border = if (selected) BorderStroke(2.dp, colors.accent) else null,
        elevated = false,
        shape = RoundedCornerShape(OrbinRadius.card),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .background(colors.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val initial =
                        row.poster
                            .firstOrNull()
                            ?.uppercaseChar()
                            ?.toString() ?: "A"
                    Text(
                        initial,
                        style = OrbinType.meta.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onAccent,
                        maxLines = 1,
                    )
                }
                Column(Modifier.weight(1f)) {
                    if (row.poster.isNotBlank()) {
                        Text(
                            row.poster,
                            style = OrbinType.meta.withReaderFont(),
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (numberLabel.isNotEmpty()) {
                        Text(
                            numberLabel,
                            style = OrbinType.meta.withReaderFont(),
                            color = colors.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (title.isNotBlank()) {
                CatalogTitle(
                    row,
                    title,
                    bodyStyle,
                    maxLines = titleMaxLines,
                )
            }
            if (metadata != null) {
                Text(
                    metadata,
                    style = OrbinType.meta.withReaderFont(),
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showThumbnail) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(imageHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceVariant)
                        .testTag(thumbnailTag ?: CatalogTags.THUMBNAIL)
                        .clearAndSetSemantics {}
                        .then(
                            catalogThumbnailAction(
                                covered,
                                row,
                                onOpenThumbnail,
                                thumbnailClickLabel,
                                onLongClick,
                                onLongClickLabel,
                            ),
                        ),
                ) {
                    if (covered) {
                        ReaderSpoilerCover(
                            onReveal = reveal,
                            modifier = Modifier.fillMaxSize(),
                            onLongClick = onLongClick,
                            onLongClickLabel = onLongClickLabel,
                        )
                    } else {
                        thumbnail(row, Modifier.fillMaxSize())
                    }
                }
            }
            CatalogIconCounts(row, style = bodyStyle)
        }
    }
}

@Composable
private fun CatalogIconCounts(
    row: FeedRow,
    style: TextStyle,
) {
    val colors = reader
    Column(
        Modifier.testTag(CatalogTags.COUNTS).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ReaderSpace.line),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                ReaderIcons.Chat,
                contentDescription = null,
                tint = colors.inkMuted,
                modifier = Modifier.size(16.dp),
            )
            Text(
                pluralStringResource(Res.plurals.next_row_replies, row.replies, row.replies),
                modifier = Modifier.weight(1f),
                style = style.withReaderFont(),
                color = colors.inkMuted,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                ReaderIcons.Album,
                contentDescription = null,
                tint = colors.inkMuted,
                modifier = Modifier.size(16.dp),
            )
            Text(
                pluralStringResource(Res.plurals.next_row_files, row.media, row.media),
                modifier = Modifier.weight(1f),
                style = style.withReaderFont(),
                color = colors.inkMuted,
            )
        }
        if (row.unread > 0) {
            Text(
                pluralStringResource(Res.plurals.reader_unread, row.unread, row.unread),
                modifier = Modifier.fillMaxWidth(),
                style = OrbinType.meta.withReaderFont(),
                color = colors.accent,
            )
        }
    }
}

/** Inline status icons follow the title and share its wrapping limit. */
@Composable
private fun CatalogTitle(
    row: FeedRow,
    title: String,
    style: TextStyle,
    minLines: Int = 1,
    maxLines: Int = 2,
) {
    val colors = reader
    val text =
        buildAnnotatedString {
            append(title)
            if (!row.muted && row.sticky) {
                append(" ")
                appendInlineContent("sticky")
            }
            if (!row.muted && row.closed) {
                append(" ")
                appendInlineContent("closed")
            }
        }
    val placeholder =
        Placeholder(OrbinType.meta.lineHeight, OrbinType.meta.lineHeight, PlaceholderVerticalAlign.Center)
    Text(
        text = text,
        style = style.withReaderFont(),
        color = if (row.read || row.muted) colors.inkMuted else colors.subject,
        minLines = minLines,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        inlineContent =
            mapOf(
                "sticky" to
                    InlineTextContent(placeholder) {
                        Icon(
                            ReaderIcons.Pin,
                            null,
                            Modifier.fillMaxSize().testTag("catalog-sticky"),
                            tint = colors.inkMuted,
                        )
                    },
                "closed" to
                    InlineTextContent(placeholder) {
                        Icon(
                            ReaderIcons.Lock,
                            null,
                            Modifier.fillMaxSize().testTag("catalog-closed"),
                            tint = colors.inkMuted,
                        )
                    },
            ),
    )
}

/** A grid card: a full-width image [imageHeight] tall with the text below it, which grows with the font scale. */
@Composable
fun ReaderCatalogCardLayout(
    imageHeight: Dp,
    modifier: Modifier = Modifier,
    image: @Composable (Modifier) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        image(Modifier.fillMaxWidth().height(imageHeight))
        content(Modifier.fillMaxWidth().heightIn(min = ReaderSize.gridCardText).padding(ReaderSpace.cardPad))
    }
}

/** The opening post's text with quoted lines in `color.quote` and the rest in `color.ink`. */
internal fun previewText(
    excerpt: String,
    colors: ReaderColors,
): AnnotatedString = previewText(previewLinesOf(excerpt), colors)

internal fun previewText(
    lines: List<PreviewLine>,
    colors: ReaderColors,
): AnnotatedString =
    buildAnnotatedString {
        lines.forEachIndexed { index, line ->
            if (index > 0) append('\n')
            val color = if (line.quoted) colors.quote else colors.ink
            withStyle(SpanStyle(color = color)) { append(line.text) }
        }
    }

private const val COUNT_ABBREVIATION_FROM = 1000
private const val TENTHS = 10
private val NO_GAP = 0.dp
internal val ORBIN_CATALOG_GUTTER = 16.dp
internal val ORBIN_CATALOG_GAP = 8.dp
internal val ORBIN_CATALOG_MIN_CARD = 152.dp
private const val ORBIN_CATALOG_IMAGE_RATIO = 0.75f

/** "/g/ - Technology" when both path and title are present. */
internal fun catalogHeading(
    board: String,
    description: String,
): String =
    when {
        description.isBlank() -> board
        board.isBlank() || description == board || board.contains(description) -> description
        else -> "$board - $description"
    }

@Composable
private fun CatalogSortTabRow(
    tabs: List<CatalogSortTab>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val largeText = fontScale >= 2f
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(reader.appBar)
                .testTag(CatalogTags.SORT_TABS)
                .selectableGroup()
                .padding(horizontal = ORBIN_CATALOG_GUTTER)
                .heightIn(min = MIN_TOUCH_TARGET)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = tab.id == selectedId
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = MIN_TOUCH_TARGET)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { if (!selected) onSelect(tab.id) },
                        ).semantics { contentDescription = tab.label },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    tab.label,
                    style = if (largeText) OrbinType.caption1 else OrbinType.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) reader.accent else next.muted,
                    maxLines = if (largeText) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .height(2.dp)
                        .fillMaxWidth(0.55f)
                        .background(if (selected) reader.accent else reader.background.copy(alpha = 0f)),
                )
            }
        }
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

@Composable
private fun catalogThumbnailAction(
    covered: Boolean,
    row: FeedRow,
    onOpen: ((FeedRow) -> Unit)?,
    label: String?,
    onLongClick: (() -> Unit)?,
    onLongClickLabel: String?,
): Modifier =
    if (!covered && onOpen != null) {
        Modifier.nextClickable(
            role = Role.Button,
            onClickLabel = label,
            onLongClick = onLongClick,
            onLongClickLabel = onLongClickLabel,
        ) {
            onOpen(row)
        }
    } else {
        Modifier
    }
