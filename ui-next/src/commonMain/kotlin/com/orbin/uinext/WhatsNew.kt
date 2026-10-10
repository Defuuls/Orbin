package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
 */
@Composable
fun WhatsNewOnUpdate(
    lastSeen: Flow<String?>,
    onSeen: suspend (String) -> Unit,
    visible: Boolean,
    notes: ReleaseNotes = CurrentReleaseNotes,
) {
    // Nothing until the stored version has loaded, so an old value can't flash the dialog.
    val seen by remember(lastSeen) { lastSeen.map { Loaded(it) } }.collectAsState(null)
    var dismissed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val loaded = seen ?: return
    if (visible && !dismissed && shouldShowWhatsNew(notes, loaded.version)) {
        WhatsNewDialog(notes) {
            dismissed = true
            scope.launch { onSeen(notes.version) }
        }
    }
}

private class Loaded(
    val version: String?,
)

/** The "What's new" card shown once after an update, in the same chrome as every ui-next dialog. */
@Composable
fun WhatsNewDialog(
    notes: ReleaseNotes,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NextRadius.card))
                    .background(next.raised)
                    .padding(horizontal = NextSpace.gutter, vertical = NextSpace.section),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "What's new in ${notes.version.replace('-', ' ')}",
                style = NextType.title3,
                fontWeight = FontWeight.SemiBold,
                color = next.ink,
            )
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                notes.sections.filter { it.entries.isNotEmpty() }.forEach { section ->
                    Text(
                        text = section.heading,
                        style = NextType.body,
                        fontWeight = FontWeight.SemiBold,
                        color = next.ink,
                    )
                    section.entries.forEach { entry ->
                        Text(text = "• $entry", style = NextType.body, color = next.muted)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InlineAction(label = "Done", accent = true, onClick = onDismiss)
            }
        }
    }
}
