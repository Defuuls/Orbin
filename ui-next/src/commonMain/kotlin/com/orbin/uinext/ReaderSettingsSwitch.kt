package com.orbin.uinext

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable

/** The enclosing setting row owns the single labeled switch action and state. */
@Composable
internal fun ReaderSettingsSwitch(checked: Boolean) {
    val colors = reader
    Switch(
        checked = checked,
        onCheckedChange = null,
        colors =
            SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
                checkedBorderColor = colors.accent,
                checkedIconColor = colors.accent,
                uncheckedThumbColor = colors.inkMuted,
                uncheckedTrackColor = colors.surfaceVariant,
                uncheckedBorderColor = colors.inkMuted,
                uncheckedIconColor = colors.surfaceVariant,
                disabledCheckedThumbColor = colors.onAccent,
                disabledCheckedTrackColor = colors.accent,
                disabledCheckedBorderColor = colors.accent,
                disabledCheckedIconColor = colors.accent,
                disabledUncheckedThumbColor = colors.inkMuted,
                disabledUncheckedTrackColor = colors.surfaceVariant,
                disabledUncheckedBorderColor = colors.inkMuted,
                disabledUncheckedIconColor = colors.surfaceVariant,
            ),
    )
}
