package com.orbin.uinext

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_board_media
import com.orbin.uinext.resources.next_row_files
import com.orbin.uinext.resources.next_row_replies
import com.orbin.uinext.resources.reader_catalog_counts
import com.orbin.uinext.resources.reader_catalog_stats
import com.orbin.uinext.resources.reader_muted
import com.orbin.uinext.resources.reader_open_browser
import com.orbin.uinext.resources.reader_pick_board
import com.orbin.uinext.resources.reader_reload
import com.orbin.uinext.resources.reader_reveal_spoiler
import com.orbin.uinext.resources.reader_row_meta
import com.orbin.uinext.resources.reader_row_summary
import com.orbin.uinext.resources.reader_search
import com.orbin.uinext.resources.reader_sort
import com.orbin.uinext.resources.reader_unread
import com.orbin.uinext.resources.reader_view_grid
import com.orbin.uinext.resources.reader_view_list
import com.orbin.uinext.tokens.ReaderColors
import com.orbin.uinext.tokens.ReaderRadius
import com.orbin.uinext.tokens.ReaderRatio
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import com.orbin.uinext.tokens.ReaderType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** How a board catalog lays its threads out. The reader chooses; see docs/design-system. */
enum class CatalogMode {
    /** One row per thread: thumbnail, title, post number and date, preview, reply count. */
    LIST,

    /** Cards in as many columns as fit, using Clover's card/image ratios and reply/file counts. */
    GRID,
}

object CatalogTags {
    const val GRID = "catalog-grid"
    const val ROW = "catalog-row"
    const val CARD = "catalog-card"
    const val PILL = "catalog-pill"
    const val COUNTS = "catalog-counts"
    const val THUMBNAIL = "catalog-thumbnail"
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
    onOpenBrowser: (() -> Unit)? = null,
    headerContent: (@Composable () -> Unit)? = null,
    stateContent: (@Composable () -> Unit)? = null,
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
            overflow = catalogOverflow(mode, onModeChange, sortLabel, onSort, onOpenMedia, onOpenBrowser),
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
            val columns = if (grid) catalogGridColumns(maxWidth) else 1
            val gap = if (grid) ReaderSpace.gridGap else NO_GAP
            val outer = if (grid) ReaderSpace.gridOuter else NO_GAP
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
                        row == null && grid -> Box(Modifier.fillMaxWidth().aspectRatio(1f))
                        row == null -> Box(Modifier.fillMaxWidth().height(cellMin))
                        grid -> CatalogCard(row, row.id == selectedRowId, onOpenRow, thumbnail, wideWindow = wideWindow)
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
) {
    val colors = reader
    val showThumbnail = row.hasPreview && !row.muted && thumbnail != null
    var revealed by rememberSaveable(row.id) { mutableStateOf(false) }
    val covered = showThumbnail && row.imageSpoiler && !(spoilerRevealed ?: revealed)
    val reveal = { if (onRevealSpoiler != null) onRevealSpoiler() else revealed = true }
    val revealLabel = stringResource(Res.string.reader_reveal_spoiler)
    val title = rowTitle(row)
    val inset = readerPostInset(wideWindow)
    val bodyStyle = if (wideWindow) ReaderType.postBodyWide.withReaderFont() else ReaderType.postBody.withReaderFont()
    val detailStyle =
        if (wideWindow) {
            ReaderType.postDetailsWide.withReaderFont()
        } else {
            ReaderType.postDetails
                .withReaderFont()
        }
    val summary = listOfNotNull(rowSummary(row, title), metadata).joinToString(", ")
    // The tag leads the chain: clearAndSetSemantics clears the descendants after this node.
    Column(
        modifier
            .testTag(itemTag ?: CatalogTags.ROW)
            .fillMaxWidth()
            .background(if (selected) colors.selected else colors.background)
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
                                .clip(RoundedCornerShape(ReaderRadius.card))
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
                        text = remember(row.excerpt, colors) { previewText(row.excerpt, colors) },
                        style = bodyStyle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CatalogCounts(row, grid = false, style = bodyStyle)
            }
        }
        if (showDivider) ReaderHairline(Modifier.padding(horizontal = inset))
    }
}

