package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
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
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.orbin.uinext.components.InlineAction
import com.orbin.uinext.components.LocalOrbinNavigationAtSide
import com.orbin.uinext.components.OrbinLogoMark
import com.orbin.uinext.components.ReaderAppBarTags
import com.orbin.uinext.components.ReaderIcons
import com.orbin.uinext.components.ReaderSettingsSwitch
import com.orbin.uinext.components.SchematicSearch
import com.orbin.uinext.components.SettingsGroupCard
import com.orbin.uinext.components.SettingsIconTile
import com.orbin.uinext.components.bottomInset
import com.orbin.uinext.components.next
import com.orbin.uinext.components.nextClickable
import com.orbin.uinext.components.readerBackSwipe
import com.orbin.uinext.components.readerKeyboardShortcuts
import com.orbin.uinext.components.readerTextEntry
import com.orbin.uinext.components.settingsStyle
import com.orbin.uinext.components.withReaderFont
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_settings_done
import com.orbin.uinext.resources.next_settings_no_matches
import com.orbin.uinext.resources.next_settings_search
import com.orbin.uinext.resources.reader_back
import com.orbin.uinext.resources.reader_settings_project
import com.orbin.uinext.resources.reader_settings_search_options
import com.orbin.uinext.resources.reader_settings_title
import com.orbin.uinext.screens.ReaderSettingAnchor
import com.orbin.uinext.screens.ReaderSettingsChoiceMark
import com.orbin.uinext.screens.SETTINGS_SECTION_ORDER
import com.orbin.uinext.screens.SettingsFilterEditor
import com.orbin.uinext.screens.SettingsSegments
import com.orbin.uinext.screens.filterSettings
import com.orbin.uinext.screens.preferenceSummaryStyle
import com.orbin.uinext.screens.preferenceTitleStyle
import com.orbin.uinext.screens.settingsDestinationLabel
import com.orbin.uinext.screens.settingsDirectorySummary
import com.orbin.uinext.screens.settingsSectionDestinations
import com.orbin.uinext.screens.settingsSectionId
import com.orbin.uinext.screens.settingsSectionLabels
import com.orbin.uinext.screens.settingsSections
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
    val destructive: Boolean = false,
    /** Secret values are never included in search or pre-filled by the platform host. */
    val secret: Boolean = false,
    val copyable: Boolean = false,
    /** Boolean state stays independent of the displayed, possibly localized value. */
    val checked: Boolean? = null,
    val filterRules: List<SettingFilterRule>? = null,
)

/** Plain presentation data; platform adapters map these rules to their persisted domain models. */
data class SettingFilterRule(
    val kind: String = "TEXT",
    val pattern: String,
    val regex: Boolean = false,
)

enum class SettingKind {
    TOGGLE,
    CHOICE,
    TEXT,
    ACTION,
    INFO,
}

