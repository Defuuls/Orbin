package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_settings_done
import com.orbin.uinext.resources.next_settings_no_matches
import com.orbin.uinext.resources.next_settings_search
import com.orbin.uinext.resources.social_settings_back
import com.orbin.uinext.resources.social_settings_downloads
import com.orbin.uinext.resources.social_settings_title
import com.orbin.uinext.resources.social_settings_view_history
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.stringResource

data class SettingItem(
    val id: String,
    val label: String,
    val value: String,
    val kind: SettingKind = SettingKind.TOGGLE,
    val options: List<String> = emptyList(),
    val selected: Int = -1,
    val text: String = "",
    val hint: String? = null,
    /** When true, the trailing value uses [nextDestructiveRed] (Delete / Clear rows). */
    val destructive: Boolean = false,
    /** A [SettingKind.TEXT] row whose text is hidden as typed, and never pre-filled (a password). */
    val secret: Boolean = false,
    /** Show a selectable, wrapping pairing code below an informational row. */
    val copyable: Boolean = false,
    /** Explicit boolean checked state for [SettingKind.TOGGLE], decoupling switch state from localized labels. */
    val checked: Boolean? = null,
)

enum class SettingKind {
    TOGGLE,
    CHOICE,
    TEXT,
    ACTION,
    INFO,
}

