package com.orbin.uinext

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.NextMaterials
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType

/** Destinations understood by the app. Only Feed and Settings live in permanent bottom chrome. */
enum class NextDestination {
    FEED,
    BOARDS,
    MEDIA,
    SETTINGS,
}

/** Minimal permanent chrome: Feed and Settings, with Command as the trailing action. */
@Composable
fun DestinationPill(
    selected: NextDestination,
    onSelect: (NextDestination) -> Unit,
    modifier: Modifier = Modifier,
    action: String = stringResource(R.string.next_action_search),
    onCommand: () -> Unit = {},
) {
    val railInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Box(
            modifier = Modifier.fillMaxWidth().height(RAIL_HEIGHT + 58.dp + bottomInset()).background(
                Brush.verticalGradient(0f to next.background.copy(alpha = 0f), 0.4f to next.background.copy(alpha = 0.88f), 1f to next.background),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(railInsets).padding(horizontal = NextSpace.chromeInset, vertical = NextSpace.chromeBottom),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f).heightIn(min = RAIL_HEIGHT).nextFrosted(RoundedCornerShape(NextRadius.pill)).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DestinationTab(stringResource(R.string.next_feed_title), Icons.Outlined.Home, selected == NextDestination.FEED) { onSelect(NextDestination.FEED) }
                DestinationTab(stringResource(R.string.next_settings_title), Icons.Outlined.Settings, selected == NextDestination.SETTINGS) { onSelect(NextDestination.SETTINGS) }
            }
            Box(
                modifier = Modifier.sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET).clip(RoundedCornerShape(NextRadius.pill)).nextClickable(role = Role.Button, onClick = onCommand),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = action,
                    style = NextType.tab,
                    color = next.onAccent,
                    modifier = Modifier.clip(RoundedCornerShape(NextRadius.pill)).background(next.accent).padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun RowScope.DestinationTab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val fill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "tabFill",
    )
    val selectedFill = next.accent.copy(alpha = (if (next.dark) NextMaterials.SELECTED_FILL_DARK else NextMaterials.SELECTED_FILL_LIGHT) * fill)
    val tint = if (selected) next.accent else next.muted
    Box(
        modifier = Modifier.weight(1f).sizeIn(minHeight = MIN_TOUCH_TARGET).clip(RoundedCornerShape(NextRadius.pill)).background(selectedFill)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick).semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(text = label, style = NextType.caption2, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ContextRail(
    where: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: String = stringResource(R.string.next_action_search),
    onSearch: () -> Unit = {},
) {
    val railInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Box(modifier = Modifier.fillMaxWidth().height(RAIL_HEIGHT + 58.dp + bottomInset()).background(Brush.verticalGradient(0f to next.background.copy(alpha = 0f), 0.45f to next.background.copy(alpha = 0.92f), 1f to next.background)))
        Row(
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(railInsets).padding(horizontal = NextSpace.chromeInset, vertical = NextSpace.chromeBottom).heightIn(min = RAIL_HEIGHT).nextFrosted(RoundedCornerShape(NextRadius.pill)).padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = where, style = NextType.tab, color = next.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (detail != null) Text(text = "  $detail", style = NextType.footnote, color = next.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.45f, fill = false))
            Box(
                modifier = Modifier.sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET).clip(RoundedCornerShape(NextRadius.control)).nextClickable(role = Role.Button, onClick = onSearch),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = action, style = NextType.tab, color = next.onAccent, modifier = Modifier.clip(RoundedCornerShape(NextRadius.control)).background(next.accent).padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
    }
}

val COMPACT_TITLE_CLEARANCE = 52.dp
object NextTitleTags { const val LARGE = "next_large_title"; const val COMPACT = "next_compact_title" }

@Composable
fun CompactTitleBar(title: String, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 2 } + fadeIn(),
        exit = slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 2 } + fadeOut(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(horizontal = 12.dp, vertical = 6.dp).nextFrosted(RoundedCornerShape(NextRadius.control)).padding(horizontal = 14.dp, vertical = 10.dp).testTag(NextTitleTags.COMPACT),
            contentAlignment = Alignment.Center,
        ) { Text(text = title, style = NextType.headline, color = next.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null, size: Int = 34) {
    val titleStyle = when { size >= 32 -> NextType.largeTitle; size >= 26 -> NextType.title1; else -> NextType.title2 }
    Column(modifier = modifier.padding(start = NextSpace.gutter, end = NextSpace.gutter, top = NextSpace.titleTop + 14.dp, bottom = NextSpace.titleBottom)) {
        Text(text = text, style = titleStyle, color = next.ink, modifier = Modifier.testTag(NextTitleTags.LARGE))
        if (subtitle != null) Text(text = subtitle, style = NextType.footnote, color = next.muted, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier, inset: Boolean = false) {
    Box(modifier = modifier.fillMaxWidth().padding(start = if (inset) NextSpace.gutter else 0.dp).height(0.5.dp).background(next.hairline))
}

@Composable
fun GroupedSection(modifier: Modifier = Modifier, header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = NextSpace.gutterTight)) {
        if (header != null) Text(text = header.uppercase(), style = NextType.sectionHeader, color = next.muted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp, top = 4.dp))
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(NextRadius.card)).background(next.raised), content = content)
        if (footer != null) Text(text = footer, style = NextType.caption1, color = next.faint, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
    }
}