/** One searchable settings page; the platform hosts retain ownership of every persisted action. */
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
    onCommitFilters: (SettingItem, List<SettingFilterRule>) -> Unit = { _, _ -> },
    onBack: (() -> Unit)? = null,
    onNavigate: ((String) -> Unit)? = null,
    enabledSiteCount: Int? = null,
    bottomBar: (@Composable () -> Unit)? = null,
) {
    val rootState = rememberLazyListState()
    val searchState = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    val allRows = remember(groups) { groups.flatMap { it.second } }
    val sections = remember(groups) { settingsSections(groups) }
    val labels = settingsSectionLabels()
    var choiceId by rememberSaveable {
        mutableStateOf(
            allRows.firstOrNull { it.id == expandedId && it.kind == SettingKind.CHOICE && !it.hasSegments() }?.id,
        )
    }
    var initialTarget by rememberSaveable { mutableStateOf(expandedId) }
    val choice = allRows.firstOrNull { it.id == choiceId }
    val fullPageChoice = choice?.takeIf { it.id == "playbackScope" || it.options.size > 8 }
    val localizedGroups = sections.map { (id, rows) -> labels.getValue(id) to rows }
    // Keep the platform's original headings searchable as well as the visible section names.
    val visibleSections =
        remember(groups, localizedGroups, query) {
            if (query.isBlank()) {
                sections
            } else {
                val matches =
                    (filterSettings(groups, query) + filterSettings(localizedGroups, query))
                        .flatMap { it.second }
                        .toSet()
                sections
                    .mapNotNull { (id, rows) ->
                        rows.filter { it in matches }.takeIf { it.isNotEmpty() }?.let { id to it }
                    }.toMap()
            }
        }
    val visibleIds =
        SETTINGS_SECTION_ORDER.filter { id ->
            id in visibleSections ||
                (query.isBlank() && onNavigate != null && settingsSectionDestinations(id).isNotEmpty())
        }
    LaunchedEffect(initialTarget, sections) {
        val target = initialTarget ?: return@LaunchedEffect
        val index = visibleIds.indexOf(settingsSectionId(target))
        if (index >= 0) rootState.scrollToItem(index + 1) // The brand hero is the first item.
        initialTarget = null
    }
    val closeLocal: () -> Unit = {
        if (choiceId != null) {
            choiceId = null
        } else {
            query = ""
            searchVisible = false
        }
    }
    val localBack = choiceId != null || query.isNotBlank() || searchVisible
    val showBottomBar = bottomBar != null && !localBack && !LocalOrbinNavigationAtSide.current
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = localBack,
        onBackCompleted = closeLocal,
    )
    val activate: (SettingItem) -> Unit = { item ->
        if (item.kind == SettingKind.CHOICE && !item.hasSegments()) choiceId = item.id
        onActivate(item)
    }
    val uriHandler = LocalUriHandler.current
    val contact = allRows.firstOrNull { it.id == "contact" && it.kind == SettingKind.ACTION }
    val colors = settingsStyle
    val currentState = if (searchVisible) searchState else rootState
    val searchFocus = remember { FocusRequester() }
    val title = fullPageChoice?.label ?: stringResource(Res.string.reader_settings_title)
    Column(
        modifier
            .readerKeyboardShortcuts(
                onBack = if (localBack) closeLocal else onBack,
                onSearch =
                    if (fullPageChoice ==
                        null
                    ) {
                        {
                            searchState.requestScrollToItem(0)
                            searchVisible = true
                        }
                    } else {
                        null
                    },
            ).readerBackSwipe(if (localBack) closeLocal else onBack)
            .fillMaxSize()
            .background(colors.background)
            .testTag("reader-settings-root"),
    ) {
        SettingsNavigationBar(
            title = title,
            onBack = if (localBack) closeLocal else onBack,
            onSearch =
                if (fullPageChoice == null && !searchVisible) {
                    {
                        searchState.requestScrollToItem(0)
                        searchVisible = true
                    }
                } else {
                    null
                },
            scrolled = currentState.firstVisibleItemIndex > 0 || currentState.firstVisibleItemScrollOffset > 6,
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            val contentModifier = Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxSize()
            if (fullPageChoice != null) {
                SettingsChoiceList(fullPageChoice, contentModifier) { index ->
                    onSelectOption(fullPageChoice, index)
                    choiceId = null
                }
            } else {
                LazyColumn(
                    state = currentState,
                    modifier = contentModifier.imePadding().testTag("settings-list"),
                    contentPadding =
                        PaddingValues(
                            top = 12.dp,
                            bottom =
                                24.dp + if (showBottomBar) 0.dp else bottomInset(),
                        ),
                ) {
                    if (searchVisible) {
                        item(key = "search") {
                            LaunchedEffect(Unit) { searchFocus.requestFocus() }
                            val searchLabel = stringResource(Res.string.next_settings_search)
                            SchematicSearch(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = searchLabel,
                                shape = RoundedCornerShape(22.dp),
                                modifier =
                                    Modifier
                                        .focusRequester(
                                            searchFocus,
                                        ).padding(horizontal = 16.dp, vertical = 8.dp)
                                        .semantics {
                                            contentDescription = searchLabel
                                        },
                            )
                        }
                    } else {
                        item(key = "brand") {
                            SettingsBrandHero(
                                subtitle = subtitle ?: allRows.firstOrNull { it.id == "version" }?.value,
                                onProject = {
                                    if (contact !=
                                        null
                                    ) {
                                        onActivate(contact)
                                    } else {
                                        uriHandler.openUri("https://github.com/Defuuls/Orbin")
                                    }
                                },
                            )
                        }
                    }
                    if (visibleIds.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(Res.string.next_settings_no_matches),
                                style = preferenceTitleStyle,
                                color = colors.muted,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    visibleIds.forEach { id ->
                        item(key = "section:$id") {
                            val rows = visibleSections[id].orEmpty()
                            val destinations =
                                if (onNavigate != null &&
                                    query.isBlank()
                                ) {
                                    settingsSectionDestinations(id)
                                } else {
                                    emptyList()
                                }
                            SettingsSection(labels.getValue(id), id) {
                                destinations.forEachIndexed { index, destination ->
                                    SettingsNavigationRow(
                                        destination = destination,
                                        value = if (destination == "boards") enabledSiteCount?.toString() else null,
                                        divider = index < destinations.lastIndex || rows.isNotEmpty(),
                                        onClick = { onNavigate?.invoke(destination) },
                                    )
                                }
                                rows.forEachIndexed { index, item ->
                                    SettingRow(
                                        item = item,
                                        expanded = item.id == expandedId && item.kind == SettingKind.TEXT,
                                        requested = item.id == expandedId,
                                        divider = index < rows.lastIndex,
                                        onActivate = activate,
                                        onCommitText = onCommitText,
                                        onCommitFilters = onCommitFilters,
                                        choiceVisible = item.id == choiceId,
                                        onSelectOption = { selected ->
                                            onSelectOption(item, selected)
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
        if (showBottomBar) Box(Modifier.fillMaxWidth().testTag("settings-bottom-navigation")) { bottomBar.invoke() }
    }
}

@Composable
private fun SettingsSection(
    label: String,
    id: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 22.dp)) {
        Text(
            label.uppercase(),
            style = OrbinType.meta.withReaderFont().copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp),
            color = settingsStyle.muted,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp).semantics { heading() },
        )
        SettingsGroupCard(Modifier.fillMaxWidth().testTag("settings-section:$id"), content)
    }
}

@Composable
private fun SettingsNavigationBar(
    title: String,
    onBack: (() -> Unit)?,
    onSearch: (() -> Unit)?,
    scrolled: Boolean,
) {
    val colors = settingsStyle
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .testTag(ReaderAppBarTags.BAR)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(
                    min = 62.dp,
                ).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onBack != null) SettingsBarAction(ReaderIcons.Back, stringResource(Res.string.reader_back), onBack)
            Text(
                title,
                style =
                    OrbinType.appBarTitle.withReaderFont().copy(
                        fontSize = 21.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.6).sp,
                    ),
                color = colors.text,
                modifier = Modifier.weight(1f).testTag(ReaderAppBarTags.TITLE).semantics { heading() },
            )
            if (onSearch !=
                null
            ) {
                SettingsBarAction(ReaderIcons.Search, stringResource(Res.string.next_settings_search), onSearch)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(
                    1.dp,
                ).background(if (scrolled) colors.accent.copy(alpha = 0.18f) else Color.Transparent),
        )
    }
}

@Composable
private fun SettingsBarAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val colors = settingsStyle
    Box(
        Modifier.size(48.dp).nextClickable(role = Role.Button, onClick = onClick).semantics {
            contentDescription =
                description
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(colors.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = colors.accentInk, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SettingsBrandHero(
    subtitle: String?,
    onProject: () -> Unit,
) {
    val colors = settingsStyle
    val shape = RoundedCornerShape(28.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 22.dp)) {
        val stacked = maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.5f
        val brand: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Orbin",
                    style =
                        OrbinType.appBarTitle.withReaderFont().copy(
                            fontSize = 24.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.7).sp,
                        ),
                    color = colors.text,
                    modifier = Modifier.semantics { heading() },
                )
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = preferenceSummaryStyle, color = colors.muted)
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            colors.accent.copy(alpha = 0.12f),
                        ).nextClickable(role = Role.Button, onClick = onProject)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(ReaderIcons.Globe, null, tint = colors.accentInk, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(Res.string.reader_settings_project),
                        style = preferenceSummaryStyle.copy(fontWeight = FontWeight.Bold),
                        color = colors.accentInk,
                    )
                    Icon(ReaderIcons.ChevronRight, null, tint = colors.accentInk, modifier = Modifier.size(14.dp))
                }
            }
        }
        val heroModifier =
            Modifier
                .fillMaxWidth()
                .testTag("settings-brand-hero")
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            colors.accent.copy(alpha = if (next.dark) 0.22f else 0.35f),
                            colors.secondary.copy(alpha = if (next.dark) 0.18f else 0.28f),
                        ),
                    ),
                ).border(1.dp, colors.accent.copy(alpha = 0.2f), shape)
                .padding(18.dp)
        if (stacked) {
            Column(heroModifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OrbinLogoMark(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)))
                brand()
            }
        } else {
            Row(
                heroModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OrbinLogoMark(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)))
                Box(Modifier.weight(1f)) { brand() }
            }
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    destination: String,
    value: String?,
    divider: Boolean,
    onClick: () -> Unit,
) {
    val colors = settingsStyle
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .nextClickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsIconTile(settingsRowIcon(destination), settingsRowTint(destination))
            Column(Modifier.weight(1f)) {
                Text(settingsDestinationLabel(destination), style = preferenceTitleStyle, color = colors.text)
                Text(
                    value ?: settingsDirectorySummary(destination),
                    style = preferenceSummaryStyle,
                    color = colors.muted,
                )
            }
            Icon(ReaderIcons.ChevronRight, null, tint = colors.muted, modifier = Modifier.size(18.dp))
        }
        if (divider) SettingsRowDivider()
    }
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
    val colors = settingsStyle
    LazyColumn(
        modifier = modifier.imePadding().testTag("settings-list").selectableGroup(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp + bottomInset()),
    ) {
        if (item.id == "playbackScope" || item.options.size > 8) {
            item(key = "search-options") {
                SchematicSearch(
                    optionQuery,
                    { optionQuery = it },
                    stringResource(Res.string.reader_settings_search_options),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(22.dp),
                )
            }
        }
        if (item.hint != null) {
            item(key = "hint") {
                Text(
                    item.hint,
                    style = preferenceSummaryStyle,
                    color = colors.muted,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                )
            }
        }
        items(options, key = { it.index }) { (index, option) ->
            SettingsGroupCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = index == item.selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(index) },
                        ).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(option, style = preferenceTitleStyle, color = colors.text, modifier = Modifier.weight(1f))
                    ReaderSettingsChoiceMark(selected = index == item.selected)
                }
            }
        }
        if (options.isEmpty()) {
            item(key = "empty-options") {
                Text(
                    stringResource(Res.string.next_settings_no_matches),
                    style = preferenceTitleStyle,
                    color = colors.muted,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    item: SettingItem,
    expanded: Boolean,
    requested: Boolean,
    divider: Boolean,
    onActivate: (SettingItem) -> Unit,
    onCommitText: (SettingItem, String) -> Unit,
    onCommitFilters: (SettingItem, List<SettingFilterRule>) -> Unit,
    choiceVisible: Boolean = false,
    onSelectOption: (Int) -> Unit = {},
    onDismissChoice: () -> Unit = {},
) {
    val colors = settingsStyle
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(expanded, requested) { if (expanded || requested) bringIntoView.bringIntoView() }
    val segments = item.hasSegments()
    val action =
        if (item.kind == SettingKind.INFO || segments) {
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
            BoxWithConstraints(
                Modifier
                    .testTag("reader-setting-row-${item.id}")
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .then(action)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                val stacked = maxWidth < 292.dp || LocalDensity.current.fontScale >= 1.3f
                val trailing: @Composable () -> Unit = {
                    when {
                        item.kind == SettingKind.TOGGLE ->
                            Box(
                                Modifier.clearAndSetSemantics {},
                            ) { ReaderSettingsSwitch(item.isOn()) }
                        segments ->
                            SettingsSegments(
                                item,
                                onSelectOption,
                                Modifier.widthIn(max = if (stacked) 400.dp else 140.dp),
                            )
                        item.kind != SettingKind.INFO ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (item.value.isNotBlank()) {
                                    Text(
                                        item.value,
                                        style = preferenceSummaryStyle.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (item.destructive) colors.danger else colors.accentInk,
                                        modifier = Modifier.widthIn(max = if (stacked) 400.dp else 140.dp),
                                    )
                                }
                                if (item.kind == SettingKind.CHOICE || item.kind == SettingKind.TEXT) {
                                    Icon(
                                        if (item.kind == SettingKind.CHOICE &&
                                            item.options.size <= 8 &&
                                            item.id != "playbackScope"
                                        ) {
                                            ReaderIcons.ChevronDown
                                        } else {
                                            ReaderIcons.ChevronRight
                                        },
                                        null,
                                        tint = colors.muted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingsIconTile(
                            settingsRowIcon(item.id),
                            if (item.destructive) colors.danger else settingsRowTint(item.id),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.label,
                                style = preferenceTitleStyle,
                                color = if (item.destructive) colors.danger else colors.text,
                            )
                            if (item.kind == SettingKind.INFO &&
                                item.value.isNotBlank()
                            ) {
                                Text(item.value, style = preferenceSummaryStyle, color = colors.muted)
                            }
                            if (item.hint != null &&
                                item.kind != SettingKind.TEXT
                            ) {
                                Text(item.hint, style = preferenceSummaryStyle, color = colors.muted)
                            }
                        }
                        if (!stacked) trailing()
                    }
                    if (stacked &&
                        item.kind != SettingKind.INFO
                    ) {
                        Box(Modifier.padding(start = 44.dp, top = 8.dp)) { trailing() }
                    }
                }
            }
            if (item.copyable && item.text.isNotBlank()) {
                SelectionContainer {
                    Text(
                        item.text,
                        style = preferenceSummaryStyle,
                        color = colors.text,
                        modifier = Modifier.padding(start = 58.dp, end = 14.dp, bottom = 11.dp),
                    )
                }
            }
            if (expanded) {
                if (item.hint !=
                    null
                ) {
                    Text(
                        item.hint,
                        style = preferenceSummaryStyle,
                        color = colors.muted,
                        modifier = Modifier.padding(start = 58.dp, end = 14.dp, bottom = 10.dp),
                    )
                }
                if (item.filterRules != null) {
                    SettingsFilterEditor(item, onCommitFilters)
                } else {
                    SettingTextEditor(item, onCommitText)
                }
            }
            if (divider) SettingsRowDivider()
        }
    }
}

@Composable
private fun SettingsRowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 58.dp, end = 14.dp)
            .height(1.dp)
            .background(settingsStyle.border),
    )
}

