package com.orbin.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_nav_boards
import com.orbin.uinext.resources.reader_nav_feed
import com.orbin.uinext.resources.reader_nav_settings
import com.orbin.uinext.tokens.NextType
import com.orbin.uinext.tokens.OrbinElevation
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.ReaderType
import org.jetbrains.compose.resources.stringResource

/**
 * Shared Orbin Coral chrome: the coral ring mark, wordmark, and softly elevated cards.
 *
 * Screens opt into these primitives as they are restyled. They do not, by themselves, replace
 * the Clover-derived reader cells.
 */
object OrbinStyle {
    const val WORDMARK = "Orbin"
}

/** The coral ring, tinted with the current accent by default. */
@Composable
fun OrbinLogo(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = reader.accent,
    contentDescription: String? = OrbinStyle.WORDMARK,
) {
    Icon(
        imageVector = ReaderIcons.Logo,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/**
 * Logo plus the "Orbin" wordmark, as used on the Boards home header and the media viewer.
 *
 * [onInk] is the wordmark color; the ring always uses [tint] so it stays coral on both
 * light chrome and the dark media viewer.
 */
@Composable
fun OrbinWordmark(
    modifier: Modifier = Modifier,
    logoSize: Dp = 28.dp,
    tint: Color = reader.accent,
    onInk: Color = reader.ink,
    contentDescription: String = OrbinStyle.WORDMARK,
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OrbinLogo(size = logoSize, tint = tint, contentDescription = null)
        Text(
            text = OrbinStyle.WORDMARK,
            style = ReaderType.appBarTitle.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
            color = onInk,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * A white (or dark-elevated) card with the Coral corner radius and a soft shadow.
 *
 * On light themes the lift is a 2dp drop shadow over the page ground, with a white card
 * fill. On dark and AMOLED the fill is [reader.surfaceVariant] (lifted against the page
 * ground); the drop shadow is omitted so the card does not halo.
 */
@Composable
fun OrbinCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    shape: Shape = RoundedCornerShape(OrbinRadius.card),
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shadow = if (next.dark) 0.dp else OrbinElevation.card
    val tonal = if (next.dark) OrbinElevation.card else 0.dp
    // next.elevated equals the page ground, so dark/AMOLED cards would vanish.
    // Use the lifted surfaceVariant fill against the darker page background.
    val color = containerColor ?: if (next.dark) reader.surfaceVariant else reader.background
    Surface(
        modifier =
            if (onClick != null) {
                modifier.nextClickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            } else {
                modifier
            },
        shape = shape,
        color = color,
        shadowElevation = shadow,
        tonalElevation = tonal,
        content = { Column(content = content) },
    )
}

/**
 * Coral bottom navigation for Boards, Feed, and Settings.
 *
 * Catalog is omitted until that destination is wired. Watched, Saved, and Search stay
 * reachable from the drawer opened by the header menu. This is not a posting or composer
 * entry point.
 */
@Composable
fun OrbinBottomBar(
    selected: NextDestination,
    onSelect: (NextDestination) -> Unit,
    modifier: Modifier = Modifier,
    destinations: List<NextDestination> =
        listOf(NextDestination.BOARDS, NextDestination.FEED, NextDestination.SETTINGS),
) {
    val shadow = if (next.dark) 0.dp else OrbinElevation.bar
    val tonal = if (next.dark) OrbinElevation.bar else 0.dp
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = reader.background,
        shadowElevation = shadow,
        tonalElevation = tonal,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ).heightIn(min = MIN_TOUCH_TARGET)
                    .padding(top = 6.dp, bottom = 4.dp)
                    .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                val isSelected = destination == selected
                val label = orbinBottomLabel(destination)
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = MIN_TOUCH_TARGET)
                            .selectable(
                                selected = isSelected,
                                role = Role.Tab,
                                onClick = { if (!isSelected) onSelect(destination) },
                            ).semantics { contentDescription = label },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = orbinBottomIcon(destination, isSelected),
                        contentDescription = null,
                        tint = if (isSelected) reader.accent else next.muted,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        label,
                        style = NextType.caption1,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) reader.accent else next.muted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun orbinBottomLabel(destination: NextDestination): String =
    when (destination) {
        NextDestination.BOARDS -> stringResource(Res.string.reader_nav_boards)
        NextDestination.FEED -> stringResource(Res.string.reader_nav_feed)
        NextDestination.SETTINGS -> stringResource(Res.string.reader_nav_settings)
        NextDestination.WATCHED,
        NextDestination.SAVED,
        NextDestination.SEARCH,
        NextDestination.DOWNLOADS,
        -> destination.name.lowercase().replaceFirstChar { it.titlecase() }
    }

@Composable
private fun orbinBottomIcon(
    destination: NextDestination,
    selected: Boolean,
): ImageVector =
    when (destination) {
        NextDestination.BOARDS -> if (selected) ReaderIcons.BoardsFilled else ReaderIcons.Boards
        NextDestination.FEED -> if (selected) ReaderIcons.HomeFilled else ReaderIcons.Home
        NextDestination.SETTINGS -> ReaderIcons.Settings
        NextDestination.WATCHED -> ReaderIcons.Watched
        NextDestination.SAVED -> ReaderIcons.Saved
        NextDestination.SEARCH -> ReaderIcons.Search
        NextDestination.DOWNLOADS -> ReaderIcons.Settings
    }
