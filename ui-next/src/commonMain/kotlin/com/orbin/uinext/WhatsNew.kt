@file:Suppress("MagicNumber")

package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_whats_new_done
import com.orbin.uinext.resources.next_whats_new_title
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType
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

/** Optional theme migration controls shown on the full-screen update setup screen. */
data class UpdateThemeConfig(
    val theme: SetupThemeOption,
    val onThemeChange: (SetupThemeOption) -> Unit,
)

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
    themeConfig: UpdateThemeConfig? = null,
    content: @Composable (covered: Boolean) -> Unit = {},
) {
    // Only the screen waits for storage; content remains covered until its visibility is known.
    val seen by remember(lastSeen) { lastSeen.map { Loaded(it) } }.collectAsState(null)
    var dismissed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val loaded = seen
    val showNotes = loaded != null && !dismissed && shouldShowWhatsNew(notes, loaded.version)
    content(visible && (loaded == null || showNotes))
    if (visible && showNotes) {
        UpdateSetupScreen(
            notes = notes,
            themeConfig = themeConfig,
            onDismiss = {
                dismissed = true
                scope.launch { onSeen(notes.version) }
            },
        )
    }
}

private class Loaded(
    val version: String?,
)

/** Full-screen Midnight Electric update & migration screen shown once after an app update. */
@Composable
fun UpdateSetupScreen(
    notes: ReleaseNotes,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    themeConfig: UpdateThemeConfig? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(reader.background)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NextSpace.gutter, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OrbinLogoMark(modifier = Modifier.size(40.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(Res.string.next_whats_new_title, notes.version.replace('-', ' ')),
                        style = OrbinType.stateTitle.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
                        color = reader.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "What's new & theme setup",
                        style = OrbinType.meta.withReaderFont(),
                        color = reader.inkMuted,
                    )
                }
            }
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = NextSpace.gutter, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (themeConfig != null) {
                UpdateThemeMigrationCard(themeConfig)
            }
            notes.sections.filter { it.entries.isNotEmpty() }.forEach { section ->
                OrbinCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = section.heading,
                            style = OrbinType.rowTitle.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
                            color = reader.accent,
                        )
                        section.entries.forEach { entry ->
                            Text(
                                text = "• $entry",
                                style = OrbinType.readerBody.withReaderFont(),
                                color = reader.inkMuted,
                            )
                        }
                    }
                }
            }
        }
        ReaderHairline()
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(reader.background)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(horizontal = NextSpace.gutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbinFilledButton(
                label = stringResource(Res.string.next_whats_new_done),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UpdateThemeMigrationCard(config: UpdateThemeConfig) {
    val choices =
        listOf(
            "System" to SetupThemeOption.SYSTEM,
            "Light" to SetupThemeOption.LIGHT,
            "Dark" to SetupThemeOption.DARK,
            "AMOLED" to SetupThemeOption.AMOLED,
        )
    OrbinCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Midnight Electric theme",
                style = OrbinType.rowTitle.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
                color = reader.ink,
            )
            Text(
                text = "Confirm or switch your appearance for this release.",
                style = OrbinType.meta.withReaderFont(),
                color = reader.inkMuted,
            )
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                choices.forEach { (label, option) ->
                    val selected = config.theme == option
                    val shape = RoundedCornerShape(OrbinRadius.pill)
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .heightIn(min = MIN_TOUCH_TARGET)
                                .clip(shape)
                                .background(if (selected) reader.accent else Color.Transparent)
                                .border(1.dp, if (selected) reader.accent else reader.hairline, shape)
                                .selectable(selected = selected, role = Role.RadioButton) {
                                    config.onThemeChange(option)
                                }.padding(horizontal = 6.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style =
                                OrbinType.caption1.withReaderFont().copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                            color = if (selected) reader.onAccent else reader.ink,
                        )
                    }
                }
            }
        }
    }
}

/** The "What's new" card shown once after an update, in Midnight Electric dialog chrome. */
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
                    style = OrbinType.rowTitle.withReaderFont(),
                    fontWeight = FontWeight.SemiBold,
                    color = reader.ink,
                )
                section.entries.forEach { entry ->
                    Text(
                        text = "• $entry",
                        style = OrbinType.readerBody.withReaderFont(),
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
