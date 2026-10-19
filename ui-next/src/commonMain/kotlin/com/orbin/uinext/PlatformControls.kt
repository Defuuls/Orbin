package com.orbin.uinext

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_clear_search
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.stringResource

/**
 * The app's palette for [dark] / [amoled], the same forest-green palettes iOS draws with. Android
 * used to substitute Material's baseline purple here while keeping the green containers, so the
 * two apps differed and Android mixed purple accents with green surfaces.
 */
fun materialPalette(
    dark: Boolean,
    amoled: Boolean = false,
): NextPalette =
    when {
        dark && amoled -> AmoledPalette
        dark -> DarkPalette
        else -> LightPalette
    }

@Composable
fun PlatformSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val toggle: (Boolean) -> Unit = {
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn)
        onCheckedChange(it)
    }
    if (LocalNextPlatform.current == NextPlatform.IOS) {
        IosSwitch(checked = checked, onCheckedChange = toggle, modifier = modifier)
    } else {
        Switch(
            checked = checked,
            onCheckedChange = toggle,
            modifier = modifier,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = next.onAccent,
                    checkedTrackColor = next.accent,
                    uncheckedThumbColor = next.muted,
                    uncheckedTrackColor = next.faint,
                    uncheckedBorderColor = next.hairline,
                ),
        )
    }
}

/**
 * UISwitch-like metrics and colors for iOS. Brand forest green stays elsewhere in the shell;
 * the switch itself uses system green / grey chrome so it reads as a native control.
 */
@Composable
private fun IosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackOn = next.accent
    val trackOff = next.hairline
    val trackColor = animateColorAsState(if (checked) trackOn else trackOff, label = "ios-switch-track")
    val thumbOffset = animateDpAsState(if (checked) 20.dp else 0.dp, label = "ios-switch-thumb")
    Box(
        modifier =
            modifier
                .width(IOS_SWITCH_WIDTH)
                .height(IOS_SWITCH_HEIGHT)
                .clip(RoundedCornerShape(percent = 50))
                .background(trackColor.value)
                .nextClickable(role = Role.Switch, onClick = { onCheckedChange(!checked) })
                .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset.value)
                .size(IOS_SWITCH_THUMB)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

private val IOS_SWITCH_WIDTH = 51.dp
private val IOS_SWITCH_HEIGHT = 31.dp
private val IOS_SWITCH_THUMB = 27.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformSegments(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth(),
    ) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selected,
                onClick = {
                    if (index != selected) {
                        haptics.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn,
                        )
                    }
                    onSelect(index)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                colors =
                    SegmentedButtonDefaults.colors(
                        activeContainerColor = next.accentSoft,
                        activeContentColor = next.accent,
                        inactiveContainerColor = next.elevated,
                        inactiveContentColor = next.ink,
                        activeBorderColor = next.hairline,
                        inactiveBorderColor = next.hairline,
                    ),
                label = {
                    Text(
                        text = label,
                        style = NextType.callout,
                        fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Medium,
                    )
                },
            )
        }
    }
}

@Composable
fun SchematicSearch(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    NextTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier.fillMaxWidth(),
        leading = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = next.muted,
                modifier = Modifier.size(18.dp),
            )
        },
        trailing = {
            if (value.isNotEmpty()) {
                NextIconAction(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.next_clear_search),
                    onClick = { onValueChange("") },
                    tint = next.muted,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
    )
}
