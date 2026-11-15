package com.orbin.uinext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_downloads_active
import com.orbin.uinext.resources.reader_library_new_replies
import com.orbin.uinext.tokens.OrbinElevation
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.accentSecondary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Shared Orbin identity on NekoSurf-style rounded surfaces and floating navigation.
 *
 * The Orbin mark and destination contracts remain the same across the shared presentation.
 */
object OrbinStyle {
    const val WORDMARK = "Orbin"
}

/** A hidden saved-media tab remains reachable through the existing drawer and Settings actions. */
val LocalOrbinSavedMediaTabVisible = staticCompositionLocalOf { true }

/**
 * The Orbin Midnight Electric mark (`>>` quote chevrons + interlocking play spark),
 * delegating to [OrbinLogoMark] with optional accessibility semantics.
 */
@Composable
fun OrbinLogo(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    contentDescription: String? = OrbinStyle.WORDMARK,
) {
    val semanticsModifier =
        if (contentDescription != null) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        }
    OrbinLogoMark(modifier = modifier.size(size).then(semanticsModifier))
}

/**
 * Logo plus the "Orbin" wordmark, as used on the Boards home header and the media viewer.
 *
 * [onInk] is the wordmark color; the mark keeps its Orbin brand tile so it stays
 * identical on light chrome and the dark media viewer.
 */
@Composable
fun OrbinWordmark(
    modifier: Modifier = Modifier,
    logoSize: Dp = 28.dp,
    onInk: Color = reader.ink,
    contentDescription: String = OrbinStyle.WORDMARK,
) {
    Row(
        modifier =
            modifier.semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                heading()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OrbinLogo(size = logoSize, contentDescription = null)
        Text(
            text = OrbinStyle.WORDMARK,
            style = OrbinType.appBarTitle.withReaderFont(),
            color = onInk,
            // The parent supplies the label; UIKit otherwise announces both as "Orbin, Orbin".
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * A softly rounded solid card, outlined on dark pages and lightly lifted on light ones.
 */
@Composable
fun OrbinCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    shape: Shape = RoundedCornerShape(OrbinRadius.card),
    containerColor: Color? = null,
    border: BorderStroke? = null,
    elevated: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Shadows and tonal elevation go through graphicsLayer; callers that scroll many
    // cards (catalog grid) pass elevated = false and use a flat fill or border instead.
    val shadow = if (elevated && !next.dark) OrbinElevation.card else 0.dp
    val color = containerColor ?: reader.surfaceVariant
    val cardBorder = border ?: BorderStroke(1.dp, reader.hairline)
    Surface(
        modifier =
            if (onClick != null) {
                modifier.nextClickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            } else {
                modifier
            },
        shape = shape,
        color = color,
        border = cardBorder,
        shadowElevation = shadow,
        tonalElevation = 0.dp,
        content = { Column(content = content) },
    )
}

private val TAB_WIDTH = 76.dp
private val TAB_LABEL_INSET = 8.dp

@Composable
private fun tabLabelStyle() =
    OrbinType.caption1.copy(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)

/**
 * The phone's only primary navigation: Boards, Feed, Library and Settings from [ReaderNavigationSpec].
 *
 * When any label would not fit on one line (large text, narrow window) every tab goes icon-only
 * together; each keeps its spoken label and shows it as a tooltip on long press. Library carries
 * the new-replies count of watched threads. This is not a posting or composer entry point.
 */
@Composable
fun OrbinBottomBar(
    selected: NextDestination,
    onSelect: (NextDestination) -> Unit,
    modifier: Modifier = Modifier,
    destinations: List<NextDestination> = ReaderNavigationSpec.destinations(NavSurface.BAR, NavWindow.COMPACT),
    downloadBadge: Int = 0,
    watchedBadge: Int = 0,
) {
    val colors = reader
    val bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    val status = libraryStatus(downloadBadge, watchedBadge)
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                ).padding(horizontal = 16.dp)
                .padding(bottom = if (bottom > 0.dp) 0.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val barWidth = minOf(maxWidth, TAB_WIDTH * destinations.size + 12.dp)
        val tabWidth = (barWidth - 12.dp) / destinations.size.coerceAtLeast(1)
        val labels = destinations.map { orbinBottomLabel(it) }
        val iconOnly = !navigationLabelsFit(labels, tabWidth - TAB_LABEL_INSET)
        Surface(
            modifier = Modifier.width(barWidth),
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceVariant.copy(alpha = 0.82f),
            border = BorderStroke(1.dp, colors.hairline),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(6.dp)
                        .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { destination ->
                    OrbinNavigationTab(
                        destination = destination,
                        selected = destination == selected.primary,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        iconOnly = iconOnly,
                        badge = if (destination == NextDestination.LIBRARY) watchedBadge else 0,
                        status = if (destination == NextDestination.LIBRARY) status else null,
                    )
                }
            }
        }
    }
}