/** Clover's catalog counters are plain text; watched threads retain Orbin's unread marker. */
@Composable
private fun CatalogCounts(
    row: FeedRow,
    grid: Boolean,
    style: TextStyle = ReaderType.postBody,
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
                if (grid) {
                    stringResource(Res.string.reader_catalog_stats, row.replies, row.media)
                } else {
                    stringResource(
                        Res.string.reader_catalog_counts,
                        pluralStringResource(Res.plurals.next_row_replies, row.replies, row.replies),
                        pluralStringResource(Res.plurals.next_row_files, row.media, row.media),
                    )
                },
            style = style.withReaderFont(),
            color = colors.inkMuted,
        )
        if (row.unread > 0) {
            Text(
                pluralStringResource(Res.plurals.reader_unread, row.unread, row.unread),
                modifier = Modifier.fillMaxWidth(),
                style = ReaderType.meta.withReaderFont(),
                color = colors.accent,
            )
        }
    }
}

/** A thread as a grid card with Clover's image ratio and plain reply/file counters. */
@Composable
internal fun CatalogCard(
    row: FeedRow,
    selected: Boolean,
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
    wideWindow: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val colors = reader
    val shape = RoundedCornerShape(ReaderRadius.card)
    val title = rowTitle(row)
    val bodyStyle = if (wideWindow) ReaderType.postBodyWide.withReaderFont() else ReaderType.postBody.withReaderFont()
    val summary = listOfNotNull(rowSummary(row, title), metadata).joinToString(", ")
    val showThumbnail = row.hasPreview && !row.muted && thumbnail != null
    var revealed by rememberSaveable(row.id) { mutableStateOf(false) }
    val covered = showThumbnail && row.imageSpoiler && !(spoilerRevealed ?: revealed)
    val reveal = { if (onRevealSpoiler != null) onRevealSpoiler() else revealed = true }
    val revealLabel = stringResource(Res.string.reader_reveal_spoiler)
    Column(
        modifier
            .testTag(itemTag ?: CatalogTags.CARD)
            .fillMaxWidth()
            .shadow(ReaderSize.cardElevation, shape)
            .clip(shape)
            .background(if (selected) colors.selected else colors.background)
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
        ReaderCatalogCardLayout(
            image = { imageModifier ->
                Box(
                    imageModifier
                        .testTag(thumbnailTag ?: CatalogTags.THUMBNAIL)
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
                    } else if (showThumbnail) {
                        thumbnail(row, Modifier.fillMaxSize())
                    }
                }
            },
        ) { textModifier ->
            Column(textModifier, verticalArrangement = Arrangement.spacedBy(ReaderSpace.line)) {
                CatalogTitle(row, title, bodyStyle, maxLines = 3)
                if (metadata != null) {
                    Text(
                        metadata,
                        style = ReaderType.meta.withReaderFont(),
                        color = colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!row.muted && row.excerpt.isNotBlank()) {
                    Text(
                        text = remember(row.excerpt, colors) { previewText(row.excerpt, colors) },
                        style = bodyStyle,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                CatalogCounts(row, grid = true, style = bodyStyle)
            }
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
        Placeholder(ReaderType.meta.lineHeight, ReaderType.meta.lineHeight, PlaceholderVerticalAlign.Center)
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

/** Clover's 9:18 card and 16:13 image ratios, with text expansion for accessibility. */
@Composable
fun ReaderCatalogCardLayout(
    modifier: Modifier = Modifier,
    image: @Composable (Modifier) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val textMinimum = maxWidth / ReaderRatio.gridCard - maxWidth / ReaderRatio.gridThumbnail
        Column(Modifier.fillMaxWidth()) {
            image(Modifier.fillMaxWidth().aspectRatio(ReaderRatio.gridThumbnail))
            content(Modifier.fillMaxWidth().heightIn(min = textMinimum).padding(ReaderSpace.cardPad))
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
