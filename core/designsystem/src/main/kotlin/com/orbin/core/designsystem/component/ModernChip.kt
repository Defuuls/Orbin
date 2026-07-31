package com.orbin.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Modern Material Design 3 filter chip for categorical selection.
 * Used in filters, tags, and selection UIs.
 */
@Composable
fun ModernFilterChip(
    label: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    FilterChip(
        selected = selected,
        onClick = { onSelectedChange(!selected) },
        label = { Text(label) },
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        leadingIcon =
            if (leadingIcon != null) {
                { Icon(leadingIcon, contentDescription = null, modifier = Modifier.padding(end = 4.dp)) }
            } else {
                null
            },
        trailingIcon =
            if (selected && trailingIcon == null) {
                { Icon(Icons.Filled.Close, contentDescription = "Clear") }
            } else if (trailingIcon != null) {
                { Icon(trailingIcon, contentDescription = null) }
            } else {
                null
            },
        colors =
            FilterChipDefaults.filterChipColors(
                labelColor = MaterialTheme.colorScheme.onSurface,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
            ),
    )
}

/**
 * Modern Material Design 3 input chip for user selections with removal option.
 * Used for tags, selected items, and user inputs.
 */
@Composable
fun ModernInputChip(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    InputChip(
        selected = true,
        onClick = {},
        label = { Text(label) },
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        leadingIcon =
            if (leadingIcon != null) {
                { Icon(leadingIcon, contentDescription = null) }
            } else {
                null
            },
        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove") },
        onRemove = onRemove,
        colors =
            InputChipDefaults.inputChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                labelColor = MaterialTheme.colorScheme.onSurface,
                trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    )
}

/**
 * Modern Material Design 3 assist chip for actions and suggestions.
 * Used for recommendations and supportive actions.
 */
@Composable
fun ModernAssistChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        leadingIcon =
            if (leadingIcon != null) {
                { Icon(leadingIcon, contentDescription = null) }
            } else {
                null
            },
        trailingIcon =
            if (trailingIcon != null) {
                { Icon(trailingIcon, contentDescription = null) }
            } else {
                null
            },
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                labelColor = MaterialTheme.colorScheme.onSurface,
                leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                trailingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    )
}

/**
 * Modern Material Design 3 suggestion chip for suggestions.
 * Used for search suggestions and recommendations.
 */
@Composable
fun ModernSuggestionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    SuggestionChip(
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        icon =
            if (leadingIcon != null) {
                { Icon(leadingIcon, contentDescription = null) }
            } else {
                null
            },
        colors =
            SuggestionChipDefaults.suggestionChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                labelColor = MaterialTheme.colorScheme.onSurface,
                iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    )
}

/**
 * A horizontal scrollable row of filter chips for easy filtering.
 * Used in filtered lists and search results.
 */
@Composable
fun ChipGroup(
    chips: List<String>,
    selectedChips: Set<String>,
    onChipClick: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            ModernFilterChip(
                label = chip,
                selected = chip in selectedChips,
                onSelectedChange = { selected -> onChipClick(chip, selected) },
            )
        }
    }
}
