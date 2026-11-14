package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_settings_done
import com.orbin.uinext.resources.next_settings_no_matches
import com.orbin.uinext.resources.next_settings_search
import com.orbin.uinext.resources.reader_settings_downloads
import com.orbin.uinext.resources.reader_settings_search_options
import com.orbin.uinext.resources.reader_settings_title
import com.orbin.uinext.resources.reader_settings_view_history
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType
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
    /** Hosts supply the app navigation for the directory; local settings pages keep their Back action. */
    bottomBar: (@Composable () -> Unit)? = null,
) {
    val state = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
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
    val fullPageChoice = choice?.takeIf { it.id == "playbackScope" || it.options.size > 8 }
    val title = fullPageChoice?.label ?: page?.let { labels[it] } ?: stringResource(Res.string.reader_settings_title)
    val visibleGroups =
        remember(groups, query, page) {
            when {
                query.isNotBlank() -> filterSettings(groups, query)
                page != null -> listOf("" to pages[page].orEmpty())
                else -> emptyList()
            }
        }
    val closeLocalPage: () -> Unit = {
        if (choiceId != null) {
            choiceId = null
        } else if (page != null) {
            page = null
            state.requestScrollToItem(0)
        } else {
            query = ""
            searchVisible = false
        }
    }
    val localBack = choiceId != null || page != null || query.isNotBlank() || searchVisible
    val showBottomBar = bottomBar != null && !localBack
    val bottomPadding = if (showBottomBar) 24.dp else 24.dp + bottomInset()
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
    Column(modifier = modifier.fillMaxSize().background(reader.background).testTag("reader-settings-root")) {
        SettingsNavigationBar(title, backAction, if (page == null) ({ searchVisible = true }) else null)
        if (fullPageChoice != null) {
            SettingsChoiceList(fullPageChoice, modifier = Modifier.weight(1f)) { index ->
                onSelectOption(fullPageChoice, index)
                choiceId = null
            }
        } else {
            LazyColumn(
                state = state,
                modifier =
                    Modifier
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .imePadding()
                        .testTag("settings-list"),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
            ) {
                if (page == null && searchVisible) {
                    item(key = "search") {
                        val searchLabel = stringResource(Res.string.next_settings_search)
                        Column(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
                            SchematicSearch(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = searchLabel,
                                shape = RoundedCornerShape(OrbinRadius.search),
                                modifier =
                                    Modifier.padding(horizontal = GUTTER).semantics {
                                        contentDescription =
                                            searchLabel
                                    },
                            )
                            if (subtitle != null) {
                                Text(
                                    subtitle,
                                    style = OrbinType.footnote,
                                    color = next.muted,
                                    modifier = Modifier.padding(GUTTER),
                                )
                            }
                        }
                    }
                }
                if (page == null && query.isBlank()) {
                    settingsDirectory(pages.keys, onNavigate != null).forEach { (heading, entries) ->
                        item(key = "directory:$heading") {
                            SettingsSection(directorySectionLabel(heading), "directory:$heading") {
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
                                            state.requestScrollToItem(0)
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
                                style = OrbinType.body,
                                color = next.muted,
                                modifier = Modifier.padding(GUTTER),
                            )
                        }
                    }
                    visibleGroups.forEachIndexed { index, (heading, rows) ->
                        item(key = "group:$index") {
                            SettingsSection(heading.ifBlank { page?.let { labels[it] }.orEmpty() }, "group:$index") {
                                if (page == "history" && onNavigate != null && query.isBlank()) {
                                    SettingsDirectoryRow(
                                        "history",
                                        stringResource(Res.string.reader_settings_view_history),
                                    ) {
                                        onNavigate("history")
                                    }
                                }
                                if (page == "storage" && onNavigate != null && query.isBlank()) {
                                    SettingsDirectoryRow(
                                        "downloads",
                                        stringResource(Res.string.reader_settings_downloads),
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
                                        choiceVisible = item.id == choiceId,
                                        onSelectOption = { index ->
                                            onSelectOption(item, index)
                                            choiceId = null
                                        },
                                        onDismissChoice = { choiceId = null },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showBottomBar) {
            Box(Modifier.fillMaxWidth().testTag("settings-bottom-navigation")) { bottomBar.invoke() }
        }
    }
}

@Composable
private fun SettingsSection(
    label: String,
    id: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 8.dp)) {
        if (label.isNotBlank()) SettingsSectionHeading(label)
        OrbinCard(modifier = Modifier.fillMaxWidth().testTag("settings-section:$id"), content = content)
    }
}

@Composable
private fun SettingsNavigationBar(
    title: String,
    onBack: (() -> Unit)?,
    onSearch: (() -> Unit)?,
) {
    ReaderAppBar(
        title = title,
        navigation = if (onBack != null) ReaderNavigation.BACK else ReaderNavigation.NONE,
        onNavigation = { onBack?.invoke() },
        actions =
            onSearch
                ?.let {
                    listOf(
                        ReaderAction(ReaderIcons.Search, stringResource(Res.string.next_settings_search), it),
                    )
                }.orEmpty(),
    )
}

@Composable
private fun SettingsSectionHeading(label: String) {
    Text(
        text = label,
        style = preferenceTitleStyle.copy(fontWeight = FontWeight.SemiBold),
        color = reader.accent,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun SettingsDirectoryRow(
    destination: String,
    label: String,
    value: String? = null,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .nextClickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = settingsDirectoryIcon(destination),
                contentDescription = null,
                tint = reader.accent,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(label, style = preferenceTitleStyle, color = preferencePrimaryColor)
                Text(
                    value ?: settingsDirectorySummary(destination),
                    style = preferenceSummaryStyle,
                    color = reader.inkSecondary,
                )
            }
            Icon(
                imageVector = ReaderIcons.ChevronRight,
                contentDescription = null,
                tint = reader.accent,
                modifier = Modifier.size(20.dp),
            )
        }
        ReaderHairline(Modifier.padding(horizontal = 16.dp))
    }
}

private fun settingsDirectoryIcon(destination: String): androidx.compose.ui.graphics.vector.ImageVector =
    when (destination) {
        "appearance" -> ReaderIcons.Palette
        "playback" -> ReaderIcons.Play
        "boards" -> ReaderIcons.Boards
        "favorites" -> ReaderIcons.Heart
        "privacy" -> ReaderIcons.Shield
        "storage" -> ReaderIcons.Storage
        "about" -> ReaderIcons.Info
        else -> ReaderIcons.Settings
    }

@Composable
private fun SettingsChoiceList(
    item: SettingItem,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    var optionQuery by rememberSaveable(item.id) { mutableStateOf("") }
    val options =
        remember(item.options, optionQuery) {
            item.options.withIndex().filter { it.value.contains(optionQuery.trim(), ignoreCase = true) }
        }
    LazyColumn(
        modifier =
            modifier
                .fillMaxWidth()
                .background(reader.background)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
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
                    stringResource(Res.string.reader_settings_search_options),
                    modifier = Modifier.padding(horizontal = GUTTER, vertical = 8.dp),
                    shape = RoundedCornerShape(OrbinRadius.search),
                )
            }
        }
        if (item.hint != null) {
            item(key = "hint") {
                Text(
                    item.hint,
                    style = preferenceSummaryStyle,
                    color = reader.inkSecondary,
                    modifier = Modifier.padding(horizontal = GUTTER, vertical = 8.dp),
                )
            }
        }
        items(options, key = { it.index }) { (index, option) ->
            OrbinCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 4.dp),
                elevated = false,
            ) {
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
                    Text(
                        option,
                        style = preferenceTitleStyle,
                        color = preferencePrimaryColor,
                        modifier = Modifier.weight(1f),
                    )
                    ReaderSettingsChoiceMark(selected = index == item.selected)
                }
            }
        }
        if (options.isEmpty()) {
            item(key = "empty-options") {
                Text(
                    stringResource(Res.string.next_settings_no_matches),
                    style = OrbinType.body,
                    color = reader.inkSecondary,
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
    choiceVisible: Boolean = false,
    onSelectOption: (Int) -> Unit = {},
    onDismissChoice: () -> Unit = {},
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
    ReaderSettingAnchor(item, choiceVisible, onSelectOption, onDismissChoice) {
        Column(Modifier.bringIntoViewRequester(bringIntoView)) {
            Row(
                Modifier
                    .testTag("reader-setting-row-${item.id}")
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .then(action)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.label,
                        style = preferenceTitleStyle,
                        color = if (item.destructive) nextDestructiveRed else preferencePrimaryColor,
                    )
                    if (item.kind != SettingKind.TOGGLE && item.value.isNotBlank()) {
                        Text(
                            item.value,
                            style = preferenceSummaryStyle,
                            color = if (item.destructive) nextDestructiveRed else reader.inkSecondary,
                        )
                    }
                    if (item.hint != null && item.kind != SettingKind.TEXT) {
                        Text(item.hint, style = preferenceSummaryStyle, color = reader.inkSecondary)
                    }
                }
                if (item.kind == SettingKind.TOGGLE) {
                    Box(Modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                        ReaderSettingsSwitch(checked = item.isOn())
                    }
                } else if (item.kind == SettingKind.CHOICE) {
                    Icon(
                        imageVector = if (item.options.size > 8) ReaderIcons.ChevronRight else ReaderIcons.ChevronDown,
                        contentDescription = null,
                        tint = reader.inkSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (item.copyable && item.text.isNotBlank()) {
                SelectionContainer {
                    Text(
                        item.text,
                        style = preferenceSummaryStyle,
                        color = preferencePrimaryColor,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    )
                }
            }
            if (expanded) {
                if (item.hint != null) {
                    Text(
                        item.hint,
                        style = preferenceSummaryStyle,
                        color = reader.inkSecondary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    )
                }
                SettingTextEditor(item, onCommitText)
            }
            ReaderHairline(Modifier.padding(horizontal = 16.dp))
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
                    style = OrbinType.body,
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
                textStyle = OrbinType.body.copy(color = next.ink),
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
