package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The one piece of permanent chrome in the new interface.
 *
 * The current app carries a top bar and a bottom navigation bar on every screen, plus overflow menus
 * hanging off the top bar. That is two horizontal bands of the display spent on furniture before a
 * single post is drawn, and it is the same furniture whichever of the twenty-one destinations you
 * are on.
 *
 * This replaces both: one floating bar, inset from the edges, over content that scrolls beneath it.
 * Where you are on the left; on the right the only affordance that is always available. There is no
 * back button because layers are dismissed by dragging them down, and no tab bar because there are
 * no longer tabs.
 *
 * [action] names that one affordance. It is Search in the full client, because search is how you
 * get anywhere else there. Orbin Minimal has nowhere else to get to but its board list, so it says
 * Boards — one bar with one affordance either way, rather than a bar the smaller app cannot use.
 */
@Composable
fun ContextRail(
    where: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: String = "Search",
    onSearch: () -> Unit = {},
) {
    val railInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        // Content dissolves into the background under the bar instead of running into it. The scrim
        // runs to the bottom edge, behind the navigation bar, so there is no seam where it stops.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(RAIL_HEIGHT + 58.dp + bottomInset())
                    .background(
                        Brush.verticalGradient(
                            0f to next.background.copy(alpha = 0f),
                            0.45f to next.background.copy(alpha = 0.92f),
                            1f to next.background,
                        ),
                    ),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // The bar floats above the navigation bar rather than under it; the scrim
                    // behind it is what covers that strip.
                    .windowInsetsPadding(railInsets)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .height(RAIL_HEIGHT)
                    .clip(RoundedCornerShape(RAIL_HEIGHT / 2))
                    .background(next.raised)
                    .border(1.dp, next.hairline, RoundedCornerShape(RAIL_HEIGHT / 2))
                    .padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = where,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp,
                color = next.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (detail != null) {
                Text(
                    text = "  $detail",
                    fontSize = 13.sp,
                    color = next.muted,
                    maxLines = 1,
                )
            }
            Box(modifier = Modifier.weight(1f))
            Box(
                modifier =
                    Modifier
                        .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button, onClick = onSearch),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = action,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = next.accent,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(next.accentSoft)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * A screen title, set in the content rather than in a bar above it.
 *
 * Titles scroll away with the content because a title is information, not furniture: it tells you
 * what you opened, and once you are reading you no longer need to be told. The current app keeps its
 * title pinned in a bar for the life of the screen.
 *
 * Set large, heavy and tightly tracked — negative letter spacing at display sizes is most of the
 * difference between a heading that looks drawn and one that looks defaulted.
 */
@Composable
fun ScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    size: Int = 32,
) {
    Column(modifier = modifier.padding(start = GUTTER, end = GUTTER, top = 26.dp, bottom = 18.dp)) {
        Text(
            text = text,
            fontSize = size.sp,
            lineHeight = (size + 5).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.9).sp,
            color = next.ink,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = next.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * The only separator in the interface.
 *
 * No cards, no elevation, no filled containers. A list is a list because of the space around its
 * rows and a one-pixel line between them, which is enough — and it costs nothing in vertical space,
 * where cards cost padding twice per row plus the gap between them.
 */
@Composable
fun Hairline(
    modifier: Modifier = Modifier,
    inset: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = if (inset) GUTTER else 0.dp)
                .height(1.dp)
                .background(next.hairline),
    )
}

/** A row's secondary line: counts and timestamps. One line, muted, never wrapping. */
@Composable
fun MetaLine(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    Text(
        text = text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
        color = color ?: next.muted,
        maxLines = 1,
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
 * An action, rendered as a word rather than as an icon in a bar.
 *
 * Icons in a top bar are ambiguous and permanent; words are unambiguous and can sit inline with the
 * thing they act on, appearing only where they apply. The active one is filled so the set reads as a
 * choice rather than a row of links.
 */
@Composable
fun InlineAction(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(15.dp)
    val text =
        @Composable {
            Text(
                text = label,
                fontSize = 13.5.sp,
                fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Medium,
                color = if (accent) next.accent else next.muted,
                modifier =
                    Modifier
                        .clip(shape)
                        .background(if (accent) next.accentSoft else Color.Transparent)
                        .padding(horizontal = if (accent) 13.dp else 4.dp, vertical = 7.dp),
            )
        }
    if (onClick == null) {
        Box(modifier = modifier) { text() }
        return
    }
    // The pill keeps its own small size; the box around it is what gets pressed. Set as words
    // rather than as a Material button, an action still has to be a button to a screen reader and
    // still has to be big enough to hit — neither of which a clickable Text gives you.
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
                .clip(shape)
                .clickable(role = Role.Button, onClick = onClick),
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
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.3.sp,
        color = hue,
        // Board ids have no length limit, and an unbounded pill runs off the tile it sits on.
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .background(hue.copy(alpha = if (next.dark) 0.16f else 0.11f))
                .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** Where a thumbnail will load: rounded, and filled with stand-in artwork rather than flat grey. */
@Composable
fun MediaTile(
    modifier: Modifier = Modifier,
    seed: Int = 0,
    badge: String? = null,
    radius: Dp = 12.dp,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(radius))
                .background(placeholderArt(seed)),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (badge != null) {
            Pill(text = badge, tint = boardHue(badge), modifier = Modifier.padding(7.dp))
        }
    }
}

/** Vertical rhythm and the height of the one bar. */
val GUTTER = 20.dp
val RAIL_HEIGHT = 52.dp

/** The smallest thing a finger should have to hit. */
val MIN_TOUCH_TARGET = 48.dp

/**
 * The status bar and the side of a display cutout, kept off the content.
 *
 * A screen with a top bar and a bottom navigation bar gets this for free: the bars sit in the inset
 * strips and the content starts below them. This interface deleted both bars, and nothing took over
 * the job they were doing — so the first row of every screen was drawn underneath the clock and the
 * rail underneath the gesture handle. The background still runs to the edges; only the content is
 * pushed clear.
 */
@Composable
internal fun Modifier.contentInsets(): Modifier =
    windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    )

/**
 * How much taller a scrolling list's bottom padding has to be so its last row clears the navigation
 * bar as well as the rail. The list itself still runs to the bottom edge — content scrolls under
 * both, which is the point of drawing edge to edge.
 */
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
 * What the interface shows when there is nothing to show: no spinner in the middle of an empty
 * screen, no red error card. The same title-and-subtitle the loaded screen uses, saying what is
 * happening, with one action when there is one worth offering.
 */
@Composable
fun MessageScreen(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    where: String? = null,
    action: String = "Search",
    onSearch: () -> Unit = {},
) {
    Surface {
        Box(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().contentInsets()) {
                ScreenTitle(text = title, subtitle = subtitle)
                if (actionLabel != null) {
                    Box(modifier = Modifier.padding(horizontal = GUTTER - 4.dp)) {
                        InlineAction(label = actionLabel, accent = true, onClick = onAction)
                    }
                }
            }
            if (where != null) {
                ContextRail(
                    where = where,
                    action = action,
                    onSearch = onSearch,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