/** A searchable settings directory with compact, task-specific subpages. */
@Composable
fun SettingsScreen(
    groups: List<Pair<String, List<SettingItem>>>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    expandedId: String? = null,
    @Suppress("UnusedParameter") showRail: Boolean = true,
    onActivate: (SettingItem) -> Unit = {},
    onSelectOption: (SettingItem, Int) -> Unit = { _, _ -> },
    onCommitText: (SettingItem, String) -> Unit = { _, _ -> },
    onBack: (() -> Unit)? = null,
    onNavigate: ((String) -> Unit)? = null,
) {
    val state = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    val pages = remember(groups) { settingsPages(groups) }
    var page by rememberSaveable {
        mutableStateOf(
            expandedId?.let { id ->
                pages.entries.firstOrNull { (_, rows) -> rows.any { it.id == id } }?.key
            },
        )
    }
    val activePage = page
    val labels = settingsPageLabels()
    val title = activePage?.let { labels[it] } ?: stringResource(Res.string.social_settings_title)
    val visibleGroups =
        remember(groups, query, activePage) {
            if (query.isNotBlank()) {
                filterSettings(groups, query)
            } else if (activePage != null) {
                listOf("" to pages[activePage].orEmpty())
            } else {
                emptyList()
            }
        }
    LaunchedEffect(activePage) { state.scrollToItem(0) }
    LaunchedEffect(expandedId, query) {
        val targetId = expandedId ?: return@LaunchedEffect
        val groupIndex = visibleGroups.indexOfFirst { (_, rows) -> rows.any { it.id == targetId } }
        if (groupIndex >= 0) state.scrollToItem(1 + groupIndex)
    }
    val backAction: (() -> Unit)? =
        if (activePage != null || query.isNotBlank()) {
            {
                page = null
                query = ""
            }
        } else {
            onBack
        }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = activePage != null || query.isNotBlank(),
        onBackCompleted = {
            page = null
            query = ""
        },
    )
    val searchLabel = stringResource(Res.string.next_settings_search)
    Column(modifier = modifier.fillMaxSize().background(next.background).contentInsets()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(48.dp)) {
                if (backAction != null) {
                    NextIconAction(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(Res.string.social_settings_back),
                        onClick = backAction,
                    )
                }
            }
            Text(
                text = title,
                style = NextType.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() },
                color = next.ink,
            )
            WidthSpacer(48)
        }
        Hairline()
        LazyColumn(
            state = state,
            modifier = Modifier.weight(1f).imePadding().testTag("settings-list"),
            contentPadding = PaddingValues(bottom = 24.dp + bottomInset()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "search") {
                Column {
                    Gap(12)
                    SchematicSearch(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = searchLabel,
                        modifier = Modifier.padding(horizontal = GUTTER).semantics { contentDescription = searchLabel },
                    )
                    if (subtitle != null && activePage == null) {
                        Text(
                            subtitle,
                            style = NextType.footnote,
                            color = next.muted,
                            modifier = Modifier.padding(GUTTER),
                        )
                    }
                }
            }
            if (activePage == null && query.isBlank()) {
                settingsDirectory(pages.keys, onNavigate != null).forEach { (heading, entries) ->
                    item(key = "directory:$heading") {
                        Column {
                            Text(
                                text = directorySectionLabel(heading),
                                style = NextType.footnote,
                                color = next.muted,
                                modifier =
                                    Modifier
                                        .padding(
                                            horizontal = GUTTER,
                                            vertical = 8.dp,
                                        ).semantics { heading() },
                            )
                            entries.forEach { destination ->
                                SettingsDirectoryRow(
                                    destination = destination,
                                    label = labels.getValue(destination),
                                    onClick = {
                                        if (destination in
                                            EXTERNAL_SETTINGS_DESTINATIONS
                                        ) {
                                            onNavigate?.invoke(destination)
                                        } else {
                                            page =
                                                if (destination ==
                                                    "boardPreferences"
                                                ) {
                                                    "playback"
                                                } else {
                                                    destination
                                                }
                                            query = ""
                                        }
                                    },
                                )
                            }
                            Gap(12)
                            Hairline()
                        }
                    }
                }
            } else {
                if (visibleGroups.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(Res.string.next_settings_no_matches),
                            style = NextType.body,
                            color = next.muted,
                            modifier = Modifier.padding(horizontal = GUTTER),
                        )
                    }
                }
                visibleGroups.forEachIndexed { index, (heading, rows) ->
                    item(key = "group:$index") {
                        Column {
                            if (heading.isNotBlank()) {
                                Text(
                                    heading,
                                    style = NextType.footnote,
                                    color = next.muted,
                                    modifier = Modifier.padding(GUTTER),
                                )
                            }
                            if (activePage == "history" && onNavigate != null && query.isBlank()) {
                                SettingsDirectoryRow(
                                    "history",
                                    stringResource(Res.string.social_settings_view_history),
                                ) {
                                    onNavigate("history")
                                }
                            }
                            if (activePage == "storage" && onNavigate != null && query.isBlank()) {
                                SettingsDirectoryRow(
                                    "downloads",
                                    stringResource(Res.string.social_settings_downloads),
                                ) {
                                    onNavigate("downloads")
                                }
                            }
                            rows.forEach { item ->
                                SettingRow(item, item.id == expandedId, onActivate, onSelectOption, onCommitText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDirectoryRow(
    destination: String,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .nextClickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = GUTTER, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(settingsIcon(destination), contentDescription = null, tint = next.ink, modifier = Modifier.size(24.dp))
        Text(label, style = NextType.body, color = next.ink, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = next.faint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SettingRow(
    item: SettingItem,
    expanded: Boolean,
    onActivate: (SettingItem) -> Unit,
    onSelectOption: (SettingItem, Int) -> Unit,
    onCommitText: (SettingItem, String) -> Unit,
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(expanded) {
        if (expanded) bringIntoView.bringIntoView()
    }
    Column(Modifier.bringIntoViewRequester(bringIntoView)) {
        ListItem(
            headlineContent = {
                Text(
                    text = item.label,
                    style = NextType.body,
                    color = next.ink,
                )
            },
            supportingContent =
                if (item.kind != SettingKind.TOGGLE || item.hint != null) {
                    {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (item.kind != SettingKind.TOGGLE && item.value.isNotBlank()) {
                                SelectionContainer {
                                    Text(
                                        text = item.value,
                                        style = NextType.callout,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (item.destructive) nextDestructiveRed else next.muted,
                                    )
                                }
                            }
                            if (item.hint != null && item.kind != SettingKind.TEXT) {
                                Text(
                                    text = item.hint,
                                    style = NextType.footnote,
                                    color = next.muted,
                                )
                            }
                        }
                    }
                } else {
                    null
                },
            trailingContent =
                if (item.kind == SettingKind.TOGGLE) {
                    {
                        Box(Modifier.clearAndSetSemantics {}) {
                            NextToggle(checked = item.isOn(), onCheckedChange = null)
                        }
                    }
                } else {
                    null
                },
            colors =
                ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (item.kind == SettingKind.INFO) {
                            Modifier
                        } else {
                            val role = if (item.kind == SettingKind.TOGGLE) Role.Switch else Role.Button
                            Modifier
                                .nextClickable(role = role, onClick = { onActivate(item) })
                                .semantics {
                                    if (item.kind == SettingKind.TOGGLE) {
                                        toggleableState = ToggleableState(item.isOn())
                                    }
                                }
                        },
                    ),
        )
        if (item.copyable && item.text.isNotBlank()) {
            SelectionContainer {
                Text(
                    text = item.text,
                    style = NextType.footnote,
                    color = next.ink,
                    modifier = Modifier.padding(start = GUTTER, end = GUTTER, bottom = 14.dp),
                )
            }
        }
        if (!expanded) return@Column
        if (item.kind == SettingKind.TEXT && item.hint != null) {
            Text(
                text = item.hint,
                style = NextType.footnote,
                color = next.muted,
                modifier = Modifier.padding(start = GUTTER, end = GUTTER, bottom = 10.dp),
            )
        }
        when {
            item.kind == SettingKind.TEXT -> SettingTextEditor(item, onCommitText)
            item.options.isNotEmpty() ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                ) {
                    item.options.forEachIndexed { index, option ->
                        InlineAction(
                            label = option,
                            selected = index == item.selected,
                            onClick = { onSelectOption(item, index) },
                        )
                    }
                }
        }
    }
}

@Composable
private fun SettingTextEditor(
    item: SettingItem,
    onCommitText: (SettingItem, String) -> Unit,
) {
    var draft by remember(item.id, item.text) { mutableStateOf(item.text) }
    val focus = remember { FocusRequester() }
    val commit = { onCommitText(item, draft) }
    LaunchedEffect(item.id) { focus.requestFocus() }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = GUTTER, end = GUTTER, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (draft.isEmpty()) {
                Text(
                    text = item.value,
                    style = NextType.body,
                    color = next.faint,
                    maxLines = 1,
                )
            }
            // BasicTextField already supports selection; the value Text above keeps
            // SelectionContainer for long-press copy of the committed value.
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = NextType.body.copy(color = next.ink),
                cursorBrush = SolidColor(next.accent),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = if (item.secret) KeyboardType.Password else KeyboardType.Unspecified,
                        imeAction = ImeAction.Done,
                    ),
                visualTransformation =
                    if (item.secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Hairline(modifier = Modifier.padding(top = 26.dp))
        }
        WidthSpacer(8)
        InlineAction(
            label = stringResource(Res.string.next_settings_done),
            accent = true,
            onClick = commit,
        )
    }
}

private fun SettingItem.isOn(): Boolean = checked ?: (kind == SettingKind.TOGGLE && value != OFF_LABEL)

const val ON_LABEL = "On"
const val OFF_LABEL = "Off"
