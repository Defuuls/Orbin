package com.orbin.uinext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.orbin.uinext.resources.reader_sort_images
import com.orbin.uinext.resources.reader_sort_oldest
import com.orbin.uinext.resources.reader_sort_reply_count
import com.orbin.uinext.resources.reader_unread
import com.orbin.uinext.resources.reader_view_card
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

    /** Compact cards in the columns [orbinCatalogGridSpec] gives, with reply and file counts. */
    GRID,

    /** The catalog card at the full pane width, inside the usual outer gutters. */
    CARD,
    ;

    /**
     * Whether a host's thumbnail slot should draw a still image from its original file, decoded to
     * the cell. A grid or full-width card is far wider than a provider thumbnail (about 250 pixels),
     * which would look blurry stretched across it; a list cell never needs the original.
     */
    val sharpensThumbnails: Boolean get() = this != LIST
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

/** Stable host sort IDs for every primary catalog order. */
object CatalogSortTabs {
    const val CATALOG = "CREATION_DATE"
    const val BUMP_ORDER = "BUMP_ORDER"
    const val REPLY_COUNT = "REPLY_COUNT"
    const val OLDEST = "OLDEST"
    const val IMAGE_COUNT = "IMAGE_COUNT"
}

/** The five browsing orders shared by both hosts, with their persisted model IDs. */
@Composable
fun catalogSortTabs(): List<CatalogSortTab> =
    listOf(
        CatalogSortTab(CatalogSortTabs.CATALOG, stringResource(Res.string.reader_sort_catalog)),
        CatalogSortTab(CatalogSortTabs.BUMP_ORDER, stringResource(Res.string.reader_sort_bump_order)),
        CatalogSortTab(CatalogSortTabs.OLDEST, stringResource(Res.string.reader_sort_oldest)),
        CatalogSortTab(CatalogSortTabs.REPLY_COUNT, stringResource(Res.string.reader_sort_reply_count)),
        CatalogSortTab(CatalogSortTabs.IMAGE_COUNT, stringResource(Res.string.reader_sort_images)),
    )

/**
 * A board's catalog, as docs/design-system specifies it.
 *
 * All modes share one lazy grid and one [state], so switching [mode] keeps the scroll
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
    PreserveCatalogScrollAnchor(state, mode)
    Column(
        modifier =
            modifier
                .readerKeyboardShortcuts(
                    onBack =
                        onNavigation.takeIf {
                            navigation == ReaderNavigation.BACK
                        },
                    onRefresh = onReload,
                    onSearch = onSearch,
                ).readerBackSwipe(onNavigation.takeIf { navigation == ReaderNavigation.BACK })
                .fillMaxSize()
                .background(colors.background)
                .semantics { paneTitle = board },
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
            val cards = mode != CatalogMode.LIST
            val gridSpec =
                when (mode) {
                    CatalogMode.GRID -> orbinCatalogGridSpec(maxWidth)
                    CatalogMode.CARD -> orbinCatalogCardSpec(maxWidth)
                    CatalogMode.LIST -> catalogGridSpec(maxWidth, maxHeight)
                }
            val columns = if (cards) gridSpec.columns else 1
            val gap = ORBIN_CATALOG_GAP
            val outer = ORBIN_CATALOG_GUTTER
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
                        row == null && cards ->
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
                        cards ->
                            CatalogCard(
                                row = row,
                                selected = row.id == selectedRowId,
                                onOpen = onOpenRow,
                                thumbnail = thumbnail,
                                imageHeight = gridSpec.imageHeight,
                                wideWindow = wideWindow,
                                compactGrid = mode == CatalogMode.GRID,
                            )
                        else ->
                            OrbinCard(modifier = Modifier.fillMaxWidth(), elevated = false) {
                                CatalogRow(
                                    row = row,
                                    selected = row.id == selectedRowId,
                                    wideWindow = wideWindow,
                                    onOpen = onOpenRow,
                                    thumbnail = thumbnail,
                                    showDivider = false,
                                    containerColor = colors.surfaceVariant,
                                )
                            }
                    }
                }
            }
        }
    }
}

/**
 * Columns for the thread Files grid in a pane [width] wide: the width over `size.gridCardMin`,
 * rounded down, at least one. Catalog, Home and loading grids use [orbinCatalogGridSpec] instead.
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
fun orbinCatalogGridSpec(
    width: Dp,
    gutter: Dp = ORBIN_CATALOG_GUTTER,
): CatalogGridSpec {
    val fallback = ORBIN_CATALOG_MIN_CARD * 2 + gutter * 2 + ORBIN_CATALOG_GAP
    val usable = if (width.isFinite && width > 0.dp) width else fallback
    val columns =
        maxOf(
            1,
            (
                (usable - gutter * 2 + ORBIN_CATALOG_GAP) /
                    (ORBIN_CATALOG_MIN_CARD + ORBIN_CATALOG_GAP)
            ).toInt(),
        )
    val colWidth = (usable - gutter * 2 - ORBIN_CATALOG_GAP * (columns - 1)) / columns
    return CatalogGridSpec(columns, colWidth * ORBIN_CATALOG_GRID_IMAGE_RATIO)
}

/**
 * One full-width catalog card, inside the catalog gutters, with a landscape image.
 * Unmeasured, unbounded, or gutter-only widths fall back to a usable minimum card width.
 */
