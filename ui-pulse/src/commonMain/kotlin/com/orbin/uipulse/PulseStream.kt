package com.orbin.uipulse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Test tag of the whole stream, the surface a long press opens the controls from. */
const val PULSE_STREAM_TAG = "pulse-stream"

private val LineMinHeight = 56.dp
private val EdgePadding = 16.dp

/**
 * The single stream: a heading, a one-line summary and a plain list of text lines. There are no
 * cards, thumbnails or bars. Holding anywhere on the stream opens the controls; [controlsLabel]
 * is the same action as a visible button, for keyboards and screen readers.
 */
@Composable
fun PulseStream(
    lensName: String,
    summary: String,
    controlsLabel: String,
    lines: List<PulseLineUi>,
    onLineClick: (String) -> Unit,
    onOpenControls: () -> Unit,
    modifier: Modifier = Modifier,
    unreadLabel: String = "",
    onVisibleLines: (List<String>) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentLines = rememberUpdatedState(lines)
    val reportVisible = rememberUpdatedState(onVisibleLines)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
            .distinctUntilChanged()
            .collect { reportVisible.value(it) }
    }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(PULSE_STREAM_TAG)
                .pointerInput(onOpenControls) {
                    detectTapGestures(onLongPress = { onOpenControls() })
                },
    ) {
        StreamStrip(lensName, summary, controlsLabel, onOpenControls)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(lines, key = { it.id }) { line ->
                StreamLine(
                    line = line,
                    unreadLabel = unreadLabel,
                    onClick = { onLineClick(line.id) },
                    onRef = { postNo ->
                        val index = lineIndexOfNumber(currentLines.value, postNo)
                        if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                    },
                )
            }
        }
    }
}

@Composable
private fun StreamStrip(
    lensName: String,
    summary: String,
    controlsLabel: String,
    onOpenControls: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = EdgePadding, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = lensName,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenControls) { Text(controlsLabel) }
    }
}

@Composable
private fun StreamLine(
    line: PulseLineUi,
    unreadLabel: String,
    onClick: () -> Unit,
    onRef: (Long) -> Unit,
) {
    if (line.body.isEmpty()) {
        TitleLine(line, unreadLabel, onClick)
    } else {
        PostLine(line, unreadLabel, onClick, onRef)
    }
}

@Composable
private fun TitleLine(
    line: PulseLineUi,
    unreadLabel: String,
    onClick: () -> Unit,
) {
    val accent = if (line.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = LineMinHeight)
                .clickable(onClick = onClick)
                .padding(horizontal = EdgePadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = line.age,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            if (line.unread && unreadLabel.isNotEmpty()) UnreadLabel(unreadLabel)
            Text(
                text = line.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (line.unread) FontWeight.SemiBold else FontWeight.Normal,
                color = accent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = line.sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (line.trailing.isNotEmpty()) {
            Text(text = line.trailing, style = MaterialTheme.typography.labelMedium, color = accent)
        }
    }
}

@Composable
private fun UnreadLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** A post: a detail row, then its text line by line, with references that scroll to their post. */
@Composable
private fun PostLine(
    line: PulseLineUi,
    unreadLabel: String,
    onClick: () -> Unit,
    onRef: (Long) -> Unit,
) {
    val accent = if (line.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val refStyle = SpanStyle(color = MaterialTheme.colorScheme.primary)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = EdgePadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (line.unread && unreadLabel.isNotEmpty()) UnreadLabel(unreadLabel)
            Text(
                text = line.age,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = line.sub,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (line.unread) FontWeight.SemiBold else FontWeight.Normal,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (line.trailing.isNotEmpty()) {
                Text(text = line.trailing, style = MaterialTheme.typography.labelMedium, color = accent)
            }
        }
        line.body.forEach { bodyLine ->
            Text(
                text = annotateBody(bodyLine, refStyle, onRef),
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (bodyLine.quote) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
