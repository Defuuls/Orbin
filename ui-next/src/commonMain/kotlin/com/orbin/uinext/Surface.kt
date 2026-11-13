package com.orbin.uinext

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
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
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType
import com.orbin.uinext.tokens.ReaderDarkColors

/** Drawer roots and secondary destinations shared by the Android and iOS hosts. */
enum class NextDestination {
    FEED,
    SEARCH,
    BOARDS,
    WATCHED,
    SAVED,
    DOWNLOADS,
    SETTINGS,
}

/** Compatibility entry point; permanent bottom navigation has been replaced by the drawer. */
@Deprecated("Use ReaderDrawer or ReaderScaffold")
@Suppress("UNUSED_PARAMETER")
@Composable
fun DestinationPill(
    selected: NextDestination,
    onSelect: (NextDestination) -> Unit,
    modifier: Modifier = Modifier,
    immersive: Boolean = false,
) {
    ReaderDrawer(selected = selected, onSelect = onSelect, modifier = modifier)
}

/** Space to keep scrolling content clear of [CompactTitleBar]. */
val COMPACT_TITLE_CLEARANCE = 56.dp

/** Clearance for compact chrome when text has been enlarged by the system. */
@Composable
fun compactTitleClearance(): Dp {
    if (LocalReaderFramePresent.current) return 0.dp
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
    if (LocalReaderFramePresent.current) return
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 2 } + fadeIn(),
        exit = slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 2 } + fadeOut(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(next.background)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            if (LocalReaderFramePresent.current) {
                                WindowInsetsSides.Horizontal
                            } else {
                                WindowInsetsSides.Top +
                                    WindowInsetsSides.Horizontal
                            },
                        ),
                    ).padding(horizontal = GUTTER, vertical = 14.dp)
                    .testTag(NextTitleTags.COMPACT),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                style = NextType.titleLarge,
                color = next.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
    }
}

/**
 * A compact screen title that scrolls with its content.
 *
 * Uses the same proportional type as the fixed navigation headers.
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
    if (LocalReaderFramePresent.current) return
    Column(
        modifier =
            modifier.fillMaxWidth().padding(
                start = inset,
                end = inset,
                top = NextSpace.titleTop,
                bottom = NextSpace.titleBottom,
            ),
    ) {
        Text(
            text = text,
            style = if (size >= 32) NextType.largeTitle else NextType.titleLarge,
            color = next.ink,
            modifier = Modifier.testTag(NextTitleTags.LARGE).semantics { heading() },
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = NextType.footnote,
                color = next.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
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
        thickness = 0.5.dp,
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
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = NextSpace.gutter)) {
        if (header != null) {
            Text(
                text = header,
                style = NextType.sectionHeader,
                color = next.muted,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() },
            )
        }
        Column(modifier = Modifier.fillMaxWidth(), content = content)
        if (footer != null) {
            Text(
                text = footer,
                style = NextType.footnote,
                color = next.muted,
                modifier = Modifier.padding(top = 8.dp),
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
    enabled: Boolean = true,
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
                color =
                    if (!enabled) {
                        next.faint
                    } else if (filled) {
                        next.onAccent
                    } else {
                        next.muted
                    },
                modifier =
                    Modifier
                        .clip(shape)
                        .background(
                            if (!filled) {
                                Color.Transparent
                            } else if (enabled) {
                                next.accent
                            } else {
                                next.raised
                            },
                        ).padding(horizontal = if (filled) 13.dp else 4.dp, vertical = 7.dp),
            )
        }
    if (onClick == null) {
        Box(modifier = modifier) { text() }
        return
    }
    val press =
        if (selected == null) {
            Modifier.clickable(
                enabled = enabled,
                role = Role.Button,
                indication = androidx.compose.material3.ripple(color = next.accent),
                interactionSource = interaction,
                onClick = onClick,
            )
        } else {
            Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
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
 * Pass [destination] + [onDestination] for the shared app bar and navigation drawer.
 * The five primary destinations draw it; secondary screens use back navigation.
 * [where] names the screen for accessibility services as its pane title.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun NextScaffold(
    where: String?,
    modifier: Modifier = Modifier,
    railVisible: Boolean = true,
    destination: NextDestination? = null,
    onDestination: ((NextDestination) -> Unit)? = null,
    immersive: Boolean = false,
    onBack: (() -> Unit)? = null,
    actions: List<ReaderAction> = emptyList(),
    overflow: List<ReaderMenuItem> = emptyList(),
    drawerDestinations: Set<NextDestination>? = null,
    drawAppBar: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    // A host can frame a destination explicitly; nested screens keep their content layout.
    if (LocalReaderFramePresent.current) {
        Box(modifier.fillMaxSize()) { content(PaddingValues(bottom = bottomInset())) }
        return
    }
    val root = destination?.takeIf { it.isReaderRoot() }
    if (root != null || onBack != null) {
        ReaderScaffold(
            title = root?.let { readerDestinationTitle(it) } ?: where.orEmpty(),
            modifier = modifier,
            destination = root,
            onDestination = onDestination,
            immersive = immersive,
            onBack = onBack,
            actions = actions,
            overflow = overflow,
            drawerDestinations = drawerDestinations,
            drawAppBar = drawAppBar,
            content = content,
        )
    } else {
        Scaffold(
            modifier =
                modifier.fillMaxSize().then(
                    if (where !=
                        null
                    ) {
                        Modifier.semantics { paneTitle = where }
                    } else {
                        Modifier
                    },
                ),
            containerColor = if (immersive) TikTokDesign.Black else next.background,
            contentColor = if (immersive) TikTokDesign.White else next.ink,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { content(PaddingValues(bottom = bottomInset())) }
        }
    }
}

/** Vertical rhythm and the height of the one bar. */
val GUTTER = NextSpace.gutter
val RAIL_HEIGHT = TikTokDesign.NavigationHeight

