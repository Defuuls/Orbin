package com.orbin.uinext

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_clear_search
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.stringResource

/**
 * The app's OPTIC-26 palette for [dark] / [amoled], shared across Android and iOS.
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
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val toggle: ((Boolean) -> Unit)? =
        onCheckedChange?.let { onChange ->
            { value ->
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn)
                onChange(value)
            }
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
 * OPTIC-26 machined toggle switch for iOS with accessible 48dp hit target.
 */
@Composable
private fun IosSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val trackOn = next.accent
    val trackOff = next.elevated
    val trackColor = animateColorAsState(if (checked) trackOn else trackOff, label = "ios-switch-track")
    val thumbOffset = animateDpAsState(if (checked) 20.dp else 0.dp, label = "ios-switch-thumb")
    val trackShape = RoundedCornerShape(NextRadius.control)
    val thumbShape = RoundedCornerShape(NextRadius.tight)
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
                .then(
                    if (onCheckedChange != null) {
                        Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .width(IOS_SWITCH_WIDTH)
                    .height(IOS_SWITCH_HEIGHT)
                    .clip(trackShape)
                    .background(trackColor.value)
                    .border(1.dp, if (checked) next.accent else next.hairline, trackShape)
                    .padding(2.dp),
        ) {
            Box(
                Modifier
                    .offset(x = thumbOffset.value)
                    .size(IOS_SWITCH_THUMB)
                    .clip(thumbShape)
                    .background(if (checked) next.onAccent else next.ink),
            )
        }
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