internal fun settingsRowIcon(id: String): ImageVector =
    when (id) {
        "themeMode" -> ReaderIcons.Palette
        "amoled" -> ReaderIcons.TrueBlack
        "boards", "catalogMode", "feedColumns" -> ReaderIcons.Boards
        "favorites" -> ReaderIcons.Heart
        "openThreadComments" -> ReaderIcons.Chat
        "playbackScope" -> ReaderIcons.Globe
        "autoplay", "startMuted", "loopVideos",
        "preloadNextClip", "inheritPlayback", "resetPlayback",
        -> ReaderIcons.Play
        "hideNsfw", "coverViolent" -> ReaderIcons.Hide
        "biometric" -> ReaderIcons.Lock
        "screenPrivacy", "privacy", "strictDns" -> ReaderIcons.Shield
        "dohProvider", "contact" -> ReaderIcons.Globe
        "history", "saveSearchHistory", "saveBrowsingHistory" -> ReaderIcons.Clock
        "downloads" -> ReaderIcons.Download
        "clearActivity", "clearImageCache", "videoCacheLimit", "clearVideoCache" -> ReaderIcons.Storage
        "exportBackup" -> ReaderIcons.Share
        "importBackup" -> ReaderIcons.Download
        "checkUpdates", "updateOnLaunch" -> ReaderIcons.Refresh
        "help" -> ReaderIcons.Help
        "version" -> ReaderIcons.Info
        else -> ReaderIcons.Settings
    }

