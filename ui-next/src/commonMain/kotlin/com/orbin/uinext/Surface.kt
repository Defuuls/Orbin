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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.rememberTextMeasurer
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
    val shape = RoundedCornerShape(topStart = NextRadius.tight, topEnd = NextRadius.tight)
    val destinations = listOf(NextDestination.FEED, NextDestination.DOWNLOADS, NextDestination.BOARDS)
    val channelCodes = listOf("01", "02", "03")
    val labels =
        listOf(
            stringResource(Res.string.next_feed_title),
            stringResource(Res.string.next_downloads_tab),
            stringResource(Res.string.next_launchpad_boards),
        )
    val icons = listOf(Icons.Outlined.Home, Icons.Outlined.Download, Icons.Outlined.GridView)
    val measurer = rememberTextMeasurer()
    val labelStyle = NextType.tab.copy(fontWeight = FontWeight.Bold)
    val widestLabel =
        labels.maxOf { label ->
            measurer.measure(text = label, style = labelStyle, maxLines = 1, softWrap = false).size.width
        }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(railInsets),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val cellWidth = (minOf(maxWidth, 640.dp) - 20.dp) / destinations.size.toFloat() - 12.dp
        val useRows = widestLabel > with(density) { cellWidth.toPx() }
        val dock =
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(next.elevated)
                .border(1.dp, next.hairline, shape)
                .selectableGroup()
                .padding(horizontal = 6.dp, vertical = 4.dp)
        if (useRows) {
            Column(modifier = dock, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                destinations.forEachIndexed { index, destination ->
                    DockTab(
                        label = labels[index],
                        channelCode = channelCodes[index],
                        icon = icons[index],
                        selected = selected == destination,
                        onClick = { onSelect(destination) },
                        modifier = Modifier.fillMaxWidth(),
                        horizontal = true,
                    )
                }
            }
        } else {
            Row(
                modifier = dock,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEachIndexed { index, destination ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(next.hairline),
                        )
                    }
                    DockTab(
                        label = labels[index],
                        channelCode = channelCodes[index],
                        icon = icons[index],
                        selected = selected == destination,
                        onClick = { onSelect(destination) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DockTab(
    label: String,
    channelCode: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
) {
    val ink = if (selected) next.ink else next.muted
    val tabShape = RoundedCornerShape(NextRadius.tight)
    val target =
        modifier
            .heightIn(min = RAIL_HEIGHT)
            .clip(tabShape)
            .background(if (selected) next.accentContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = if (horizontal) 16.dp else 4.dp, vertical = 6.dp)
    if (horizontal) {
        Row(
            modifier = target,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (selected) next.accent else next.hairline),
            )
            Text(
                text = channelCode,
                style = NextType.telemetry,
                color = if (selected) next.accent else next.faint,
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) next.accent else ink,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = NextType.tab,
                fontWeight = FontWeight.Bold,
                color = ink,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Column(
            modifier = target,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Mechanical detent status bar
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (selected) next.accent else next.hairline.copy(alpha = 0.4f)),
            )
            Gap(4)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (selected) next.accent else next.hairline),
                )
                Text(
                    text = channelCode,
                    style = NextType.telemetry,
                    color = if (selected) next.accent else next.faint,
                )
            }
            Gap(2)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) next.accent else ink,
                modifier = Modifier.size(20.dp),
            )
            Gap(2)
            Text(
                text = label,
                style = NextType.tab,
                fontWeight = FontWeight.Bold,
                color = ink,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Machined 6-blade geometric optical aperture iris.
 * Lineage: Leica M-System rangefinders, teenage engineering field hardware, and brand.md.
 */
@Composable
fun ApertureMark(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color? = null,
) {
    val ink = tint ?: next.ink
    val reticle = next.accent
    Canvas(
        modifier =
            modifier
                .size(size)
                .clearAndSetSemantics {},
    ) {
        val w = this.size.width
        val h = this.size.height
        val cx = w * 0.5f
        val cy = h * 0.5f
        val minDim = this.size.minDimension
        val stroke = Stroke(width = 1.25.dp.toPx())
        val thinStroke = Stroke(width = 1.dp.toPx())

        // Outer precision housing ring + inner aperture ring
        val outerR = minDim * 0.44f
        val innerR = minDim * 0.20f
        drawCircle(color = ink, radius = outerR, center = Offset(cx, cy), style = stroke)
        drawCircle(color = ink, radius = innerR, center = Offset(cx, cy), style = thinStroke)

        // 6-blade tangential aperture chords
        val cos60 = 0.5f
        val sin60 = 0.8660254f
        val dirs =
            arrayOf(
                1f to 0f,
                cos60 to sin60,
                -cos60 to sin60,
                -1f to 0f,
                -cos60 to -sin60,
                cos60 to -sin60,
            )
        for (i in dirs.indices) {
            val (dx0, dy0) = dirs[i]
            val (dx1, dy1) = dirs[(i + 1) % dirs.size]
            drawLine(
                color = ink,
                start = Offset(cx + dx0 * innerR, cy + dy0 * innerR),
                end = Offset(cx + dx1 * outerR, cy + dy1 * outerR),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // Cardinal reticle ticks + optical lock dot
        val tickInner = outerR * 0.82f
        val tickOuter = minDim * 0.49f
        drawLine(reticle, Offset(cx, cy - tickOuter), Offset(cx, cy - tickInner), strokeWidth = 1.25.dp.toPx())
        drawLine(reticle, Offset(cx, cy + tickInner), Offset(cx, cy + tickOuter), strokeWidth = 1.25.dp.toPx())
        drawLine(reticle, Offset(cx - tickOuter, cy), Offset(cx - tickInner, cy), strokeWidth = 1.25.dp.toPx())
        drawLine(reticle, Offset(cx + tickInner, cy), Offset(cx + tickOuter, cy), strokeWidth = 1.25.dp.toPx())
        drawCircle(
            color = reticle,
            radius = minDim * 0.065f,
            center = Offset(cx, cy),
        )
    }
}

@Composable
fun OrbitEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    val emblemShape = RoundedCornerShape(NextRadius.control)
    Box(
        modifier =
            modifier
                .size(size)
                .clip(emblemShape)
                .background(next.accentContainer)
                .border(1.dp, next.hairline, emblemShape)
                .padding(size * 0.14f)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        ApertureMark(modifier = Modifier.fillMaxSize(), size = size * 0.72f, tint = next.onAccentContainer)
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
        val shape = RoundedCornerShape(bottomStart = NextRadius.tight, bottomEnd = NextRadius.tight)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ).clip(shape)
                    .background(next.elevated)
                    .border(1.dp, next.hairline, shape)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag(NextTitleTags.COMPACT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OrbitEmblem(size = 22.dp)
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(next.accent),
            )
            Text(
                text = "HUD //",
                style = NextType.telemetry,
                color = next.accent,
            )
            Text(
                text = title,
                style = NextType.headline,
                color = next.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "[SIG:OK]",
                style = NextType.telemetry,
                color = next.muted,
            )
        }
    }
}

