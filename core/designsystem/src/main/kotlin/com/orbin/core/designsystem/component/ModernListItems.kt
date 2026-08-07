package com.orbin.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A fixed-size slot for a leading/trailing icon or control in a list row. Centralizes the sizing
 * shared by the `Modern*ListItem` family so their icon slots stay in lockstep instead of drifting
 * independently.
 */
@Composable
private fun IconSlot(
    size: Dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        content()
    }
}

private val ListItemIconSlotSize = 40.dp
private val CompactListItemIconSlotSize = 36.dp
private val CardListItemLeadingSlotSize = 56.dp

/**
 * Modern list item with title, subtitle, and optional leading/trailing icons.
 * Flexible component for list content with Material Design 3 styling.
 */
@Composable
fun ModernListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(backgroundColor)
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) {
            IconSlot(size = ListItemIconSlotSize, content = leading)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (trailing != null) {
            IconSlot(size = ListItemIconSlotSize, content = trailing)
        }
    }
}

/**
 * Modern small list item with compact design.
 * Used for dense lists where space is limited.
 */
@Composable
fun ModernCompactListItem(
    title: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, modifier = Modifier.size(20.dp))
        }

        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )

        if (trailing != null) {
            IconSlot(size = CompactListItemIconSlotSize, content = trailing)
        }
    }
}

/**
 * Modern card-based list item for prominent content.
 * Combines card elevation with list item layout for enhanced visual hierarchy.
 */
@Composable
fun ModernCardListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    description: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    // ModernCard already wires click handling (and its ripple) through Card(onClick = ...) when
    // onClick is non-null, so no clickable modifier is attached here — doing so would double up
    // the touch target and gesture handling.
    ModernCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leading != null) {
                IconSlot(size = CardListItemLeadingSlotSize, content = leading)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }

            if (trailing != null) {
                IconSlot(size = ListItemIconSlotSize, content = trailing)
            }
        }
    }
}

/**
 * Modern grouped list item header for section separation.
 * Used to create visual grouping in lists.
 */
@Composable
fun ModernListItemHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        title,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * Modern divider with label option for visual grouping.
 * Separates list sections with optional label.
 */
@Composable
fun ModernDivider(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    if (label != null) {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            HorizontalDivider(modifier = Modifier.weight(1f))
        }
    } else {
        HorizontalDivider(
            modifier = modifier,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}
