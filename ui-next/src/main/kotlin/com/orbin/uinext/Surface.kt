package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The one piece of permanent chrome in the new interface.
 *
 * The current app carries a top bar and a bottom navigation bar on every screen, plus overflow
 * menus hanging off the top bar. That is two horizontal bands of the display spent on furniture
 * before a single post is drawn, and it is the same furniture whichever of the twenty-one
 * destinations you are on.
 *
 * This replaces both. One line, forty-four density-independent pixels tall, pinned to the bottom
 * edge where a thumb already rests: where you are on the left, and the only affordance that is
 * always available on the right — the command surface, which is how you get anywhere else. There
 * is no back button because layers are dismissed by dragging them down, and no tab bar because
 * there are no longer tabs.
 */
@Composable
fun ContextRail(
    where: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Hairline()
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(RAIL_HEIGHT)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = GUTTER),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = where,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (detail != null) {
                Text(
                    text = "  $detail",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
                    maxLines = 1,
                )
            }
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "⌘",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
            )
        }
    }
}

/**
 * A screen title, set in the content rather than in a bar above it.
 *
 * Titles scroll away with the content because a title is information, not furniture: it tells you
 * what you opened, and once you are reading you no longer need to be told. The current app keeps
 * its title pinned in a bar for the life of the screen.
 */
@Composable
fun ScreenTitle(
    text: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = GUTTER, vertical = 20.dp)) {
        Text(
            text = text,
            fontSize = 30.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * The only separator in the interface.
 *
 * No cards, no elevation, no filled containers. A list is a list because of the space around its
 * rows and a one-pixel line between them, which is enough — and it costs nothing in vertical
 * space, where cards cost padding twice per row plus the gap between them.
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
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = HAIRLINE)),
    )
}

/** A row's secondary line: board, counts, timestamps. One line, muted, never wrapping. */
@Composable
fun MetaLine(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * An action, rendered as a word rather than as an icon in a bar.
 *
 * Icons in a top bar are ambiguous and permanent; words are unambiguous and can sit inline with
 * the thing they act on, appearing only where they apply.
 */
@Composable
fun InlineAction(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (accent) FontWeight.Medium else FontWeight.Normal,
        color =
            if (accent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED)
            },
        modifier = modifier,
    )
}

/** A square media tile placeholder, flat and untinted, used where a thumbnail will load. */
@Composable
fun MediaTile(
    modifier: Modifier = Modifier,
    tone: Float = 0.10f,
    badge: String? = null,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.onBackground.copy(alpha = tone)),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
                modifier = Modifier.padding(6.dp),
            )
        }
    }
}

@Composable
internal fun IconGlyph(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(18.dp))
}

/** Vertical rhythm and the two opacities the whole interface is built from. */
val GUTTER = 20.dp
val RAIL_HEIGHT = 44.dp
const val MUTED = 0.55f
const val HAIRLINE = 0.10f

@Composable
internal fun Gap(height: Int) {
    Box(modifier = Modifier.height(height.dp))
}

@Composable
internal fun Spread(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
internal fun Surface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) { content() }
}

@Composable
internal fun WidthSpacer(width: Int) {
    Box(modifier = Modifier.width(width.dp))
}
