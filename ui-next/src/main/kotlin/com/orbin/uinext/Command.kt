package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One thing you can go to or do, whatever kind of thing it is. */
data class Command(
    val label: String,
    val kind: String,
    val hint: String? = null,
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
 * whatever you are reading. Type a few letters and it matches boards, threads you have open,
 * settings, and actions in the same list — because to the person typing "auto" there is no
 * meaningful difference between a screen called Media & Playback and the toggle inside it that
 * they actually wanted.
 *
 * Nothing here is a destination you must first know exists. That is what removes the screens
 * without removing the features.
 */
@Composable
fun CommandSheet(
    query: String,
    results: List<Command>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // The dimmed content behind the sheet: you have not left where you were.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(0.32f)
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f)),
                contentAlignment = Alignment.BottomStart,
            ) {
                Column(modifier = Modifier.padding(GUTTER)) {
                    MetaLine("Behind: Feed")
                }
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(0.68f)
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                        .background(MaterialTheme.colorScheme.background),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(width = 34.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f),
                                ),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(GUTTER),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = query,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Box(
                        modifier =
                            Modifier
                                .padding(start = 2.dp)
                                .size(width = 2.dp, height = 24.dp)
                                .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Hairline()
                results.forEach { command ->
                    CommandRow(command)
                    Hairline(inset = true)
                }
            }
        }
    }
}

@Composable
private fun CommandRow(command: Command) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = command.kind,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
            modifier = Modifier.width(74.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = command.label,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (command.hint != null) {
                Gap(2)
                MetaLine(command.hint)
            }
        }
    }
}

@Composable
private fun Modifier.width(value: androidx.compose.ui.unit.Dp): Modifier =
    this.then(Modifier.size(width = value, height = 16.dp))

/**
 * Settings: fifty-nine of them, on one screen.
 *
 * They are currently spread over a hub and seven category screens, which is why a search screen
 * had to be added. Categories are not wrong, but they should be waypoints in one scrolling list
 * rather than places you navigate to and back from — you can flick past a heading, and you cannot
 * flick past a screen.
 *
 * Each row is its label and its current value. No switches drawn as switches, no chevrons, no
 * secondary description repeating the label in a longer form: the value *is* the description, and
 * where it needs explaining, that explanation belongs on the row you pressed rather than on all
 * fifty-nine rows at once.
 */
@Composable
fun SettingsScreen(
    groups: List<Pair<String, List<Pair<String, String>>>>,
    modifier: Modifier = Modifier,
) {
    Surface {
        Column(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f)) {
                ScreenTitle(text = "Settings", subtitle = "59 settings · type ⌘ to jump to one")
                groups.forEach { (heading, rows) ->
                    Text(
                        text = heading.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = GUTTER, top = 18.dp, bottom = 6.dp),
                    )
                    rows.forEach { (label, value) ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = GUTTER, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
                            )
                        }
                        Hairline(inset = true)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(24.dp))
            }
            ContextRail(where = "Settings")
        }
    }
}
