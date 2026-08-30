package com.orbin.uinext

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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One thing you can go to or do, whatever kind of thing it is.
 *
 * [id] is what selection reports back, so the sheet never has to know what a board, a thread, a
 * setting or an action actually is — only that they are all things with a name you can type.
 */
data class Command(
    val label: String,
    val kind: String,
    val hint: String? = null,
    val id: String = "$kind:$label",
)

/**
 * The command surface — the single answer to "there are too many screens".
 *
 * The current app reaches its twenty-one destinations through a two-item bottom bar, icons in each
 * screen's top bar, overflow menus, and a settings hub of seven category screens with a search
 * screen bolted on to find your way around them. That search screen is the tell: the interface had
 * already grown past the point where anyone could remember where anything lived, and the fix was
 * another screen.
 *
 * So make that the primary way in, for everything, not a last resort for settings. One sheet over
 * whatever you are reading — the page behind it stays visible under a scrim, so you have not left
 * where you were. Type a few letters and it matches boards, threads you have open, settings, and
 * actions in the same list, each tagged with what kind of thing it is, because to the person typing
 * "auto" there is no meaningful difference between a screen called Media & Playback and the toggle
 * inside it that they actually wanted.
 *
 * Nothing here is a destination you must first know exists. That is what removes the screens without
 * removing the features.
 */
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
    Box(modifier = modifier.fillMaxSize()) {
        // Tapping what you were reading puts you back in it. The sheet is a layer, not a screen.
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
                    .fillMaxHeight(0.72f)
                    .align(Alignment.BottomCenter)
                    // The keyboard moves the whole sheet, since a search sheet whose results are
                    // behind the keyboard is a search sheet you cannot read. The navigation bar
                    // only insets the content: the sheet's own background still runs to the bottom
                    // edge, so it reads as attached to it rather than floating above a gap.
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
                MetaLine("${results.size}", color = next.faint)
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

/**
 * One setting, as the list draws it. [kind] decides what happens when it is pressed.
 *
 * [value] is what the row shows on the right; for a [SettingKind.TEXT] row that may be a summary
 * ("3 tags"), so [text] carries the raw string the editor is seeded with. [hint] is the line that
 * appears under the label while a row is open, for the settings whose format has to be explained.
 */
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
    /** Pressing flips it. The value reads On or Off. */
    TOGGLE,

    /** Pressing opens its options in place, under the row. */
    CHOICE,

    /** Pressing opens a text field in place, under the row. */
    TEXT,

    /**
     * Pressing does something here — a file picker, an export, a check.
     *
     * Not a link: the interface it belongs to is the one you are already in, and where a system
     * picker is the right editor that picker opens over this screen rather than a screen of ours
     * opening under it.
     */
    ACTION,

    /** States something and cannot be pressed: the settings that are guarantees, not choices. */
    INFO,
}

/**
 * Settings: all of them, on one screen.
 *
 * They were spread over a hub and seven category screens, which is why a settings *search* screen
 * had to be added — the interface had outgrown anyone's memory of where things lived, and the fix
 * was another screen. Categories are not wrong, but they should be waypoints in one scrolling list
 * rather than places you navigate to and back from: you can flick past a heading, and you cannot
 * flick past a screen.
 *
 * Each row is its label and its current value. No switches drawn as switches, no chevrons, no
 * secondary description repeating the label in a longer form: the value *is* the description. A
 * setting that is on says so in the accent colour, so the state of a whole section is one glance
 * down the right-hand edge.
 *
 * A choice opens under the row that owns it rather than in a dialog, so the list never moves out
 * from under the thing you were reading.
 */
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
    // Flattened once so the list is lazy and a row can be reached by index: arriving from the
    // command surface having typed a setting's name should land on that setting, not near it.
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
                        subtitle = subtitle ?: "${groups.sumOf { it.second.size }} of them, in one list",
                    )
                }
                items(entries, key = SettingsEntry::key) { entry ->
                    when (entry) {
                        is SettingsEntry.Heading ->
                            Text(
                                text = entry.text.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = next.accent,
                                modifier = Modifier.padding(start = GUTTER, top = 22.dp, bottom = 8.dp),
                            )

                        is SettingsEntry.Row ->
                            Column {
                                SettingRow(
                                    item = entry.item,
                                    expanded = entry.item.id == expandedId,
                                    onActivate = onActivate,
                                    onSelectOption = onSelectOption,
                                    onCommitText = onCommitText,
                                )
                                if (!entry.last) Hairline(inset = true)
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

/** A heading or a row, in the order they are drawn, so the list can be lazy and addressable. */
private sealed interface SettingsEntry {
    val key: String

    data class Heading(
        val text: String,
    ) : SettingsEntry {
        override val key: String get() = "heading:$text"
    }

    /** [last] suppresses the separator after a group's final row, which its heading replaces. */
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
                    // An INFO row states a guarantee. Nothing happens when it is pressed, so it
                    // does not offer a press: no ripple, and no button role for a screen reader.
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
                // An action states its consequence without being pressed: "merges rather than
                // replaces" is not something to find out afterwards, and a stated guarantee is
                // only worth stating in full. Toggles and choices don't get this — for them the
                // value is the description, and a second line would be noise.
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

/**
 * The text editor for a setting that is a string rather than a choice — tags, a user agent, a time.
 *
 * It opens under its own row exactly as a choice does, so the one thing the list never does is move
 * out from under what you were reading. The draft is local until Save, because a setting that wrote
 * on every keystroke would persist every half-typed intermediate state of it.
 */
@Composable
private fun SettingTextEditor(
    item: SettingItem,
    onCommitText: (SettingItem, String) -> Unit,
) {
    // Keyed on the row, so opening a different one starts from that row's value rather than the
    // last one's, and a value changed elsewhere is picked up on reopening.
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

/** Whether the value should read as active. Only toggles have an "on"; a choice is never accented. */
private fun SettingItem.isOn(): Boolean = kind == SettingKind.TOGGLE && value != OFF_LABEL

private val STATED_KINDS = setOf(SettingKind.ACTION, SettingKind.INFO)

const val ON_LABEL = "On"
const val OFF_LABEL = "Off"
