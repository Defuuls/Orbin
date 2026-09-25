package com.orbin.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_settings_done
import com.orbin.uinext.resources.next_settings_title
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
)

enum class SettingKind {
    TOGGLE,
    CHOICE,
    TEXT,
    ACTION,
    INFO,
}

@Composable
fun SettingsScreen(
    groups: List<Pair<String, List<SettingItem>>>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    expandedId: String? = null,
    showRail: Boolean = true,
    onActivate: (SettingItem) -> Unit = {},
    onSelectOption: (SettingItem, Int) -> Unit = { _, _ -> },
    onCommitText: (SettingItem, String) -> Unit = { _, _ -> },
) {
    val state = rememberLazyListState()
    LaunchedEffect(expandedId, groups) {
        val targetId = expandedId ?: return@LaunchedEffect
        val groupIndex = groups.indexOfFirst { (_, rows) -> rows.any { it.id == targetId } }
        if (groupIndex >= 0) {
            val lazyItemIndex = 1 + groupIndex
            state.scrollToItem(lazyItemIndex)
        }
    }
    val settingsTitle = stringResource(Res.string.next_settings_title)
    val showCompactTitle by remember {
        derivedStateOf {
            state.firstVisibleItemIndex > 0 ||
                state.firstVisibleItemScrollOffset > 64
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        NextScaffold(
            // Settings is visited from the Feed header, not a tab, so it draws no tab chrome.
            where = settingsTitle.takeIf { showRail },
            modifier = Modifier.fillMaxSize(),
        ) { bottomPad ->
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize().contentInsets().imePadding(),
                contentPadding =
                    PaddingValues(
                        bottom = bottomPad.calculateBottomPadding(),
                        top = 4.dp + (if (showCompactTitle) COMPACT_TITLE_CLEARANCE else 0.dp),
                    ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    ScreenTitle(
                        text = stringResource(Res.string.next_settings_title),
                        subtitle = subtitle,
                    )
                }
                groups.forEachIndexed { groupIndex, (heading, rows) ->
                    item(key = "group:$groupIndex") {
                        GroupedSection(header = heading.takeIf { it.isNotBlank() }) {
                            rows.forEachIndexed { index, item ->
                                SettingRow(
                                    item = item,
                                    expanded = item.id == expandedId,
                                    onActivate = onActivate,
                                    onSelectOption = onSelectOption,
                                    onCommitText = onCommitText,
                                )
                                if (index < rows.lastIndex) GroupedDivider()
                            }
                        }
                    }
                }
                item { Gap(8) }
            }
        }
        CompactTitleBar(
            title = settingsTitle,
            visible = showCompactTitle,
            modifier = Modifier.align(Alignment.TopCenter),
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
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (item.kind == SettingKind.INFO) {
                            Modifier
                        } else {
                            Modifier.nextClickable(role = Role.Button, onClick = { onActivate(item) })
                        },
                    ).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    fontSize = 15.5.sp,
                    letterSpacing = (-0.1).sp,
                    color = next.ink,
                )
                if (item.hint != null && item.kind != SettingKind.TEXT) {
                    Text(
                        text = item.hint,
                        fontSize = 12.5.sp,
                        color = next.muted,
                        // Large text must wrap mid-sentence rather than clip.
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            WidthSpacer(12)
            if (item.kind == SettingKind.TOGGLE) {
                NextToggle(checked = item.isOn(), onCheckedChange = { onActivate(item) })
            } else {
                SelectionContainer {
                    Text(
                        text = item.value,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.isOn()) next.accent else next.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (!expanded) return@Column
        if (item.kind == SettingKind.TEXT && item.hint != null) {
            Text(
                text = item.hint,
                fontSize = 13.sp,
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
                            accent = index == item.selected,
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
                Text(text = item.value, fontSize = 15.sp, color = next.faint, maxLines = 1)
            }
            // BasicTextField already supports selection; the value Text above keeps
            // SelectionContainer for long-press copy of the committed value.
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = next.ink),
                cursorBrush = SolidColor(next.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
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

private fun SettingItem.isOn(): Boolean = kind == SettingKind.TOGGLE && value != OFF_LABEL

const val ON_LABEL = "On"
const val OFF_LABEL = "Off"
