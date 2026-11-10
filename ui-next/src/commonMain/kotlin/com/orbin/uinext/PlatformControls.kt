package com.orbin.uinext

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_clear_search
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.stringResource

/**
 * The app's shared reader palette for [dark] / [amoled], shared across Android and iOS.
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
                    uncheckedTrackColor = next.elevated,
                    uncheckedBorderColor = next.hairline,
                ),
        )
    }
}

/**
 * Rounded monochrome toggle switch for iOS with accessible 48dp hit target.
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
    val trackShape = RoundedCornerShape(50)
    val thumbShape = RoundedCornerShape(50)
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
                    .background(
                        if (checked) {
                            next.onAccent
                        } else if (next.dark) {
                            next.ink
                        } else {
                            Color.White
                        },
                    ),
            )
        }
    }
}

private val IOS_SWITCH_WIDTH = 51.dp
private val IOS_SWITCH_HEIGHT = 31.dp
private val IOS_SWITCH_THUMB = 27.dp

/** Compact text tabs, with native underline selection rather than Material segmented chips. */
@Composable
fun PlatformSegments(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(modifier.fillMaxWidth().selectableGroup()) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Column(
                modifier =
                    Modifier.weight(1f).selectable(
                        selected = active,
                        role = Role.Tab,
                        onClick = {
                            if (!active) {
                                haptics.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn,
                                )
                            }
                            onSelect(index)
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = MIN_TOUCH_TARGET,
                        ).padding(horizontal = 8.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = NextType.body,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) next.ink else next.muted,
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            if (active) 1.5.dp else 0.5.dp,
                        ).background(if (active) next.ink else next.hairline),
                )
            }
        }
    }
}

/** A compact gray iOS-style field; the field and clear action retain accessible touch targets. */
@Composable
fun SchematicSearch(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET), contentAlignment = Alignment.Center) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    imeAction =
                        if (onSearch !=
                            null
                        ) {
                            ImeAction.Search
                        } else {
                            ImeAction.Default
                        },
                ),
            keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
            textStyle = NextType.body.copy(color = next.ink, lineHeight = 20.sp),
            cursorBrush = SolidColor(next.ink),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(next.raised)
                    .semantics { contentDescription = placeholder },
            decorationBox = { inner ->
                Row(
                    Modifier.padding(
                        start = 10.dp,
                        end = if (value.isEmpty()) 10.dp else 48.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        ReaderIcons.Search,
                        contentDescription = null,
                        tint = next.muted,
                        modifier = Modifier.size(18.dp),
                    )
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = NextType.body.copy(lineHeight = 20.sp),
                                color = next.muted,
                            )
                        }
                        inner()
                    }
                }
            },
        )
        if (value.isNotEmpty()) {
            NextIconAction(
                imageVector = ReaderIcons.Close,
                contentDescription = stringResource(Res.string.next_clear_search),
                onClick = { onValueChange("") },
                tint = next.muted,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}
