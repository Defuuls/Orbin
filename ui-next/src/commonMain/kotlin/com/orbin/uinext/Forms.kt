package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType

/**
 * Modern Material 3 text field for forms.
 */
@Composable
fun NextTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(NextRadius.control)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    if (label != null) contentDescription = label
                },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = NextType.body.copy(color = next.ink),
        label = label?.let { { Text(it, style = NextType.sectionHeader) } },
        placeholder = placeholder?.let { { Text(it, style = NextType.body, color = next.faint) } },
        leadingIcon = leading,
        trailingIcon = trailing,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = shape,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = next.raised,
                unfocusedContainerColor = next.raised,
                disabledContainerColor = next.raised,
                focusedBorderColor = next.accent,
                unfocusedBorderColor = next.hairline,
                focusedTextColor = next.ink,
                unfocusedTextColor = next.ink,
                cursorColor = next.accent,
            ),
    )
}

/**
 * Label + [NextToggle] row for boolean form preferences.
 *
 * Prefer this over Material Checkbox so Search preference rows share the Settings On/Off
 * word-toggle language.
 */
@Composable
fun NextToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MIN_TOUCH_TARGET)
                .clip(RoundedCornerShape(NextRadius.control))
                .background(next.raised)
                .nextClickable(role = Role.Switch, onClick = { onCheckedChange(!checked) })
                .padding(horizontal = NextSpace.rowX, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = NextType.body,
            color = next.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        NextToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Single-select control in the Settings choice language.
 *
 * Tapping the value row expands options underneath as selectable rows — not Material
 * ExposedDropdownMenu. Use for board pickers and similar short lists on Next screens.
 */
@Composable
fun NextSelect(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    var expanded by remember { mutableStateOf(false) }
    val display =
        options.getOrNull(selectedIndex)?.takeIf { selectedIndex >= 0 } ?: placeholder
    val shape = RoundedCornerShape(NextRadius.control)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = NextType.sectionHeader,
            color = next.muted,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .clip(shape)
                    .background(next.raised)
                    .nextClickable(role = Role.Button, onClick = { expanded = !expanded })
                    .padding(horizontal = NextSpace.rowX, vertical = 12.dp)
                    .semantics { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = display.ifEmpty { placeholder },
                style = NextType.body,
                color = if (display.isEmpty()) next.faint else next.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "▴" else "▾",
                style = NextType.footnote,
                color = next.muted,
            )
        }
        if (expanded) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(NextRadius.card))
                        .background(next.raised),
            ) {
                options.forEachIndexed { index, option ->
                    NextSelectOptionRow(
                        label = option,
                        selected = index == selectedIndex,
                        onClick = {
                            onSelect(index)
                            expanded = false
                        },
                    )
                    if (index < options.lastIndex) GroupedDivider()
                }
            }
        }
    }
}

@Composable
private fun NextSelectOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = MIN_TOUCH_TARGET)
                .nextClickable(role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = NextSpace.rowX, vertical = NextSpace.rowY),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = NextType.body,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) next.accent else next.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text(
                text = "✓",
                style = NextType.footnote,
                fontWeight = FontWeight.SemiBold,
                color = next.accent,
            )
        }
    }
}
