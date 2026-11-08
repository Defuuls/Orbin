package com.orbin.uinext

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_downloads_tab
import com.orbin.uinext.resources.next_feed_title
import com.orbin.uinext.resources.next_launchpad_boards
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.stringResource

/**
 * Every destination the app can show.
 *
 * [FEED], [DOWNLOADS] and [BOARDS] are the three places you live, so they are the tabs. [SETTINGS]
 * is somewhere you visit, opened from the Feed header, and like threads and Search it carries no
 * bottom chrome.
 */
enum class NextDestination {
    FEED,
    BOARDS,
    DOWNLOADS,
    SETTINGS,
}

/**
 * The permanent chrome: a floating dock with labeled Feed, Downloads and Boards destinations.
 *
 * Settings is not a tab: it is visited, not lived in, so it opens from the Feed header instead of
 * taking a slot here.
 */
@Composable
fun DestinationPill(
    selected: NextDestination,
    onSelect: (NextDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val railInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    val shape = RoundedCornerShape(NextRadius.continuous)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(railInsets)
                .padding(horizontal = NextSpace.chromeInset, vertical = NextSpace.chromeBottom),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier =
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(next.elevated)
                    .border(0.75.dp, next.hairline, shape)
                    .selectableGroup()
                    .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockTab(
                label = stringResource(Res.string.next_feed_title),
                icon = Icons.Outlined.Home,
                selected = selected == NextDestination.FEED,
                onClick = { onSelect(NextDestination.FEED) },
                modifier = Modifier.weight(1f),
            )
            DockTab(
                label = stringResource(Res.string.next_downloads_tab),
                icon = Icons.Outlined.Download,
                selected = selected == NextDestination.DOWNLOADS,
                onClick = { onSelect(NextDestination.DOWNLOADS) },
                modifier = Modifier.weight(1f),
            )
            DockTab(
                label = stringResource(Res.string.next_launchpad_boards),
                icon = Icons.Outlined.GridView,
                selected = selected == NextDestination.BOARDS,
                onClick = { onSelect(NextDestination.BOARDS) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DockTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = if (selected) next.onAccent else next.muted
    Column(
        modifier =
            modifier
                .heightIn(min = RAIL_HEIGHT)
                .clip(RoundedCornerShape(NextRadius.pill))
                .background(if (selected) next.accent else Color.Transparent)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
        Gap(4)
        Text(
            text = label,
            style = NextType.tab,
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OrbitEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    val ink = next.onAccentContainer
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(next.accentContainer)
                .padding(size * 0.16f)
                .clearAndSetSemantics {},
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 1.5.dp.toPx())
            drawCircle(color = ink, radius = this.size.minDimension * 0.33f, style = stroke)
            drawOval(
                color = ink,
                topLeft = Offset(0f, this.size.height * 0.30f),
                size = Size(this.size.width, this.size.height * 0.40f),
                style = stroke,
            )
            drawCircle(
                color = ink,
                radius = this.size.minDimension * 0.08f,
                center = Offset(this.size.width * 0.92f, this.size.height * 0.50f),
            )
        }
    }
}

/** Space to keep scrolling content clear of [CompactTitleBar]. */
val COMPACT_TITLE_CLEARANCE = 56.dp

/** Clearance for compact chrome when text has been enlarged by the system. */
@Composable
fun compactTitleClearance(): Dp {
    val textHeight = with(LocalDensity.current) { NextType.headline.lineHeight.toDp() }
    return maxOf(24.dp, textHeight) + 32.dp
}

/** Semantics tags distinguishing the large in-content title from the compact overlay. */
object NextTitleTags {
    const val LARGE = "next_large_title"
    const val COMPACT = "next_compact_title"
}

/**
 * Compact matte title that appears once the large in-content [ScreenTitle] has scrolled away.
 * Uses a distinct test tag so scroll-away assertions still target the large title only.
 */
@Composable
fun CompactTitleBar(
    title: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 2 } + fadeIn(),
        exit = slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 2 } + fadeOut(),
        modifier = modifier.fillMaxWidth(),
    ) {
        val shape = RoundedCornerShape(NextRadius.control)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ).padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(shape)
                    .background(next.elevated)
                    .border(0.75.dp, next.hairline, shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag(NextTitleTags.COMPACT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OrbitEmblem(size = 24.dp)
            Text(
                text = title,
                style = NextType.headline,
                color = next.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * A screen title set in the content rather than in a bar above it.
 *
 * Large, heavy, tightly tracked — scrolls away with the content because a title is information,
 * not furniture.
 */
@Composable
fun ScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    size: Int = 34,
    // Grids already inset their content by the gutter; they pass zero so every title on every
    // screen starts at the same 16dp line.
    inset: Dp = NextSpace.gutter,
) {
    val titleStyle =
        when {
            size >= 32 -> NextType.largeTitle
            size >= 26 -> NextType.title1
            else -> NextType.title2
        }
    val showEmblem = size >= 32 && LocalDensity.current.fontScale < 1.4f
    Row(
        modifier =
            modifier.fillMaxWidth().padding(
                start = inset,
                end = inset,
                top = NextSpace.titleTop,
                bottom = NextSpace.titleBottom,
            ),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .width(28.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(next.accent),
            )
            Gap(12)
            Text(
                text = text,
                style = titleStyle,
                color = next.ink,
                modifier = Modifier.testTag(NextTitleTags.LARGE).semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = NextType.subheadline,
                    color = next.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (showEmblem) {
            OrbitEmblem(modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
        }
    }
}

/** Soft separator — prefer inside [GroupedSection]; full-bleed only between major blocks. */
@Composable
fun Hairline(
    modifier: Modifier = Modifier,
    inset: Boolean = false,
) {
    HorizontalDivider(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = if (inset) NextSpace.gutter else 0.dp),
        thickness = 0.5.dp,
        color = next.hairline,
    )
}

/** M3 tonal card section — flat card on surface background, sentence-case header. */
@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape =
        RoundedCornerShape(
            topStart = NextRadius.card,
            topEnd = NextRadius.control,
            bottomEnd = NextRadius.card,
            bottomStart = NextRadius.control,
        )
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = NextSpace.gutterTight)) {
        if (header != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 10.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(18.dp)
                        .clip(CircleShape)
                        .background(next.accent),
                )
                Text(
                    text = header,
                    style = NextType.headline,
                    fontWeight = FontWeight.Bold,
                    color = next.ink,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
        }
        Card(
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = next.raised, contentColor = next.ink),
            elevation = CardDefaults.cardElevation(0.dp),
            modifier = Modifier.fillMaxWidth().border(0.75.dp, next.hairline, shape),
            content = content,
        )
        if (footer != null) {
            Text(
                text = footer,
                style = NextType.footnote,
                color = next.muted,
                modifier = Modifier.padding(start = NextSpace.rowX, end = NextSpace.rowX, top = 10.dp),
            )
        }
    }
}