fun orbinCatalogCardSpec(
    width: Dp,
    gutter: Dp = ORBIN_CATALOG_GUTTER,
): CatalogGridSpec {
    val fallback = ORBIN_CATALOG_MIN_CARD + gutter * 2
    val usable = if (width.isFinite && width > gutter * 2) width else fallback
    val cardWidth = usable - gutter * 2
    return CatalogGridSpec(columns = 1, imageHeight = cardWidth * ORBIN_CATALOG_IMAGE_RATIO)
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
            addAll(catalogModeMenuItems(mode, onModeChange))
        }
        if (sortLabel != null) {
            add(
                ReaderMenuItem(
                    stringResource(Res.string.reader_sort, sortLabel),
                    icon = ReaderIcons.ChevronDown,
                    onClick = onSort,
                ),
            )
        }
        if (onOpenMedia != null) {
            add(
                ReaderMenuItem(
                    stringResource(Res.string.next_board_media),
                    icon = ReaderIcons.Album,
                    onClick = onOpenMedia,
                ),
            )
        }
        if (onOpenBrowser != null) {
            add(
                ReaderMenuItem(
                    stringResource(Res.string.reader_open_browser),
                    icon = ReaderIcons.ShareForward,
                    onClick = onOpenBrowser,
                ),
            )
        }
    }

/** Offer direct access to every other presentation in catalog and Home overflow menus. */
@Composable
internal fun catalogModeMenuItems(
    mode: CatalogMode,
    onModeChange: (CatalogMode) -> Unit,
): List<ReaderMenuItem> =
    CatalogMode.entries.filter { it != mode }.map { target ->
        val label =
            when (target) {
                CatalogMode.LIST -> Res.string.reader_view_list
                CatalogMode.GRID -> Res.string.reader_view_grid
                CatalogMode.CARD -> Res.string.reader_view_card
            }
        val icon =
            when (target) {
                CatalogMode.LIST -> ReaderIcons.Menu
                CatalogMode.GRID -> ReaderIcons.Boards
                CatalogMode.CARD -> ReaderIcons.Album
            }
        ReaderMenuItem(stringResource(label), icon = icon) { onModeChange(target) }
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
    var revealed by rememberSaveable(row.id, row.imageSpoiler) { mutableStateOf(false) }
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
                                if (row.mediaLabel.isNotBlank()) {
                                    OrbinPill(
                                        text = row.mediaLabel,
                                        variant = PillVariant.Media,
                                        maxLines = 1,
                                        modifier =
                                            Modifier
                                                .align(
                                                    Alignment.BottomEnd,
                                                ).padding(6.dp)
                                                .clearAndSetSemantics {},
                                    )
                                }
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
        UnreadLabel(row.unread)
    }
}

