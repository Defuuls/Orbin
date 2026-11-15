package com.orbin.uinext.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbin.uinext.SettingFilterRule
import com.orbin.uinext.SettingItem
import com.orbin.uinext.SettingKind
import com.orbin.uinext.components.InlineAction
import com.orbin.uinext.components.NextIconAction
import com.orbin.uinext.components.ReaderIcons
import com.orbin.uinext.components.ReaderSettingsSwitch
import com.orbin.uinext.components.readerTextEntry
import com.orbin.uinext.components.settingsStyle

/** Add/remove actions commit explicit rules; editing a draft never changes the saved filters. */
@Composable
internal fun SettingsFilterEditor(
    item: SettingItem,
    onCommit: (SettingItem, List<SettingFilterRule>) -> Unit,
) {
    val posts = item.id == "postFilterRules"
    var rules by remember(item.filterRules) { mutableStateOf(item.filterRules.orEmpty()) }
    var draft by remember(item.id) { mutableStateOf("") }
    var kind by remember(item.id) { mutableStateOf(0) }
    var regex by remember(item.id) { mutableStateOf(false) }
    var error by remember(item.id) { mutableStateOf(false) }
    val kinds = listOf("TEXT", "NAME", "TRIPCODE", "COUNTRY")
    val colors = settingsStyle
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterRuleList(rules, posts) { index ->
            rules = rules.filterIndexed { current, _ -> current != index }
            onCommit(item, rules)
        }
        if (posts) {
            SettingsSegments(
                SettingItem(
                    "filterKind",
                    "Match",
                    "",
                    SettingKind.CHOICE,
                    options = listOf("Text", "Name", "Tripcode", "Country"),
                    selected = kind,
                ),
                onSelect = { kind = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text("Filter pattern", style = preferenceSummaryStyle, color = colors.muted)
        BasicTextField(
            value = draft,
            onValueChange = {
                draft = it
                error = false
            },
            textStyle = preferenceTitleStyle.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            modifier =
                Modifier.readerTextEntry().fillMaxWidth().background(colors.background).padding(12.dp).semantics {
                    contentDescription = "Filter pattern"
                },
        )
        if (posts) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = 48.dp,
                    ).toggleable(value = regex, role = Role.Switch, onValueChange = {
                        regex = it
                        error = false
                    })
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Regular expression", style = preferenceSummaryStyle, color = colors.text)
                ReaderSettingsSwitch(regex)
            }
        } else {
            Text(
                "Keyword or regular expression; matching ignores case.",
                style = preferenceSummaryStyle,
                color = colors.muted,
            )
        }
        if (error) Text("Enter a valid regular expression.", style = preferenceSummaryStyle, color = colors.danger)
        InlineAction("Add filter", accent = true, onClick = {
            val pattern = draft.trim()
            if (pattern.isNotEmpty()) {
                if (posts && regex && runCatching { Regex(pattern) }.isFailure) {
                    error = true
                } else {
                    rules =
                        (
                            rules +
                                SettingFilterRule(
                                    if (posts) kinds[kind] else "TEXT",
                                    pattern,
                                    posts && regex,
                                )
                        ).distinct()
                    onCommit(item, rules)
                    draft = ""
                    error = false
                }
            }
        })
    }
}

@Composable
private fun FilterRuleList(
    rules: List<SettingFilterRule>,
    posts: Boolean,
    onRemove: (Int) -> Unit,
) {
    val colors = settingsStyle
    rules.forEachIndexed { index, rule ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (posts) {
                    "${rule.kind.lowercase()}: ${rule.pattern}${if (rule.regex) " (regex)" else ""}"
                } else {
                    rule.pattern
                },
                style = preferenceSummaryStyle,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            NextIconAction(ReaderIcons.Close, "Remove filter ${rule.pattern}", { onRemove(index) })
        }
    }
}
