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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
) {
    Box(modifier = modifier.fillMaxSize().background(next.background)) {
        // What you were reading, still there, dimmed.
        Column(modifier = Modifier.fillMaxSize()) {
            FadedFeedBehind()
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(0.28f))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(0.72f)
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
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = GUTTER, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = query,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.6).sp,
                        color = next.ink,
                    )
                    Box(
                        modifier =
                            Modifier
                                .padding(start = 3.dp)
                                .size(width = 2.dp, height = 26.dp)
                                .background(next.accent),
                    )
                    Box(modifier = Modifier.weight(1f))
                    MetaLine("${results.size} matches", color = next.faint)
                }
                Hairline()
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    results.forEachIndexed { index, command ->
                        CommandRow(command)
                        if (index < results.lastIndex) Hairline(inset = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandRow(command: Command) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 14.dp),
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

/** A suggestion of the feed underneath, so the sheet reads as a layer rather than a screen. */
@Composable
private fun FadedFeedBehind() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 26.dp)) {
        Text(
            text = "Feed",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.9).sp,
            color = next.ink,
            modifier = Modifier.padding(horizontal = GUTTER),
        )
        Gap(24)
        repeat(3) { index ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 15.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier =
                            Modifier
                                .width(54.dp)
                                .height(11.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(next.hairline),
                    )
                    Gap(10)
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(if (index == 1) 0.62f else 0.88f)
                                .height(15.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(next.hairline),
                    )
                    Gap(8)
                    Box(
                        modifier =
                            Modifier
                                .width(120.dp)
                                .height(11.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(next.hairline),
                    )
                }
                WidthSpacer(14)
                MediaTile(modifier = Modifier.size(68.dp), seed = index, radius = 14.dp)
            }
        }
    }
}

/**
 * Settings: fifty-nine of them, on one screen.
 *
 * They are currently spread over a hub and seven category screens, which is why a search screen had
 * to be added. Categories are not wrong, but they should be waypoints in one scrolling list rather
 * than places you navigate to and back from — you can flick past a heading, and you cannot flick
 * past a screen.
 *
 * Each row is its label and its current value. No switches drawn as switches, no chevrons, no
 * secondary description repeating the label in a longer form: the value *is* the description, and
 * where it needs explaining, that explanation belongs on the row you pressed rather than on all
 * fifty-nine rows at once. A setting that is on says so in the accent colour, so the state of a
 * whole section is one glance down the right-hand edge.
 */
@Composable
fun SettingsScreen(
    groups: List<Pair<String, List<Pair<String, String>>>>,
    modifier: Modifier = Modifier,
) {
    Surface {
        Box(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ScreenTitle(text = "Settings", subtitle = "59 of them, in one list")
                groups.forEach { (heading, rows) ->
                    Text(
                        text = heading.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = next.accent,
                        modifier = Modifier.padding(start = GUTTER, top = 22.dp, bottom = 8.dp),
                    )
                    rows.forEachIndexed { index, (label, value) ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = GUTTER, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                fontSize = 15.5.sp,
                                letterSpacing = (-0.1).sp,
                                color = next.ink,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = value,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (value == "On" || value == "Always on") next.accent else next.muted,
                            )
                        }
                        if (index < rows.lastIndex) Hairline(inset = true)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(RAIL_HEIGHT + 28.dp))
            }
            ContextRail(where = "Settings", modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