/** Divider drawn inside a [GroupedSection]. */
@Composable
fun GroupedDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = NextSpace.rowX),
        thickness = 0.5.dp,
        color = next.hairline,
    )
}

/** Shape for an item in a grouped card sequence. */
fun groupedCardShape(
    index: Int,
    count: Int,
): Shape =
    when {
        count <= 1 -> RoundedCornerShape(NextRadius.card)
        index == 0 -> RoundedCornerShape(topStart = NextRadius.card, topEnd = NextRadius.card)
        index == count - 1 -> RoundedCornerShape(bottomStart = NextRadius.card, bottomEnd = NextRadius.card)
        else -> RectangleShape
    }

/**
 * Emits individual lazy items with grouped card styling and virtualization.
 *
 * Each item is rendered in its own slot with top/middle/bottom card corner radii,
 * allowing Compose to virtualize long lists without eager measurement.
 */
inline fun <T> LazyListScope.groupedItems(
    items: List<T>,
    noinline key: ((T) -> Any)? = null,
    crossinline itemContent: @Composable (index: Int, item: T) -> Unit,
) {
    itemsIndexed(
        items = items,
        key = if (key != null) { _, item -> key(item) } else null,
    ) { index, item ->
        val shape = groupedCardShape(index, items.size)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NextSpace.gutterTight)
                    .clip(shape)
                    .background(next.raised),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                itemContent(index, item)
                if (index < items.size - 1) {
                    GroupedDivider()
                }
            }
        }
    }
}

