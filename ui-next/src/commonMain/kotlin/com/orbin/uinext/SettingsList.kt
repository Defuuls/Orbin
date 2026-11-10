package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_settings_done
import com.orbin.uinext.resources.next_settings_no_matches
import com.orbin.uinext.resources.next_settings_search
import com.orbin.uinext.resources.social_settings_back
import com.orbin.uinext.resources.social_settings_downloads
import com.orbin.uinext.resources.social_settings_search_options
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
    enabledSiteCount: Int? = null,
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
    var choiceId by rememberSaveable {
        mutableStateOf(
            groups.flatMap { it.second }.firstOrNull { it.id == expandedId && it.kind == SettingKind.CHOICE }?.id,
        )
    }
    val choice = groups.flatMap { it.second }.firstOrNull { it.id == choiceId }
    val labels = settingsPageLabels()
    val title = choice?.label ?: page?.let { labels[it] } ?: stringResource(Res.string.social_settings_title)
    val visibleGroups =
        remember(groups, query, page) {
            when {
                query.isNotBlank() -> filterSettings(groups, query)
                page != null -> listOf("" to pages[page].orEmpty())
                else -> emptyList()
            }
        }
    LaunchedEffect(page) { state.scrollToItem(0) }
    val closeLocalPage: () -> Unit = {
        if (choiceId != null) {
            choiceId = null
        } else {
            page = null
            query = ""
        }
    }
    val localBack = choiceId != null || page != null || query.isNotBlank()
    val backAction = if (localBack) closeLocalPage else onBack
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = localBack,
        onBackCompleted = closeLocalPage,
    )
    val activate: (SettingItem) -> Unit = { item ->
        if (item.kind == SettingKind.CHOICE) choiceId = item.id
        onActivate(item)
    }
    Column(modifier = modifier.fillMaxSize().background(next.background).contentInsets()) {
        SettingsNavigationBar(title, backAction)
        if (choice != null) {
            SettingsChoiceList(choice) { index ->
                onSelectOption(choice, index)
                choiceId = null
            }
        } else {
            LazyColumn(
                state = state,
                modifier = Modifier.weight(1f).imePadding().testTag("settings-list"),
                contentPadding = PaddingValues(bottom = 24.dp + bottomInset()),
            ) {
                if (page == null) {
                    item(key = "search") {
                        val searchLabel = stringResource(Res.string.next_settings_search)
                        Column(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
                            SchematicSearch(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = searchLabel,
                                modifier =
                                    Modifier.padding(horizontal = GUTTER).semantics {
                                        contentDescription =
                                            searchLabel
                                    },
                            )
                            if (subtitle != null) {
                                Text(
                                    subtitle,
                                    style = NextType.footnote,
                                    color = next.muted,
                                    modifier = Modifier.padding(GUTTER),
                                )
                            }
                        }
                    }
                }
                if (page == null && query.isBlank()) {
                    settingsDirectory(pages.keys, onNavigate != null).forEachIndexed { index, (heading, entries) ->
                        item(key = "directory:$heading") {
                            Column {
                                if (index > 0) Hairline()
                                SettingsSectionHeading(directorySectionLabel(heading))
                                entries.forEach { destination ->
                                    SettingsDirectoryRow(
                                        destination,
                                        labels.getValue(destination),
                                        value = if (destination == "boards") enabledSiteCount?.toString() else null,
                                    ) {
                                        if (destination in
                                            EXTERNAL_SETTINGS_DESTINATIONS
                                        ) {
                                            onNavigate?.invoke(destination)
                                        } else {
                                            page = if (destination == "boardPreferences") "playback" else destination
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (visibleGroups.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(Res.string.next_settings_no_matches),
                                style = NextType.body,
                                color = next.muted,
                                modifier = Modifier.padding(GUTTER),
                            )
                        }
                    }
                    visibleGroups.forEachIndexed { index, (heading, rows) ->
                        item(key = "group:$index") {
                            Column {
                                if (heading.isNotBlank()) SettingsSectionHeading(heading)
                                if (page == "history" && onNavigate != null && query.isBlank()) {
                                    SettingsDirectoryRow(
                                        "history",
                                        stringResource(Res.string.social_settings_view_history),
                                    ) {
                                        onNavigate("history")
                                    }
                                }
                                if (page == "storage" && onNavigate != null && query.isBlank()) {
                                    SettingsDirectoryRow(
                                        "downloads",
                                        stringResource(Res.string.social_settings_downloads),
                                    ) {
                                        onNavigate("downloads")
                                    }
                                }
                                rows.forEach { item ->
                                    SettingRow(
                                        item,
                                        item.id == expandedId && item.kind == SettingKind.TEXT,
                                        activate,
                                        onCommitText,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigationBar(
    title: String,
    onBack: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(48.dp)) {
            if (onBack != null) {
                NextIconAction(
                    imageVector = ReaderIcons.Back,
                    contentDescription = stringResource(Res.string.social_settings_back),
                    onClick = onBack,
                )
            }
        }
        Text(
            title,
            style = NextType.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() },
            color = next.ink,
        )
        WidthSpacer(48)
    }
}

@Composable
private fun SettingsSectionHeading(label: String) {
    Text(
        text = label,
        style =
            NextType.footnote.copy(
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
        color = next.muted,
        modifier = Modifier.padding(start = GUTTER, end = GUTTER, top = 16.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun SettingsDirectoryRow(
    destination: String,
    label: String,
    value: String? = null,
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (value != null) Text(value, style = NextType.bodyDense, color = next.muted)
            SettingsChevron()
        }
    }
}

@Composable
private fun SettingsChevron() {
    Icon(
        ReaderIcons.ChevronRight,
        contentDescription = null,
        tint = next.faint,
        modifier = Modifier.size(16.dp),
    )
}

@Composable
private fun SettingsChoiceList(
    item: SettingItem,
    onSelect: (Int) -> Unit,
) {
    var optionQuery by rememberSaveable(item.id) { mutableStateOf("") }
    val options =
        remember(item.options, optionQuery) {
            item.options.withIndex().filter { it.value.contains(optionQuery.trim(), ignoreCase = true) }
        }
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .imePadding()
                .testTag("settings-list")
                .selectableGroup(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp + bottomInset()),
    ) {
        if (item.options.size > 8) {
            item(key = "search-options") {
                SchematicSearch(
                    optionQuery,
                    { optionQuery = it },
                    stringResource(Res.string.social_settings_search_options),
                    modifier = Modifier.padding(horizontal = GUTTER, vertical = 8.dp),
                )
            }
        }
        if (item.hint != null) {
            item(key = "hint") {
                Text(
                    item.hint,
                    style = NextType.footnote.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = next.muted,
                    modifier = Modifier.padding(horizontal = GUTTER, vertical = 8.dp),
                )
            }
        }
        items(options, key = { it.index }) { (index, option) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = index == item.selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(index) },
                    ).padding(horizontal = GUTTER, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(option, style = NextType.body, color = next.ink, modifier = Modifier.weight(1f))
                if (index == item.selected) {
                    Icon(
                        ReaderIcons.Check,
                        contentDescription = null,
                        tint = next.ink,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        if (options.isEmpty()) {
            item(key = "empty-options") {
                Text(
                    stringResource(Res.string.next_settings_no_matches),
                    style = NextType.body,
                    color = next.muted,
                    modifier = Modifier.padding(GUTTER),
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    item: SettingItem,
    expanded: Boolean,
    onActivate: (SettingItem) -> Unit,
    onCommitText: (SettingItem, String) -> Unit,
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(expanded) { if (expanded) bringIntoView.bringIntoView() }
    val action =
        if (item.kind == SettingKind.INFO) {
            Modifier
        } else {
            Modifier
                .nextClickable(
                    role = if (item.kind == SettingKind.TOGGLE) Role.Switch else Role.Button,
                    onClick = { onActivate(item) },
                ).semantics {
                    if (item.kind == SettingKind.TOGGLE) toggleableState = ToggleableState(item.isOn())
                }
        }
    Column(Modifier.bringIntoViewRequester(bringIntoView)) {
        BoxWithConstraints(Modifier.fillMaxWidth().then(action).padding(horizontal = GUTTER, vertical = 10.dp)) {
            val valueStyle = NextType.bodyDense.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
            val measurer = rememberTextMeasurer()
            val hasValue = item.kind != SettingKind.TOGGLE && item.value.isNotBlank()
            val hasChevron = item.kind == SettingKind.CHOICE || item.kind == SettingKind.TEXT
            val labelWidth = measurer.measure(item.label, NextType.body, maxLines = 1, softWrap = false).size.width
            val valueWidth =
                if (hasValue) {
                    measurer
                        .measure(
                            item.value,
                            valueStyle,
                            maxLines = 1,
                            softWrap = false,
                        ).size.width
                } else {
                    0
                }
            val trailingWidth = with(LocalDensity.current) { (if (hasChevron) 32.dp else 12.dp).roundToPx() }
            val stackValue = hasValue && labelWidth + valueWidth + trailingWidth > constraints.maxWidth
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        item.label,
                        style = NextType.body,
                        color = if (item.destructive) nextDestructiveRed else next.ink,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.kind == SettingKind.TOGGLE) {
                        Box(Modifier.height(32.dp).clearAndSetSemantics {}) {
                            NextToggle(checked = item.isOn(), onCheckedChange = null)
                        }
                    } else {
                        if (hasValue && !stackValue) {
                            Text(
                                item.value,
                                style = valueStyle,
                                color = if (item.destructive) nextDestructiveRed else next.muted,
                            )
                        }
                        if (hasChevron) SettingsChevron()
                    }
                }
                if (stackValue) {
                    Text(
                        item.value,
                        style = valueStyle,
                        color = if (item.destructive) nextDestructiveRed else next.muted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (item.hint != null && item.kind != SettingKind.TEXT) {
                    Text(
                        item.hint,
                        style = NextType.footnote.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        color = next.muted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        if (item.copyable && item.text.isNotBlank()) {
            SelectionContainer {
                Text(
                    item.text,
                    style = NextType.footnote,
                    color = next.ink,
                    modifier = Modifier.padding(start = GUTTER, end = GUTTER, bottom = 14.dp),
                )
            }
        }
        if (expanded) {
            if (item.hint != null) {
                Text(
                    item.hint,
                    style = NextType.footnote,
                    color = next.muted,
                    modifier = Modifier.padding(start = GUTTER, end = GUTTER, bottom = 10.dp),
                )
            }
            SettingTextEditor(item, onCommitText)
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