@Composable
private fun settingsRowTint(id: String): Color =
    when (if (id in setOf("boards", "favorites", "history", "downloads", "help")) id else settingsSectionId(id)) {
        "theme", "threads" -> settingsStyle.accent
        "boards", "favorites", "backup" -> if (next.dark) Color(0xFF60A5FA) else Color(0xFF2563EB)
        "media" -> if (next.dark) Color(0xFFF472B6) else Color(0xFFDB2777)
        "hidden" -> if (next.dark) Color(0xFFFB923C) else Color(0xFFEA580C)
        "privacy" -> if (next.dark) Color(0xFF34D399) else Color(0xFF059669)
        "history" -> if (next.dark) Color(0xFF818CF8) else Color(0xFF4F46E5)
        "storage", "downloads" -> if (next.dark) Color(0xFF2DD4BF) else Color(0xFF0D9488)
        else -> settingsStyle.secondary
    }

@Composable
private fun SettingTextEditor(
    item: SettingItem,
    onCommitText: (SettingItem, String) -> Unit,
) {
    var draft by remember(item.id, item.text) { mutableStateOf(item.text) }
    val focus = remember { FocusRequester() }
    val commit = { onCommitText(item, draft) }
    val colors = settingsStyle
    LaunchedEffect(item.id) { focus.requestFocus() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                if (draft.isEmpty()) Text(item.value, style = preferenceTitleStyle, color = colors.muted)
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = preferenceTitleStyle.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = if (item.secret) KeyboardType.Password else KeyboardType.Unspecified,
                            imeAction = ImeAction.Done,
                        ),
                    visualTransformation =
                        if (item.secret) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier.readerTextEntry().fillMaxWidth().focusRequester(focus),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        }
        InlineAction(label = stringResource(Res.string.next_settings_done), accent = true, onClick = commit)
    }
}

private fun SettingItem.hasSegments(): Boolean =
    kind == SettingKind.CHOICE && options.size == 2 && id != "playbackScope"

private fun SettingItem.isOn(): Boolean = checked ?: (kind == SettingKind.TOGGLE && value != OFF_LABEL)

const val ON_LABEL = "On"
const val OFF_LABEL = "Off"
