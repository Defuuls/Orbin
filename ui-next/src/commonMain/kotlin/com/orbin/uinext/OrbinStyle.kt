package com.orbin.uinext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_downloads_active
import com.orbin.uinext.resources.reader_nav_boards
import com.orbin.uinext.resources.reader_nav_downloads
import com.orbin.uinext.resources.reader_nav_feed
import com.orbin.uinext.resources.reader_nav_settings
import com.orbin.uinext.tokens.OrbinElevation
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Shared Orbin Coral chrome: the offset-card mark, wordmark, and softly elevated cards.
 *
 * Screens opt into these primitives as they are restyled. They do not, by themselves, replace
 * the Clover-derived reader cells.
 */
object OrbinStyle {
    const val WORDMARK = "Orbin"
}

/**
 * The Orbin mark: two offset rounded cards on a coral tile, matching the
 * variant-B brand icon. A dark-coral back card sits behind a white front
 * card that holds a coral play triangle and, at 32dp and above, two thread
 * lines. Below 32dp the lines are dropped and the front card and triangle
 * are drawn about 10% larger so the mark still reads at wordmark size.
 *
 * Colors are the brand values (#FF5D68 tile and play, #B8323F back card,
 * white front), not the theme accent, so the mark is identical on light
 * and dark chrome.
 */
@Composable
fun OrbinLogo(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    contentDescription: String? = OrbinStyle.WORDMARK,
) {
    val compact = size < ORBIN_LOGO_COMPACT_BELOW
    val semanticsModifier =
        if (contentDescription != null) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        }
    Canvas(modifier = modifier.size(size).then(semanticsModifier)) {
        val canvas = this.size.minDimension
        val u = canvas / ORBIN_LOGO_VIEWPORT
        val tile =
            Path().apply {
                addRoundRect(
                    RoundRect(
                        0f,
                        0f,
                        canvas,
                        canvas,
                        CornerRadius(canvas * ORBIN_LOGO_TILE_RADIUS),
                    ),
                )
            }
        clipPath(tile) {
            drawPath(tile, OrbinLogoTile)
            drawRoundRect(
                color = OrbinLogoBack,
                topLeft = Offset(334f * u, 140f * u),
                size = Size(500f * u, 600f * u),
                cornerRadius = CornerRadius(80f * u),
            )
            val frontLeft: Offset
            val frontSize: Size
            val frontRadius: Float
            val p1: Offset
            val p2: Offset
            val p3: Offset
            if (compact) {
                val cx = 440f
                val cy = 600f
                val scale = ORBIN_LOGO_COMPACT_SCALE

                fun scaled(
                    x: Float,
                    y: Float,
                ): Offset = Offset((cx + (x - cx) * scale) * u, (cy + (y - cy) * scale) * u)
                val frontW = 500f * scale
                val frontH = 600f * scale
                frontLeft = Offset((cx - frontW / 2f) * u, (cy - frontH / 2f) * u)
                frontSize = Size(frontW * u, frontH * u)
                frontRadius = 80f * scale * u
                p1 = scaled(370.2f, 613f)
                p2 = scaled(370.2f, 803f)
                p3 = scaled(545f, 708f)
            } else {
                frontLeft = Offset(190f * u, 300f * u)
                frontSize = Size(500f * u, 600f * u)
                frontRadius = 80f * u
                p1 = Offset(370.2f * u, 613f * u)
                p2 = Offset(370.2f * u, 803f * u)
                p3 = Offset(545f * u, 708f * u)
            }
            drawRoundRect(
                color = OrbinLogoFront,
                topLeft = frontLeft,
                size = frontSize,
                cornerRadius = CornerRadius(frontRadius),
            )
            if (!compact) {
                val line = OrbinLogoTile.copy(alpha = 0.9f)
                drawRoundRect(
                    color = line,
                    topLeft = Offset(260f * u, 370f * u),
                    size = Size(300f * u, 34f * u),
                    cornerRadius = CornerRadius(17f * u),
                )
                drawRoundRect(
                    color = line,
                    topLeft = Offset(260f * u, 434f * u),
                    size = Size(220f * u, 34f * u),
                    cornerRadius = CornerRadius(17f * u),
                )
            }
            val play =
                Path().apply {
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    close()
                }
            drawPath(play, OrbinLogoTile)
        }
    }
}

/**
 * Logo plus the "Orbin" wordmark, as used on the Boards home header and the media viewer.
 *
 * [onInk] is the wordmark color; the mark uses the brand coral tile so it stays
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
            style = OrbinType.appBarTitle.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
            color = onInk,
            // The parent supplies the label; UIKit otherwise announces both as "Orbin, Orbin".
            modifier = Modifier.clearAndSetSemantics {},
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
    border: BorderStroke? = null,
    elevated: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Shadows and tonal elevation go through graphicsLayer; callers that scroll many
    // cards (catalog grid) pass elevated = false and use a flat fill or border instead.
    val shadow = if (elevated && !next.dark) OrbinElevation.card else 0.dp
    val tonal = if (elevated && next.dark) OrbinElevation.card else 0.dp
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
        border = border,
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
        listOf(
            NextDestination.BOARDS,
            NextDestination.FEED,
            NextDestination.DOWNLOADS,
            NextDestination.SETTINGS,
        ),
    downloadBadge: Int = 0,
) {
    val activeDownloads =
        if (downloadBadge > 0) {
            pluralStringResource(Res.plurals.reader_downloads_active, downloadBadge, downloadBadge)
        } else {
            null
        }
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
                            ).semantics {
                                contentDescription = label
                                if (destination == NextDestination.DOWNLOADS && activeDownloads != null) {
                                    stateDescription = activeDownloads
                                }
                            },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box {
                        Icon(
                            imageVector = orbinBottomIcon(destination, isSelected),
                            contentDescription = null,
                            tint = if (isSelected) reader.accent else next.muted,
                            modifier = Modifier.size(24.dp),
                        )
                        if (destination == NextDestination.DOWNLOADS && downloadBadge > 0) {
                            val badgeLabel = if (downloadBadge > 99) "99+" else downloadBadge.toString()
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 8.dp, y = (-4).dp)
                                    .heightIn(min = 16.dp)
                                    .background(reader.accent, CircleShape)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    badgeLabel,
                                    style = OrbinType.caption2.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    color = reader.onAccent,
                                    maxLines = 1,
                                    modifier = Modifier.clearAndSetSemantics {},
                                )
                            }
                        }
                    }
                    Text(
                        label,
                        modifier = Modifier.clearAndSetSemantics {},
                        style = OrbinType.caption1,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) reader.accent else next.muted,
                        textAlign = TextAlign.Center,
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
        NextDestination.DOWNLOADS -> stringResource(Res.string.reader_nav_downloads)
        NextDestination.WATCHED,
        NextDestination.SAVED,
        NextDestination.SEARCH,
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
        NextDestination.DOWNLOADS -> ReaderIcons.Download
    }

private val OrbinLogoTile = Color(0xFFFF5D68)
private val OrbinLogoBack = Color(0xFFB8323F)
private val OrbinLogoFront = Color.White
private const val ORBIN_LOGO_VIEWPORT = 1024f
private const val ORBIN_LOGO_TILE_RADIUS = 0.22f
private const val ORBIN_LOGO_COMPACT_SCALE = 1.1f
private val ORBIN_LOGO_COMPACT_BELOW = 32.dp
