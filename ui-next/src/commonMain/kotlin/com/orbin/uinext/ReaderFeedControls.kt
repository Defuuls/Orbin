package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_feed_choose_source
import com.orbin.uinext.resources.reader_feed_done
import com.orbin.uinext.resources.reader_feed_sources
import com.orbin.uinext.resources.reader_view_grid
import com.orbin.uinext.resources.reader_view_list
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
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
    mode: CatalogMode,
    onModeChange: ((CatalogMode) -> Unit)?,
) {
    var sourcePicker by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(reader.background)) {
        if (sourceLabels.isNotEmpty() || onModeChange != null) {
            BoxWithConstraints(
                Modifier.fillMaxWidth().padding(horizontal = ReaderSpace.gutter, vertical = ReaderSpace.gapSmall),
            ) {
                val stacked = maxWidth < 300.dp && LocalDensity.current.fontScale >= 1.5f
                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(ReaderSpace.gapSmall)) {
                        if (sourceLabels.isNotEmpty()) {
                            ReaderFeedSourceChip(
                                sourceLabels,
                                selectedSource,
                                { sourcePicker = true },
                                Modifier.fillMaxWidth(),
                            )
                        }
                        onModeChange?.let { change ->
                            ReaderFeedModeToggle(mode, change, Modifier.align(Alignment.End))
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ReaderSpace.gapSmall),
                    ) {
                        if (sourceLabels.isNotEmpty()) {
                            ReaderFeedSourceChip(
                                sourceLabels,
                                selectedSource,
                                { sourcePicker = true },
                                Modifier.weight(1f, fill = false),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        onModeChange?.let { ReaderFeedModeToggle(mode, it) }
                    }
                }
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
                style = OrbinType.meta.withReaderFont(),
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
private fun ReaderFeedSourceChip(
    labels: List<String>,
    selected: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.reader_feed_choose_source)
    val source = labels.getOrNull(selected)?.takeIf(String::isNotBlank) ?: label
    val chipModifier =
        modifier
            .testTag(ReaderFeedTags.SOURCE)
            .heightIn(min = ReaderSize.touchTarget)
            .clip(RoundedCornerShape(OrbinRadius.pill))
            .background(reader.surfaceVariant)
            .nextClickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics {
                contentDescription = label
                stateDescription = source
            }.padding(horizontal = 12.dp, vertical = ReaderSpace.gapSmall)
    if (LocalDensity.current.fontScale >= 1.5f) {
        // Large text gets the complete chip width; a trailing chevron must not squeeze its label.
        Column(chipModifier, verticalArrangement = Arrangement.spacedBy(ReaderSpace.gapSmall)) {
            Text(
                source,
                style = OrbinType.label.withReaderFont(),
                color = reader.ink,
                modifier = Modifier.fillMaxWidth(),
            )
            Icon(
                ReaderIcons.ChevronDown,
                null,
                tint = reader.inkMuted,
                modifier = Modifier.align(Alignment.End).size(ReaderSize.icon),
            )
        }
    } else {
        Row(
            chipModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ReaderSpace.gapSmall),
        ) {
            Text(
                source,
                style = OrbinType.label.withReaderFont(),
                color = reader.ink,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(ReaderIcons.ChevronDown, null, tint = reader.inkMuted, modifier = Modifier.size(ReaderSize.icon))
        }
    }
}

@Composable
private fun ReaderFeedModeToggle(
    mode: CatalogMode,
    onChange: (CatalogMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grid = mode == CatalogMode.GRID
    val action = stringResource(if (grid) Res.string.reader_view_list else Res.string.reader_view_grid)
    val current = stringResource(if (grid) Res.string.reader_view_grid else Res.string.reader_view_list)
    NextIconAction(
        imageVector = if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
        contentDescription = action,
        onClick = { onChange(if (grid) CatalogMode.LIST else CatalogMode.GRID) },
        modifier = modifier.testTag(ReaderFeedTags.MODE).semantics { stateDescription = current },
        tint = reader.accent,
    )
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
                stringResource(Res.string.reader_feed_sources),
                style = OrbinType.appBarTitle.withReaderFont(),
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
                            style = OrbinType.stateBody.withReaderFont(),
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
                    style = OrbinType.label.withReaderFont(),
                    color = reader.accent,
                )
            }
        },
    )
}
