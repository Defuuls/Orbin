package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
    placeholder: String = "Board, thread, setting, action",
    emptyLabel: String = "Nothing matches that",
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
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
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(next.background),
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

/** One setting, as the list draws it. [kind] decides what happens when it is pressed. */
data class SettingItem(
    val id: String,
    val label: String,
    val value: String,
    val kind: SettingKind = SettingKind.LINK,
    val options: List<String> = emptyList(),
    val selected: Int = -1,
)

enum class SettingKind {
    /** Pressing flips it. The value reads On or Off. */
    TOGGLE,

    /** Pressing opens its options in place, under the row. */
    CHOICE,

    /** Pressing goes somewhere — the few settings that need a keyboard or a file picker. */
    LINK,
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
    onActivate: (SettingItem) -> Unit = {},
    onSelectOption: (SettingItem, Int) -> Unit = { _, _ -> },
    onSearch: () -> Unit = {},
) {
    Surface {
        Box(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ScreenTitle(
                    text = "Settings",
                    subtitle = subtitle ?: "${groups.sumOf { it.second.size }} of them, in one list",
                )
                groups.forEach { (heading, rows) ->
                    Text(
                        text = heading.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = next.accent,
                        modifier = Modifier.padding(start = GUTTER, top = 22.dp, bottom = 8.dp),
                    )
                    rows.forEachIndexed { index, item ->
                        SettingRow(
                            item = item,
                            expanded = item.id == expandedId,
                            onActivate = onActivate,
                            onSelectOption = onSelectOption,
                        )
                        if (index < rows.lastIndex) Hairline(inset = true)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(RAIL_HEIGHT + 28.dp))
            }
            if (showRail) {
                ContextRail(
                    where = "Settings",
                    onSearch = onSearch,
                    modifier = Modifier.align(Alignment.BottomCenter),
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
    onSelectOption: (SettingItem, Int) -> Unit,
) {
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onActivate(item) }
                    .padding(horizontal = GUTTER, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.label,
                fontSize = 15.5.sp,
                letterSpacing = (-0.1).sp,
                color = next.ink,
                modifier = Modifier.weight(1f),
            )
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
        if (expanded && item.options.isNotEmpty()) {
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

/** Whether the value should read as active. Only toggles have an "on"; a choice is never accented. */
private fun SettingItem.isOn(): Boolean = kind == SettingKind.TOGGLE && value != OFF_LABEL

const val ON_LABEL = "On"
const val OFF_LABEL = "Off"
