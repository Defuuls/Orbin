package com.orbin.core.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Modern Material Design 3 card with elevated shadow and subtle gradient effect.
 * Used for content surfaces with visual hierarchy.
 */
@Composable
fun ModernCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    shape: androidx.compose.material3.Shapes = MaterialTheme.shapes,
    elevation: androidx.compose.material3.CardElevation = CardDefaults.elevatedCardElevation(
        defaultElevation = 4.dp,
        pressedElevation = 8.dp,
        focusedElevation = 4.dp,
        hoveredElevation = 8.dp,
    ),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
            shape = shape.large,
            elevation = elevation,
            onClick = onClick,
            content = content,
        )
    } else {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
            shape = shape.large,
            elevation = elevation,
            content = content,
        )
    }
}

/**
 * Modern filled button with Material Design 3 styling.
 * Primary action button with emphasis.
 */
@Composable
fun ModernButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = androidx.compose.material3.ButtonDefaults.ContentPadding,
        shape = MaterialTheme.shapes.medium,
        content = {
            if (leadingIcon != null) {
                leadingIcon()
            }
            androidx.compose.material3.Text(label)
        },
    )
}

/**
 * Modern tonal button variant - lower emphasis than filled button.
 * Used for secondary actions.
 */
@Composable
fun ModernTonalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        content = { androidx.compose.material3.Text(label) },
    )
}

/**
 * Modern outlined button - for tertiary actions.
 * Emphasizes action without strong visual weight.
 */
@Composable
fun ModernOutlinedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        content = { androidx.compose.material3.Text(label) },
    )
}
