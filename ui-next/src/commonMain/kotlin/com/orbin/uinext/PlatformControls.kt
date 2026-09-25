package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.NextType

private val DarkBackground = Color(0xFF141218)
private val DarkRaised = Color(0xFF211F26)
private val DarkElevated = Color(0xFF2B2930)
private val DarkAccent = Color(0xFFD0BCFF)
private val DarkAccentSoft = Color(0xFF4A4458)
private val DarkOnAccent = Color(0xFF381E72)
private val LightBackground = Color(0xFFFEF7FF)
private val LightRaised = Color(0xFFF7F2FA)
private val LightElevated = Color(0xFFF3EDF7)
private val LightInk = Color(0xFF1D1B20)
private val LightMuted = Color(0xFF49454F)
private val LightHairline = Color(0xFFE7E0EC)
private val LightAccent = Color(0xFF6750A4)
private val LightAccentSoft = Color(0xFFE8DEF8)

/** Material color roles for the handoff's Android counterpart, including dark/AMOLED support. */
fun materialPalette(
    dark: Boolean,
    amoled: Boolean = false,
): NextPalette =
    if (dark) {
        DarkPalette.copy(
            background = if (amoled) Color.Black else DarkBackground,
            raised = DarkRaised,
            elevated = DarkElevated,
            accent = DarkAccent,
            accentSoft = DarkAccentSoft,
            onAccent = DarkOnAccent,
            amoled = amoled,
        )
    } else {
        LightPalette.copy(
            background = LightBackground,
            raised = LightRaised,
            elevated = LightElevated,
            ink = LightInk,
            muted = LightMuted,
            faint = LightMuted,
            hairline = LightHairline,
            accent = LightAccent,
            accentSoft = LightAccentSoft,
        )
    }

@Composable
fun PlatformSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
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

@Composable
fun PlatformSegments(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, next.hairline, shape)
            .background(next.elevated)
            .selectableGroup()
            .padding(3.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(
                        if (isSelected) next.accentSoft else Color.Transparent,
                    ).selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = NextType.footnote,
                    color = if (isSelected) next.accent else next.ink,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
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
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = next.muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        },
    )
}