@Composable
fun GroupedDivider(modifier: Modifier = Modifier) { Box(modifier = modifier.fillMaxWidth().padding(start = NextSpace.rowX).height(0.5.dp).background(next.hairline)) }

@Composable
fun MetaLine(text: String, modifier: Modifier = Modifier, color: Color? = null, maxLines: Int = 1) {
    Text(text = text, style = NextType.footnote, color = color ?: next.muted, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable
fun BoardDot(board: String, modifier: Modifier = Modifier, size: Dp = 6.dp) {
    Box(modifier = modifier.size(size).clip(RoundedCornerShape(size / 2)).background(boardHue(board)))
}

@Composable
fun InlineAction(label: String, modifier: Modifier = Modifier, accent: Boolean = false, selected: Boolean? = null, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(NextRadius.control)
    val filled = selected ?: accent
    val interaction = remember { MutableInteractionSource() }
    val text = @Composable {
        Text(text = label, style = NextType.footnote, fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Medium, color = if (filled) next.onAccent else next.muted,
            modifier = Modifier.clip(shape).background(if (filled) next.accent else Color.Transparent).padding(horizontal = if (filled) 13.dp else 4.dp, vertical = 7.dp))
    }
    if (onClick == null) { Box(modifier = modifier) { text() }; return }
    val press = if (selected == null) Modifier.clickable(role = Role.Button, indication = NextHighlightIndication, interactionSource = interaction, onClick = onClick)
        else Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    Box(modifier = modifier.sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET).clip(shape).then(press), contentAlignment = Alignment.Center) { text() }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, tint: Color? = null) {
    val hue = tint ?: next.accent
    Text(text = text, style = NextType.caption2, color = hue, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(RoundedCornerShape(NextRadius.tight)).background(hue.copy(alpha = if (next.dark) 0.18f else 0.12f)).padding(horizontal = 7.dp, vertical = 3.dp))
}

@Composable
fun MediaTile(modifier: Modifier = Modifier, seed: Int = 0, badge: String? = null, radius: Dp = NextRadius.tile) {
    Box(modifier = modifier.clip(RoundedCornerShape(radius)).background(placeholderArt(seed)), contentAlignment = Alignment.BottomStart) {
        if (badge != null) Pill(text = badge, tint = boardHue(badge), modifier = Modifier.padding(7.dp))
    }
}

@Composable
fun NextScaffold(
    where: String?,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: String = stringResource(R.string.next_action_search),
    onSearch: () -> Unit = {},
    railVisible: Boolean = true,
    destination: NextDestination? = null,
    onDestination: ((NextDestination) -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val showDestination = destination == NextDestination.FEED || destination == NextDestination.SETTINGS
    val hasDestination = showDestination && onDestination != null
    val showRail = where != null && !hasDestination
    val bottom = (if (hasDestination || showRail) RAIL_HEIGHT + RAIL_CLEARANCE else NO_RAIL_CLEARANCE) + bottomInset()
    Surface {
        Box(modifier = modifier.fillMaxSize()) {
            content(PaddingValues(bottom = bottom))
            if (hasDestination || showRail) {
                AnimatedVisibility(
                    visible = railVisible,
                    enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
                    exit = slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    val selectDestination = onDestination
                    if (showDestination && destination != null && selectDestination != null) {
                        DestinationPill(selected = destination, onSelect = selectDestination, action = action, onCommand = onSearch)
                    } else if (where != null) {
                        ContextRail(where = where, detail = detail, action = action, onSearch = onSearch)
                    }
                }
            }
        }
    }
}

val GUTTER = NextSpace.gutter
val RAIL_HEIGHT = 56.dp
private val RAIL_CLEARANCE = 28.dp
private val NO_RAIL_CLEARANCE = 16.dp
val MIN_TOUCH_TARGET = 48.dp

@Composable
internal fun Modifier.contentInsets(): Modifier = windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
@Composable
internal fun bottomInset(): Dp = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
@Composable
internal fun Gap(height: Int) { Box(modifier = Modifier.height(height.dp)) }
@Composable
internal fun Surface(content: @Composable () -> Unit) { Box(modifier = Modifier.fillMaxSize().background(next.background)) { content() } }
@Composable
internal fun WidthSpacer(width: Int) { Box(modifier = Modifier.width(width.dp)) }

@Composable
fun MessageScreen(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    where: String? = null,
    action: String = stringResource(R.string.next_action_search),
    onSearch: () -> Unit = {},
    destination: NextDestination? = null,
    onDestination: ((NextDestination) -> Unit)? = null,
) {
    NextScaffold(where = where.takeIf { destination == null }, modifier = modifier, action = action, onSearch = onSearch, destination = destination, onDestination = onDestination) { _ ->
        Column(modifier = Modifier.fillMaxSize().contentInsets()) {
            ScreenTitle(text = title, subtitle = subtitle)
            if (actionLabel != null) Box(modifier = Modifier.padding(horizontal = GUTTER - 4.dp)) { InlineAction(label = actionLabel, accent = true, onClick = onAction) }
        }
    }
}