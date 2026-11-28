package com.orbin.uipulse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Test tag of the command line inside the controls. */
const val PULSE_COMMAND_TAG = "pulse-command"

private val ControlsPadding = 16.dp
private val ButtonMinHeight = 48.dp
private const val LENS_COLUMNS = 2

/**
 * The controls, centered on screen: Refresh, Back (when [showBack]), every view and a command
 * line with suggestions. Nothing sits at the top or bottom of the screen. Dismissing, running a
 * command or choosing a view all go through the callbacks; the host decides what closes it.
 */
@Composable
fun PulseControls(
    visible: Boolean,
    labels: PulseControlsLabels,
    lenses: List<PulseLensUi>,
    showBack: Boolean,
    command: String,
    suggestions: List<PulseCommandUi>,
    onCommandChange: (String) -> Unit,
    onRunCommand: (String) -> Unit,
    onSubmit: () -> Unit,
    onLens: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(0.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(ControlsPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = labels.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                ActionRow(labels, showBack, onRefresh, onBack)
                LensGrid(lenses, onLens)
                CommandLine(labels, command, onCommandChange, onSubmit)
                suggestions.forEach { suggestion ->
                    TextButton(
                        onClick = { onRunCommand(suggestion.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight),
                    ) { Text(suggestion.label) }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(labels.close) }
            }
        }
    }
}

@Composable
private fun ActionRow(
    labels: PulseControlsLabels,
    showBack: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.weight(1f).heightIn(min = ButtonMinHeight),
        ) { Text(labels.refresh) }
        if (showBack) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).heightIn(min = ButtonMinHeight),
            ) { Text(labels.back) }
        }
    }
}

@Composable
private fun LensGrid(
    lenses: List<PulseLensUi>,
    onLens: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lenses.chunked(LENS_COLUMNS).forEach { rowLenses ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowLenses.forEach { lens ->
                    LensButton(lens, onClick = { onLens(lens.id) }, modifier = Modifier.weight(1f))
                }
                if (rowLenses.size < LENS_COLUMNS) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LensButton(
    lens: PulseLensUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonModifier = modifier.heightIn(min = ButtonMinHeight).semantics { selected = lens.selected }
    if (lens.selected) {
        Button(onClick = onClick, modifier = buttonModifier) { Text(lens.label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = buttonModifier) { Text(lens.label) }
    }
}

@Composable
private fun CommandLine(
    labels: PulseControlsLabels,
    command: String,
    onCommandChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = command,
        onValueChange = onCommandChange,
        singleLine = true,
        placeholder = { Text(labels.commandHint) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier.fillMaxWidth().testTag(PULSE_COMMAND_TAG),
    )
}
