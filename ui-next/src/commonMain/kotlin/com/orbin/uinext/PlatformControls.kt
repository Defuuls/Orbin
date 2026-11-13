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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_clear_search
import com.orbin.uinext.resources.next_provider_unknown
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType
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
                        if (checked) reader.onAccent else reader.inkMuted,
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
    compact: Boolean = false,
) {
    if (compact) {
        PlatformProviderMenu(labels = labels, selected = selected, onSelect = onSelect, modifier = modifier)
        return
    }
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
                        style = OrbinType.body,
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

/** A compact gray TikTok-style field inside a full-height editable touch target. */
@Composable
fun SchematicSearch(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(4.dp),
    containerColor: Color? = null,
) {
    val fill = containerColor ?: reader.surfaceVariant
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
            textStyle = OrbinType.body.copy(color = next.ink, lineHeight = 20.sp),
            cursorBrush = SolidColor(reader.accent),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .semantics { contentDescription = placeholder },
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(fill)
                            .padding(
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
                                    style = OrbinType.body.copy(lineHeight = 20.sp),
                                    color = next.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                    }
                }
            },
        )
        if (value.isNotEmpty()) {
            val clearLabel = stringResource(Res.string.next_clear_search)
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(MIN_TOUCH_TARGET)
                    .nextClickable(role = Role.Button, onClick = { onValueChange("") })
                    .semantics { contentDescription = clearLabel },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(next.muted),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        ReaderIcons.Close,
                        contentDescription = null,
                        tint = next.background,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

/** A compact provider chip with a menu, used on the Coral Boards home row. */
@Composable
private fun PlatformProviderMenu(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var open by remember { mutableStateOf(false) }
    val selectedInRange = selected in labels.indices
    val selectedLabel =
        if (selectedInRange) {
            labels[selected]
        } else {
            stringResource(Res.string.next_provider_unknown)
        }
    val menuShape = RoundedCornerShape(OrbinRadius.search)
    Box(modifier) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .clip(menuShape)
                    .background(reader.surfaceVariant)
                    .border(2.dp, next.muted, menuShape)
                    .nextClickable(role = Role.Button, onClick = { open = true })
                    .padding(horizontal = 12.dp)
                    .semantics { contentDescription = selectedLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                selectedLabel,
                style = OrbinType.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = next.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                ReaderIcons.ChevronDown,
                contentDescription = null,
                tint = next.muted,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = reader.background,
        ) {
            labels.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            style = OrbinType.body,
                            fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (index == selected) reader.accent else reader.ink,
                        )
                    },
                    onClick = {
                        open = false
                        if (index != selected) {
                            haptics.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn,
                            )
                            onSelect(index)
                        }
                    },
                )
            }
        }
    }
}
