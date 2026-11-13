package com.orbin.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_whats_new_done
import com.orbin.uinext.resources.next_whats_new_title
import com.orbin.uinext.tokens.ReaderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** One heading of a release's changelog ("Added", "Changed", "Fixed") and its entries. */
data class ReleaseNoteSection(
    val heading: String,
    val entries: List<String>,
)

/** What changed in [version], as CHANGELOG.md tells it. */
data class ReleaseNotes(
    val version: String,
    val sections: List<ReleaseNoteSection>,
) {
    val isEmpty: Boolean get() = sections.all { it.entries.isEmpty() }
}

/**
 * Whether to show [notes] to a reader who last saw the notes for [lastSeen]: once per release,
 * and never for a build with no changelog section of its own.
 */
fun shouldShowWhatsNew(
    notes: ReleaseNotes,
    lastSeen: String?,
): Boolean = !notes.isEmpty && notes.version != lastSeen

/**
 * Shows this build's [notes] once, the first time the app is [visible] after an update: [lastSeen]
 * is the version whose notes were last shown, and [onSeen] records that these have been.
 * [content] always composes, with media covered while the stored version is pending or notes show.
 */
@Composable
fun WhatsNewOnUpdate(
    lastSeen: Flow<String?>,
    onSeen: suspend (String) -> Unit,
    visible: Boolean,
    notes: ReleaseNotes = CurrentReleaseNotes,
    content: @Composable (covered: Boolean) -> Unit = {},
) {
    // Only the dialog waits for storage; content remains covered until its visibility is known.
    val seen by remember(lastSeen) { lastSeen.map { Loaded(it) } }.collectAsState(null)
    var dismissed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val loaded = seen
    val showNotes = loaded != null && !dismissed && shouldShowWhatsNew(notes, loaded.version)
    content(visible && (loaded == null || showNotes))
    if (visible && showNotes) {
        WhatsNewDialog(notes) {
            dismissed = true
            scope.launch { onSeen(notes.version) }
        }
    }
}

private class Loaded(
    val version: String?,
)

/** The "What's new" card shown once after an update, in Coral dialog chrome. */
@Composable
fun WhatsNewDialog(
    notes: ReleaseNotes,
    onDismiss: () -> Unit,
) {
    OrbinDialog(onDismissRequest = onDismiss) {
        OrbinDialogTitle(
            stringResource(Res.string.next_whats_new_title, notes.version.replace('-', ' ')),
        )
        Column(
            modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            notes.sections.filter { it.entries.isNotEmpty() }.forEach { section ->
                Text(
                    text = section.heading,
                    style = ReaderType.rowTitle.withReaderFont(),
                    fontWeight = FontWeight.SemiBold,
                    color = reader.ink,
                )
                section.entries.forEach { entry ->
                    Text(
                        text = "• $entry",
                        style = ReaderType.body.withReaderFont(),
                        color = reader.inkMuted,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbinFilledButton(
                label = stringResource(Res.string.next_whats_new_done),
                onClick = onDismiss,
            )
        }
    }
}