/** Compact image tiles and full social cards share the same identities, actions, and spoiler state. */
@OptIn(ExperimentalLayoutApi::class)
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
    compactGrid: Boolean = false,
) {
    val colors = reader
    val title = rowTitle(row)
    val bodyStyle = if (wideWindow) OrbinType.postBodyWide.withReaderFont() else OrbinType.postBody.withReaderFont()
    val summary = listOfNotNull(rowSummary(row, title), metadata).joinToString(", ")
    val showThumbnail = row.hasPreview && !row.muted && thumbnail != null
    var revealed by rememberSaveable(row.id, row.imageSpoiler) { mutableStateOf(false) }
    val covered = showThumbnail && row.imageSpoiler && !(spoilerRevealed ?: revealed)
    val reveal = { if (onRevealSpoiler != null) onRevealSpoiler() else revealed = true }
    val revealLabel = stringResource(Res.string.reader_reveal_spoiler)
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
        border =
            if (selected) {
                BorderStroke(2.dp, colors.accent)
            } else {
                BorderStroke(ReaderSize.hairline, colors.hairline)
            },
        elevated = false,
        shape = RoundedCornerShape(if (compactGrid) 22.dp else NEKO_CARD_RADIUS_DP.dp),
    ) {
        if (compactGrid && LocalDensity.current.fontScale < READER_TRAILING_BELOW_FONT_SCALE) {
            Box(Modifier.fillMaxWidth().height(imageHeight)) {
                Box(
                    Modifier
                        .fillMaxSize()
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
                    } else if (showThumbnail) {
                        thumbnail.invoke(row, Modifier.fillMaxSize())
                    }
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.08f),
                            0.4f to Color.Black.copy(alpha = 0.02f),
                            1f to Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
                )
                FlowRow(
                    Modifier.align(Alignment.TopStart).padding(10.dp).padding(end = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (row.threadNumber.isNotBlank()) {
                        OrbinPill(
                            stringResource(Res.string.reader_post_number, row.threadNumber),
                            variant = PillVariant.Media,
                        )
                    }
                    if (metadata != null && row.board.isNotBlank()) {
                        OrbinPill("/${row.board}/", variant = PillVariant.Filled)
                    }
                }
                if (row.watched || row.bookmarked) {
                    Icon(
                        ReaderIcons.SavedFilled,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(18.dp),
                    )
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .pointerInput(row.id, onOpen, onLongClick) {
                            detectTapGestures(
                                onTap = { onOpen(row) },
                                onLongPress = onLongClick?.let { action -> { _: Offset -> action() } },
                            )
                        }.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (title.isNotBlank()) {
                        Text(
                            title,
                            style = OrbinType.body.withReaderFont().copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FlowRow(
                        Modifier.testTag(CatalogTags.COUNTS),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OrbinPill(
                            pluralStringResource(Res.plurals.next_row_replies, row.replies, row.replies),
                            variant = PillVariant.Media,
                            icon = ReaderIcons.Chat,
                        )
                        OrbinPill(
                            pluralStringResource(Res.plurals.next_row_files, row.media, row.media),
                            variant = PillVariant.Media,
                            icon = ReaderIcons.Album,
                        )
                        UnreadLabel(row.unread)
                        if (row.mediaLabel.isNotBlank() && !covered) {
                            OrbinPill(row.mediaLabel, variant = PillVariant.Media)
                        }
                    }
                }
            }
            return@OrbinCard
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(
                            if (selected) colors.accent else colors.accent.copy(alpha = 0.16f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val initial =
                        row.poster
                            .firstOrNull()
                            ?.uppercaseChar()
                            ?.toString() ?: "A"
                    Text(
                        initial,
                        style = OrbinType.meta.withReaderFont().copy(fontWeight = FontWeight.Bold),
                        color = if (selected) colors.onAccent else colors.accent,
                        maxLines = 1,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (row.poster.isNotBlank()) {
                            Text(
                                row.poster,
                                style = OrbinType.meta.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        if (metadata != null && row.board.isNotBlank()) {
                            OrbinPill(text = "/${row.board}/")
                        }
                    }
                    val subtitleLine = metadata?.takeIf { it.isNotBlank() } ?: row.activity.takeIf { it.isNotBlank() }
                    if (subtitleLine != null) {
                        Text(
                            subtitleLine,
                            style = OrbinType.caption2.withReaderFont(),
                            color = colors.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (row.watched || row.bookmarked) {
                    Icon(
                        imageVector = ReaderIcons.SavedFilled,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp).clearAndSetSemantics {},
                    )
                }
            }
            if (title.isNotBlank()) {
                CatalogTitle(
                    row,
                    title,
                    bodyStyle.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = titleMaxLines,
                )
            }
            if (row.excerpt.isNotBlank() && row.excerpt != title) {
                Text(
                    text = previewText(row.previewLines, colors),
                    style = OrbinType.body.withReaderFont(),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showThumbnail) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(imageHeight)
                        .clip(RoundedCornerShape(OrbinRadius.compactCard))
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
                        if (row.mediaLabel.isNotBlank()) {
                            OrbinPill(
                                text = row.mediaLabel,
                                variant = PillVariant.Media,
                                maxLines = 1,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clearAndSetSemantics {},
                            )
                        }
                    }
                }
            }
            CatalogIconCounts(row, style = bodyStyle, compactGrid = compactGrid)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogIconCounts(
    row: FeedRow,
    style: TextStyle,
    compactGrid: Boolean = false,
) {
    val colors = reader
    if (compactGrid) {
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
            UnreadLabel(row.unread)
        }
        return
    }
    FlowRow(
        Modifier.testTag(CatalogTags.COUNTS).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        OrbinPill(
            pluralStringResource(Res.plurals.next_row_replies, row.replies, row.replies),
            icon = ReaderIcons.Chat,
        )
        OrbinPill(
            pluralStringResource(Res.plurals.next_row_files, row.media, row.media),
            tint = colors.subject,
            icon = ReaderIcons.Album,
        )
        UnreadLabel(row.unread)
        if (row.threadNumber.isNotEmpty()) {
            OrbinPill(
                stringResource(Res.string.reader_post_number, row.threadNumber),
                tint = colors.inkMuted,
            )
        }
    }
}

private const val NEKO_CARD_RADIUS_DP = 20

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
internal val ORBIN_CATALOG_GUTTER = 10.dp
internal val ORBIN_CATALOG_GAP = 10.dp
internal val ORBIN_CATALOG_MIN_CARD = 152.dp
private const val ORBIN_CATALOG_IMAGE_RATIO = 0.75f
private const val ORBIN_CATALOG_GRID_IMAGE_RATIO = 1.1f

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
                .testTag(CatalogTags.SORT_TABS)
                .selectableGroup()
                .padding(horizontal = ORBIN_CATALOG_GUTTER, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(reader.surfaceVariant)
                .border(ReaderSize.hairline, reader.hairline, RoundedCornerShape(16.dp))
                .padding(4.dp)
                .heightIn(min = MIN_TOUCH_TARGET)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = tab.id == selectedId
            Column(
                modifier =
                    Modifier
                        .widthIn(min = 64.dp)
                        .heightIn(min = MIN_TOUCH_TARGET)
                        .clip(RoundedCornerShape(OrbinRadius.compactCard))
                        .background(if (selected) reader.accent.copy(alpha = 0.14f) else Color.Transparent)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { if (!selected) onSelect(tab.id) },
                        ).semantics { contentDescription = tab.label }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    tab.label,
                    style = if (largeText) OrbinType.caption1 else OrbinType.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) reader.accent else next.muted,
                    maxLines = if (largeText) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
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