/** True when every label fits on one line in [width] at the current text size. */
@Composable
internal fun navigationLabelsFit(
    labels: List<String>,
    width: Dp,
): Boolean {
    val measurer = rememberTextMeasurer()
    val style = tabLabelStyle()
    val density = LocalDensity.current
    val limit = with(density) { width.roundToPx() }
    return labels.all { label ->
        val result = measurer.measure(label, style, maxLines = 1, softWrap = false)
        result.size.width <= limit
    }
}

@Composable
private fun libraryStatus(
    downloads: Int,
    watched: Int,
): String? =
    listOfNotNull(
        watched.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.reader_library_new_replies, it, it) },
        downloads.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.reader_downloads_active, it, it) },
    ).takeIf { it.isNotEmpty() }?.joinToString(", ")

/** The same four destinations in a floating rail, placed beside content by the host layout. */
@Composable
fun OrbinSideBar(
    selected: NextDestination,
    onSelect: (NextDestination) -> Unit,
    modifier: Modifier = Modifier,
    destinations: List<NextDestination> = ReaderNavigationSpec.destinations(NavSurface.RAIL, NavWindow.EXPANDED),
    downloadBadge: Int = 0,
    watchedBadge: Int = 0,
) {
    val colors = reader
    val direction = LocalLayoutDirection.current
    val left = WindowInsets.safeDrawing.asPaddingValues().calculateLeftPadding(direction)
    val status = libraryStatus(downloadBadge, watchedBadge)
    val iconOnly = !navigationLabelsFit(destinations.map { orbinBottomLabel(it) }, TAB_WIDTH - TAB_LABEL_INSET)
    Box(
        modifier =
            modifier
                .width(112.dp + left)
                .fillMaxHeight()
                .background(colors.background)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Left + WindowInsetsSides.Top + WindowInsetsSides.Bottom,
                    ),
                ).padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceVariant.copy(alpha = 0.82f),
            border = BorderStroke(1.dp, colors.hairline),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(6.dp).selectableGroup()) {
                destinations.forEach { destination ->
                    OrbinNavigationTab(
                        destination = destination,
                        selected = destination == selected.primary,
                        onSelect = onSelect,
                        modifier = Modifier.width(TAB_WIDTH).heightIn(min = 60.dp),
                        iconOnly = iconOnly,
                        badge = if (destination == NextDestination.LIBRARY) watchedBadge else 0,
                        status = if (destination == NextDestination.LIBRARY) status else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrbinNavigationTab(
    destination: NextDestination,
    selected: Boolean,
    onSelect: (NextDestination) -> Unit,
    modifier: Modifier,
    iconOnly: Boolean,
    badge: Int,
    status: String?,
) {
    val colors = reader
    val label = orbinBottomLabel(destination)
    val gradient =
        if (selected) {
            listOf(colors.accent.copy(alpha = 0.24f), accentSecondary(next.accentTheme).copy(alpha = 0.20f))
        } else {
            listOf(Color.Transparent, Color.Transparent)
        }
    Column(
        modifier =
            modifier
                .testTag(OrbinNavigationTags.ENTRY + destination.name)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(gradient))
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = { if (!selected) onSelect(destination) },
                ).semantics {
                    contentDescription = label
                    if (status != null) stateDescription = status
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(
                imageVector = orbinBottomIcon(destination, selected),
                contentDescription = null,
                tint = if (selected) colors.accent else colors.inkMuted,
                modifier = Modifier.size(21.dp),
            )
            if (badge > 0) {
                val badgeLabel = if (badge > 99) "99+" else badge.toString()
                Box(
                    Modifier
                        .testTag(OrbinNavigationTags.BADGE)
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-4).dp)
                        .heightIn(min = 16.dp)
                        .background(colors.accent, CircleShape)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        badgeLabel,
                        style = OrbinType.caption2.copy(fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold),
                        color = colors.onAccent,
                        maxLines = 1,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
        if (!iconOnly) {
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                modifier = Modifier.testTag(OrbinNavigationTags.LABEL).clearAndSetSemantics {},
                style = tabLabelStyle(),
                color = if (selected) colors.ink else colors.inkMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun orbinBottomLabel(destination: NextDestination): String =
    stringResource(ReaderNavigationSpec.entry(destination).label)

private fun orbinBottomIcon(
    destination: NextDestination,
    selected: Boolean,
): ImageVector = ReaderNavigationSpec.entry(destination).icon(selected)