/** A row's secondary line: counts and timestamps. */
@Composable
fun MetaLine(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    maxLines: Int = 1,
    minLines: Int = 1,
) {
    Text(
        text = text,
        style = NextType.footnote,
        color = color ?: next.muted,
        maxLines = maxLines,
        minLines = minLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The small coloured disc that marks which board a row came from. */
@Composable
fun BoardDot(
    board: String,
    modifier: Modifier = Modifier,
    size: Dp = 6.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(size / 2))
                .background(boardHue(board)),
    )
}

/**
 * An action rendered as a word rather than an icon in a bar.
 *
 * M3 chip-aligned action with tonal/accent fill and proper touch target.
 */
@Composable
fun InlineAction(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    selected: Boolean? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(NextRadius.control)
    val filled = selected ?: accent
    val interaction = remember { MutableInteractionSource() }
    val text =
        @Composable {
            Text(
                text = label,
                style = NextType.callout,
                fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Medium,
                color = if (filled) next.onAccent else next.muted,
                modifier =
                    Modifier
                        .clip(shape)
                        .background(if (filled) next.accent else Color.Transparent)
                        .padding(horizontal = if (filled) 13.dp else 4.dp, vertical = 7.dp),
            )
        }
    if (onClick == null) {
        Box(modifier = modifier) { text() }
        return
    }
    val press =
        if (selected == null) {
            Modifier.clickable(
                role = Role.Button,
                indication = androidx.compose.material3.ripple(color = next.accent),
                interactionSource = interaction,
                onClick = onClick,
            )
        } else {
            Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        }
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
                .clip(shape)
                .then(press),
        contentAlignment = Alignment.Center,
    ) {
        text()
    }
}

/** A small tinted label: the kind of a search result, the board on a media tile. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val hue = tint ?: next.accent
    Text(
        text = text,
        style = NextType.caption2,
        color = hue,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .clip(RoundedCornerShape(NextRadius.tight))
                .background(hue.copy(alpha = if (next.dark) 0.18f else 0.12f))
                .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/**
 * A board label drawn over media: white on a dark scrim, so it reads on any image, the way the
 * play button does. [Pill] is for labels on the app's own surfaces.
 */
@Composable
fun MediaBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = NextType.caption2,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .clip(RoundedCornerShape(NextRadius.tight))
                .background(Color.Black.copy(alpha = MEDIA_BADGE_SCRIM))
                .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

private const val MEDIA_BADGE_SCRIM = 0.55f

/** Where a thumbnail will load: rounded, filled with stand-in artwork rather than flat grey. */
@Composable
fun MediaTile(
    modifier: Modifier = Modifier,
    seed: Int = 0,
    badge: String? = null,
    radius: Dp = NextRadius.tile,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(radius))
                .background(placeholderArt(seed)),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (badge != null) {
            MediaBadge(text = badge, modifier = Modifier.padding(7.dp))
        }
    }
}

/**
 * Every screen: the ground, the chrome, and the room a scrolling list has to leave for it.
 *
 * Wrapped around [androidx.compose.material3.Scaffold]. Pass [destination] + [onDestination]
 * for tab chrome. Only [NextDestination.FEED], [NextDestination.DOWNLOADS] and [NextDestination.BOARDS]
 * draw it; every other screen draws no bottom chrome, because its large title already says where you
 * are and a floating name bar would only repeat it over the content.
 * [where] names the screen for accessibility services as its pane title.
 */