/**
 * Machined instrument channel bar pinned to the top of destinations.
 * Replaces bulky scrolling titles with a dense, machined HUD header (+68dp content gain).
 */
@Composable
fun MachinedChannelBar(
    channelCode: String,
    title: String,
    modifier: Modifier = Modifier,
    status: String = "SIG:NOMINAL",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(bottomStart = NextRadius.tight, bottomEnd = NextRadius.tight)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .clip(shape)
                .background(next.elevated)
                .border(1.dp, next.hairline, shape)
                .height(48.dp)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ApertureMark(size = 20.dp)
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(next.accent),
        )
        Text(
            text = channelCode,
            style = NextType.telemetry,
            color = next.accent,
        )
        Text(
            text = "·",
            style = NextType.telemetry,
            color = next.hairline,
        )
        Text(
            text = title,
            style = NextType.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = next.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = status,
            style = NextType.telemetry,
            color = next.faint,
        )
        actions()
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
    Column(
        modifier =
            modifier.fillMaxWidth().padding(
                start = inset,
                end = inset,
                top = NextSpace.titleTop,
                bottom = NextSpace.titleBottom,
            ),
    ) {
        // Machined Telemetry Header Strip
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(next.accent),
            )
            Text(
                text = "CH-01 // INSTRUMENT",
                style = NextType.telemetry,
                color = next.accent,
            )
            Text(
                text = "·",
                style = NextType.telemetry,
                color = next.hairline,
            )
            Text(
                text = "SIG:NOMINAL",
                style = NextType.telemetry,
                color = next.muted,
            )
            Box(Modifier.weight(1f))
            Text(
                text = "[OPTIC-26]",
                style = NextType.telemetry,
                color = next.faint,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(next.hairline),
        )
        Gap(10)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .width(28.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(next.accent),
                )
                Gap(10)
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
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (showEmblem) {
                OrbitEmblem(modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
            }
        }
    }
}

