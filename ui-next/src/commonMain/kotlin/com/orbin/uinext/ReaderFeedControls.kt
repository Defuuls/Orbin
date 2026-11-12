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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_feed_done
import com.orbin.uinext.resources.social_feed_choose_source
import com.orbin.uinext.resources.social_feed_sources
import com.orbin.uinext.resources.tiktok_feed_following
import com.orbin.uinext.resources.tiktok_feed_for_you
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import com.orbin.uinext.tokens.ReaderType
import org.jetbrains.compose.resources.stringResource

/** Feed membership and source selection stay outside the scrolling catalog and never cover media. */
@Composable
internal fun ReaderFeedControls(
    following: Boolean,
    onFollowingChange: (Boolean) -> Unit,
    sourceLabels: List<String>,
    selectedSource: Int,
    onSelectSource: (Int) -> Unit,
    subtitle: String?,
    statusMessage: String?,
    headerContent: @Composable () -> Unit,
) {
    var sourcePicker by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(reader.background)) {
        Row(Modifier.fillMaxWidth()) {
            ReaderFeedChoice(
                label = stringResource(Res.string.tiktok_feed_following),
                selected = following,
                onSelect = { onFollowingChange(true) },
                modifier = Modifier.weight(1f).testTag(ReaderFeedTags.FOLLOWING),
            )
            ReaderFeedChoice(
                label = stringResource(Res.string.tiktok_feed_for_you),
                selected = !following,
                onSelect = { onFollowingChange(false) },
                modifier = Modifier.weight(1f).testTag(ReaderFeedTags.FOR_YOU),
            )
        }
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
                Text(source, style = ReaderType.label, color = reader.ink, modifier = Modifier.weight(1f))
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
                style = ReaderType.meta,
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
private fun ReaderFeedChoice(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier,
) {
    Text(
        text = label,
        style = ReaderType.label,
        color = reader.ink,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .heightIn(min = ReaderSize.touchTarget)
                .background(if (selected) reader.selected else reader.background)
                .selectable(selected = selected, role = Role.Tab, onClick = onSelect)
                .padding(horizontal = ReaderSpace.gutter, vertical = ReaderSpace.gap),
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
                stringResource(Res.string.social_feed_sources),
                style = ReaderType.appBarTitle,
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
                        Text(label, style = ReaderType.stateBody, color = reader.ink, modifier = Modifier.weight(1f))
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
                Text(stringResource(Res.string.reader_feed_done), style = ReaderType.label, color = reader.accent)
            }
        },
    )
}