@Composable
fun NextScaffold(
    where: String?,
    modifier: Modifier = Modifier,
    railVisible: Boolean = true,
    destination: NextDestination? = null,
    onDestination: ((NextDestination) -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val pillDestination = destination?.takeIf { it.drawsPill() }?.takeIf { onDestination != null }
    val density = LocalDensity.current
    var measuredChromeHeight by remember { mutableStateOf(0.dp) }
    val defaultBottom = RAIL_HEIGHT + RAIL_CLEARANCE + bottomInset()
    val bottom =
        if (pillDestination != null) {
            maxOf(defaultBottom, measuredChromeHeight + 12.dp)
        } else {
            NO_RAIL_CLEARANCE + bottomInset()
        }
    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .then(if (where != null) Modifier.semantics { paneTitle = where } else Modifier),
        containerColor = next.background,
        contentColor = next.ink,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        ) {
            content(PaddingValues(bottom = bottom))
            if (pillDestination != null && onDestination != null) {
                AnimatedVisibility(
                    visible = railVisible,
                    enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
                    exit = slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    DestinationPill(
                        selected = pillDestination,
                        onSelect = onDestination,
                        modifier =
                            Modifier.onSizeChanged {
                                measuredChromeHeight = with(density) { it.height.toDp() }
                            },
                    )
                }
            }
        }
    }
}

/** Vertical rhythm and the height of the one bar. */
val GUTTER = NextSpace.gutter
val RAIL_HEIGHT = 64.dp

/** The destinations that earn a tab in the permanent chrome. Everything else draws none. */
private val CHROME_DESTINATIONS = setOf(NextDestination.FEED, NextDestination.DOWNLOADS, NextDestination.BOARDS)

/**
 * Whether this destination draws the permanent pill.
 *
 * The one place the rule is decided. Settings answers `false` here and draws no bottom chrome.
 */
internal fun NextDestination?.drawsPill(): Boolean = this in CHROME_DESTINATIONS

private val RAIL_CLEARANCE = 48.dp
internal val NO_RAIL_CLEARANCE = 16.dp

/** The smallest thing a finger should have to hit. */
val MIN_TOUCH_TARGET = 48.dp

@Composable
internal fun Modifier.contentInsets(): Modifier =
    windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    )

@Composable
internal fun bottomInset(): Dp = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()

@Composable
internal fun Gap(height: Int) {
    Box(modifier = Modifier.height(height.dp))
}

@Composable
internal fun Surface(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(next.background)) { content() }
}

@Composable
internal fun WidthSpacer(width: Int) {
    Box(modifier = Modifier.width(width.dp))
}

/**
 * Empty / loading / error — same large title language, one calm action when there is one.
 */
@Composable
fun MessageScreen(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    where: String? = null,
    destination: NextDestination? = null,
    onDestination: ((NextDestination) -> Unit)? = null,
    // Loading a grid: show the grid's shape instead of an empty page, so the layout does not jump.
    skeleton: Boolean = false,
) {
    NextScaffold(
        where = where.takeIf { !destination.drawsPill() },
        modifier = modifier,
        destination = destination,
        onDestination = onDestination,
    ) { _ ->
        Column(modifier = Modifier.fillMaxSize().contentInsets()) {
            ScreenTitle(text = title, subtitle = subtitle)
            if (actionLabel != null) {
                Box(modifier = Modifier.padding(horizontal = GUTTER - 4.dp)) {
                    InlineAction(label = actionLabel, accent = true, onClick = onAction)
                }
            }
            if (skeleton) SkeletonGrid(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Stand-in cards in the grid's own shape while its content loads. Hidden from accessibility
 * services: the screen's subtitle already says it is loading.
 */
@Composable
internal fun SkeletonGrid(modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GRID_MIN_CELL),
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {},
        contentPadding = PaddingValues(horizontal = GRID_SIDE_INSET),
        userScrollEnabled = false,
    ) {
        items(SKELETON_CARDS) {
            Column(
                modifier =
                    Modifier
                        .padding(GRID_CELL_PADDING)
                        .clip(RoundedCornerShape(GRID_TILE_RADIUS))
                        .background(next.raised),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(next.hairline.copy(alpha = SKELETON_TILE_ALPHA)),
                )
                Column(modifier = Modifier.padding(GRID_TEXT_INSET)) {
                    PendingBar(width = 36.dp, height = 10.dp)
                    Gap(8)
                    PendingBar(width = 120.dp, height = 14.dp)
                    Gap(6)
                    PendingBar(width = 88.dp, height = 10.dp)
                }
            }
        }
    }
}

private const val SKELETON_CARDS = 6
private const val SKELETON_TILE_ALPHA = 0.5f