internal val NO_RAIL_CLEARANCE = 16.dp

/** The smallest thing a finger should have to hit. */
val MIN_TOUCH_TARGET = 48.dp

@Composable
internal fun Modifier.contentInsets(): Modifier =
    windowInsetsPadding(
        WindowInsets.safeDrawing.only(
            if (LocalReaderFramePresent.current) {
                WindowInsetsSides.Horizontal
            } else {
                WindowInsetsSides.Top +
                    WindowInsetsSides.Horizontal
            },
        ),
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
    if (destination == NextDestination.FEED) {
        NextTheme {
            NextScaffold(
                where = where ?: title,
                modifier = modifier,
                destination = destination,
                onDestination = onDestination,
                immersive = true,
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = padding.calculateBottomPadding())
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                if (LocalReaderFramePresent.current) {
                                    WindowInsetsSides.Horizontal
                                } else {
                                    WindowInsetsSides.Top +
                                        WindowInsetsSides.Horizontal
                                },
                            ),
                        ),
                ) {
                    if (!LocalReaderFramePresent.current) {
                        Text(
                            title,
                            style = NextType.titleMedium,
                            color = TikTokDesign.White,
                            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp).semantics { heading() },
                        )
                    }
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (skeleton) NextCircularProgress()
                        Text(
                            subtitle,
                            style = NextType.body,
                            color = ReaderDarkColors.inkMuted,
                            textAlign = TextAlign.Center,
                        )
                        if (actionLabel != null) InlineAction(actionLabel, accent = true, onClick = onAction)
                    }
                }
            }
        }
        return
    }
    NextScaffold(
        where = where.takeIf { !destination.isReaderRoot() },
        modifier = modifier,
        destination = destination,
        onDestination = onDestination,
    ) { _ ->
        Column(modifier = Modifier.fillMaxSize().contentInsets()) {
            if (LocalReaderFramePresent.current) {
                Text(
                    subtitle,
                    style =
                        com.orbin.uinext.tokens.ReaderType.stateBody
                            .withReaderFont(),
                    color = reader.inkMuted,
                    modifier = Modifier.padding(com.orbin.uinext.tokens.ReaderSpace.gutter),
                )
            } else {
                ScreenTitle(text = title, subtitle = subtitle)
            }
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