/** Structural 1dp hairline separator — prefer inside [GroupedSection]; full-bleed only between major blocks. */
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
        thickness = 1.dp,
        color = next.hairline,
    )
}

/** OPTIC-26 machined card section — flat card on surface background with 1dp structural border. */
@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(NextRadius.card)
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = NextSpace.gutterTight)) {
        if (header != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 10.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(1.dp))
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
            modifier = Modifier.fillMaxWidth().border(1.dp, next.hairline, shape),
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
        thickness = 1.dp,
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
                    .background(next.raised)
                    .border(1.dp, next.hairline, shape),
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

/** The machined indicator mark that identifies which board a row came from. */
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
                .clip(RoundedCornerShape(NextRadius.tight))
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
 * Machined vertical hardware navigation rail for wide viewports (tablets, foldables, landscape).
 * Replaces bottom floating bar with an instrument side rack docked to the leading edge.
 */
@Composable
fun IndustrialNavRail(
    selected: NextDestination,
    onSelect: (NextDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinations = listOf(NextDestination.FEED, NextDestination.DOWNLOADS, NextDestination.BOARDS)
    val channelCodes = listOf("01", "02", "03")
    val labels =
        listOf(
            stringResource(Res.string.next_feed_title),
            stringResource(Res.string.next_downloads_tab),
            stringResource(Res.string.next_launchpad_boards),
        )
    val icons = listOf(Icons.Outlined.Home, Icons.Outlined.Download, Icons.Outlined.GridView)
    val insets =
        WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Start,
        )
    Column(
        modifier =
            modifier
                .windowInsetsPadding(insets)
                .background(next.elevated)
                .selectableGroup()
                .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ApertureMark(size = 28.dp)
        Gap(6)
        Text(
            text = "OPTIC",
            style = NextType.telemetry,
            color = next.accent,
        )
        Gap(16)
        destinations.forEachIndexed { index, destination ->
            val isSelected = selected == destination
            val ink = if (isSelected) next.ink else next.muted
            val keyShape = RoundedCornerShape(NextRadius.tight)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(keyShape)
                        .background(if (isSelected) next.accentContainer else Color.Transparent)
                        .border(1.dp, if (isSelected) next.accent else Color.Transparent, keyShape)
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onSelect(destination) },
                        )
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 16.dp, height = 2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (isSelected) next.accent else next.hairline),
                )
                Gap(4)
                Text(
                    text = channelCodes[index],
                    style = NextType.telemetry,
                    color = if (isSelected) next.accent else next.faint,
                )
                Gap(4)
                Icon(
                    imageVector = icons[index],
                    contentDescription = null,
                    tint = if (isSelected) next.accent else ink,
                    modifier = Modifier.size(22.dp),
                )
                Gap(4)
                Text(
                    text = labels[index],
                    style = NextType.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Gap(10)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "[OPTIC-26]",
            style = NextType.telemetry,
            color = next.faint,
        )
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
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        ) {
            val isWideScreen = maxWidth >= 600.dp
            if (isWideScreen && pillDestination != null && onDestination != null) {
                Row(modifier = Modifier.fillMaxSize()) {
                    IndustrialNavRail(
                        selected = pillDestination,
                        onSelect = onDestination,
                        modifier = Modifier.width(80.dp).fillMaxHeight(),
                    )
                    Box(
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(next.hairline),
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        content(PaddingValues(bottom = 16.dp + bottomInset()))
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
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
