package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_feed_done
import com.orbin.uinext.resources.social_feed_choose_source
import com.orbin.uinext.resources.social_feed_sources
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import com.orbin.uinext.tokens.ReaderType
import org.jetbrains.compose.resources.stringResource

/** Source selection stays outside the subscribed-board catalog and never covers media. */
@Composable
internal fun ReaderFeedControls(
    sourceLabels: List<String>,
    selectedSource: Int,
    onSelectSource: (Int) -> Unit,
    subtitle: String?,
    statusMessage: String?,
    headerContent: @Composable () -> Unit,
) {
    var sourcePicker by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(reader.background)) {
        if (sourceLabels.isNotEmpty()) {
            val source = sourceLabels.getOrNull(selectedSource).orEmpty()
            val label = stringResource(Res.string.social_feed_choose_source)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ReaderSize.touchTarget)
                    .nextClickable(role = Role.Button, onClickLabel = label) { sourcePicker = true }
                    .semantics {
                        contentDescription = label
                        stateDescription = source
                    }.padding(horizontal = ReaderSpace.gutter, vertical = ReaderSpace.gapSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReaderSpace.gapSmall),
            ) {
                Text(
                    source,
                    style = ReaderType.label.withReaderFont(),
                    color = reader.ink,
                    modifier = Modifier.weight(1f),
                )
                Icon(ReaderIcons.ChevronDown, null, tint = reader.inkMuted, modifier = Modifier.size(ReaderSize.icon))
            }
        }
        val status =
            listOfNotNull(
                subtitle?.takeIf(String::isNotBlank),
                statusMessage?.takeIf(String::isNotBlank),
            ).distinct()
        if (status.isNotEmpty()) {
            Text(
                status.joinToString(" · "),
                style = ReaderType.meta.withReaderFont(),
                color = reader.inkMuted,
                modifier = Modifier.padding(horizontal = ReaderSpace.gutter, vertical = ReaderSpace.line),
            )
        }
        headerContent()
        ReaderHairline()
    }
    if (sourcePicker) {
        ReaderFeedSourcePicker(
            labels = sourceLabels,
            selected = selectedSource,
            onSelect = { source ->
                onSelectSource(source)
                sourcePicker = false
            },
            onDismiss = { sourcePicker = false },
        )
    }
}

@Composable
private fun ReaderFeedSourcePicker(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = reader.background,
        title = {
            Text(
                stringResource(Res.string.social_feed_sources),
                style = ReaderType.appBarTitle.withReaderFont(),
                color = reader.ink,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                labels.forEachIndexed { index, label ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = ReaderSize.touchTarget)
                            .selectable(
                                selected = index == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(index) },
                            ).padding(vertical = ReaderSpace.gapSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ReaderSpace.gapSmall),
                    ) {
                        Text(
                            label,
                            style = ReaderType.stateBody.withReaderFont(),
                            color = reader.ink,
                            modifier = Modifier.weight(1f),
                        )
                        if (index ==
                            selected
                        ) {
                            Icon(
                                ReaderIcons.Check,
                                null,
                                tint = reader.accent,
                                modifier = Modifier.size(ReaderSize.icon),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(Res.string.reader_feed_done),
                    style = ReaderType.label.withReaderFont(),
                    color = reader.accent,
                )
            }
        },
    )
}
