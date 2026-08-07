package com.orbin.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.orbin.core.designsystem.component.ModernFilterChip
import com.orbin.core.designsystem.component.ModernListItem

// Shared building blocks for every settings sub-screen (the hub and each category page). Kept
// `internal` rather than `private`-per-file so all of them stay visually and behaviorally
// identical without each screen re-declaring its own copy.

/** Test tag for the switch in the row titled [label]. Shared with the instrumentation tests. */
internal fun switchTagFor(label: String): String = "settings:switch:$label"

/** The running app's version name (e.g. "67-Pollux"), or empty if it could not be read. */
internal fun appVersionName(context: android.content.Context): String =
    runCatching {
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            .orEmpty()
    }.getOrDefault("")

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

@Composable
internal fun SupportingNote(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    supporting: String? = null,
) {
    ModernListItem(
        title = label,
        subtitle = supporting,
        trailing = {
            // The row itself is not clickable, so the switch is the only thing a test can drive.
            // Tagging it by label makes that addressable without asserting on the layout's shape.
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                modifier = Modifier.testTag(switchTagFor(label)),
            )
        },
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    )
}

@Composable
internal fun TextFieldRow(
    label: String,
    value: String,
    supporting: String,
    onValueChange: (String) -> Unit,
) {
    var localValue by remember(value) { mutableStateOf(value) }

    Column(
        modifier =
            Modifier
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxWidth(),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = localValue,
            onValueChange = { newValue ->
                localValue = newValue
                onValueChange(newValue)
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            supportingText = { Text(supporting) },
            singleLine = false,
            shape = MaterialTheme.shapes.medium,
        )
    }
}

@Composable
internal fun <T> ChipChoiceRow(
    label: String,
    values: List<T>,
    selected: T,
    text: (T) -> String,
    onChange: (T) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        LazyRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            // Keyed on the rendered label rather than the value: T is unconstrained here, so it
            // carries no guarantee of being a usable (Parcelable/primitive) key, whereas the label
            // is a String and is unique within a choice row by construction.
            items(values, key = text) { value ->
                ModernFilterChip(
                    label = text(value),
                    selected = value == selected,
                    onSelectedChange = { onChange(value) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun <T> ChoiceRow(
    label: String,
    values: List<T>,
    selected: T,
    text: (T) -> String,
    onChange: (T) -> Unit,
) {
    ChipChoiceRow(
        label = label,
        values = values,
        selected = selected,
        text = text,
        onChange = onChange,
    )
}
