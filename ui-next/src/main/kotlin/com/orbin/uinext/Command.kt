package com.orbin.uinext

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Command(
    val label: String,
    val kind: String,
    val hint: String? = null,
    val id: String = "$kind:$label",
)

@Composable
fun CommandSheet(
    query: String,
    results: List<Command>,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onSelect: (Command) -> Unit = {},
    onDismiss: () -> Unit = {},
    placeholder: String = stringResource(R.string.next_command_placeholder),
    emptyLabel: String = stringResource(R.string.next_command_empty),
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val sheetInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    val sheetFraction = commandSheetFraction(results.size)
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    ),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(sheetFraction)
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(next.background)
                    .windowInsetsPadding(sheetInsets),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 38.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(next.hairline),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.6).sp,
                            color = next.faint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle =
                            TextStyle(
                                fontSize = 26.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.6).sp,
                                color = next.ink,
                            ),
                        cursorBrush = SolidColor(next.accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                WidthSpacer(12)
                MetaLine(
                    pluralStringResource(R.plurals.next_command_results, results.size, results.size),
                    color = next.faint,
                )
            }
            Hairline()
            if (results.isEmpty()) {
                Text(
                    text = emptyLabel,
                    fontSize = 14.sp,
                    color = next.muted,
                    modifier = Modifier.padding(horizontal = GUTTER, vertical = 22.dp),
                )
            } else {
                LazyColumn {
                    itemsIndexed(results, key = { _, command -> command.id }) { index, command ->
                        CommandRow(command, onClick = onSelect)
                        if (index < results.lastIndex) Hairline(inset = true)
                    }
                }
            }
        }
    }
}

private fun commandSheetFraction(resultCount: Int): Float =
    when {
        resultCount <= SMALL_RESULT_COUNT -> SMALL_SHEET_FRACTION
        resultCount <= MEDIUM_RESULT_COUNT -> MEDIUM_SHEET_FRACTION
        resultCount <= LARGE_RESULT_COUNT -> LARGE_SHEET_FRACTION
        else -> MAX_SHEET_FRACTION
    }

@Composable
private fun CommandRow(
    command: Command,
    onClick: (Command) -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick(command) }
                .padding(horizontal = GUTTER, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = command.label,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.1).sp,
                color = next.ink,
            )
            if (command.hint != null) {
                Gap(4)
                MetaLine(command.hint)
            }
        }
        WidthSpacer(12)
        Pill(command.kind, tint = kindTint(command.kind))
    }
}

@Composable
private fun kindTint(kind: String): Color =
    when (kind) {
        "board" -> boardHue("/g/")
        "search" -> boardHue("/lit/")
        "thread" -> boardHue("/p/")
        else -> next.accent
    }

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    groups: List<Pair<String, List<SettingItem>>>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    expandedId: String? = null,
    showRail: Boolean = true,
    focusId: String? = null,
    onActivate: (SettingItem) -> Unit = {},
    onSelectOption: (SettingItem, Int) -> Unit = { _, _ -> },
    onCommitText: (SettingItem, String) -> Unit = { _, _ -> },
    onSearch: () -> Unit = {},
) {
    val entries = remember(groups) { groups.flatten() }
    val state = rememberLazyListState()
    LaunchedEffect(focusId, entries) {
        val index = entries.indexOfFirst { it is SettingsEntry.Row && it.item.id == focusId }
        if (index >= 0) state.animateScrollToItem(index + 1)
    }

    Surface {
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize().contentInsets(),
                contentPadding = PaddingValues(bottom = RAIL_HEIGHT + 28.dp + bottomInset()),
            ) {
                item {
                    ScreenTitle(
                        text = stringResource(R.string.next_settings_title),
                        subtitle = subtitle,
                    )
                }
                groups.forEach { (heading, rows) ->
                    stickyHeader {
                        SettingsHeading(heading)
                    }
                    items(rows, key = { item -> "row:${item.id}" }) { item ->
                        Column {
                            SettingRow(
                                item = item,
                                expanded = item.id == expandedId,
                                onActivate = onActivate,
                                onSelectOption = onSelectOption,
                                onCommitText = onCommitText,
                            )
                            if (item != rows.last()) Hairline(inset = true)
                        }
                    }
                }
            }
            if (showRail) {
                ContextRail(
                    where = stringResource(R.string.next_settings_title),
                    onSearch = onSearch,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun SettingsHeading(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = next.accent,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(next.background)
                .padding(start = GUTTER, top = 12.dp, bottom = 8.dp),
    )
}

private sealed interface SettingsEntry {
    val key: String

    data class Heading(
        val text: String,
    ) : SettingsEntry {
        override val key: String get() = "heading:$text"
    }

    data class Row(
        val item: SettingItem,
        val last: Boolean,
    ) : SettingsEntry {
        override val key: String get() = "row:${item.id}"
    }
}

private fun List<Pair<String, List<SettingItem>>>.flatten(): List<SettingsEntry> =
    flatMap { (heading, rows) ->
        listOf(SettingsEntry.Heading(heading)) +
            rows.mapIndexed { index, item -> SettingsEntry.Row(item, last = index == rows.lastIndex) }
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
                            Modifier.clickable(role = Role.Button) { onActivate(item) }
                        },
                    ).padding(horizontal = GUTTER, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    fontSize = 15.5.sp,
                    letterSpacing = (-0.1).sp,
                    color = next.ink,
                )
                if (item.kind in STATED_KINDS && item.hint != null) {
                    Text(
                        text = item.hint,
                        fontSize = 12.5.sp,
                        color = next.muted,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            WidthSpacer(12)
            Text(
                text = item.value,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (item.isOn()) next.accent else next.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                    modifier = Modifier.fillMaxWidth().padding(start = GUTTER - 4.dp, end = GUTTER, bottom = 12.dp),
                ) {
                    item.options.forEachIndexed { index, option ->
                        InlineAction(
                            label = option,
                            accent = index == item.selected,
                            onClick = { onSelectOption(item, index) },
                        )
                        WidthSpacer(4)
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
    LaunchedEffect(item.id) { focus.requestFocus() }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = GUTTER, end = GUTTER, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (draft.isEmpty()) {
                Text(text = item.value, fontSize = 15.sp, color = next.faint, maxLines = 1)
            }
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = next.ink),
                cursorBrush = SolidColor(next.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Hairline(modifier = Modifier.padding(top = 26.dp))
        }
        WidthSpacer(8)
        InlineAction(
            label = stringResource(R.string.next_command_save),
            accent = true,
            onClick = { onCommitText(item, draft) },
        )
    }
}

private fun SettingItem.isOn(): Boolean = kind == SettingKind.TOGGLE && value != OFF_LABEL

private val STATED_KINDS = setOf(SettingKind.ACTION, SettingKind.INFO)

private const val SMALL_RESULT_COUNT = 1
private const val MEDIUM_RESULT_COUNT = 3
private const val LARGE_RESULT_COUNT = 6
private const val SMALL_SHEET_FRACTION = 0.42f
private const val MEDIUM_SHEET_FRACTION = 0.48f
private const val LARGE_SHEET_FRACTION = 0.60f
private const val MAX_SHEET_FRACTION = 0.72f

const val ON_LABEL = "On"
const val OFF_LABEL = "Off"
