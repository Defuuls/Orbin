package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import com.orbin.uinext.tokens.NextRadius
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

// ── Banana Theme (Pale matte yellow) ──────────────────────────────────────
private val BananaLightBackground = Color(0xFFFFFDF0)
private val BananaLightRaised = Color(0xFFF9F5E4)
private val BananaLightElevated = Color(0xFFF2ECE7)
private val BananaLightInk = Color(0xFF1E1B10)
private val BananaLightHairline = Color(0xFFE4DEC8)
private val BananaLightAccent = Color(0xFF755C00)
private val BananaLightAccentSoft = Color(0xFFFFEEA2)
private val BananaLightOnAccent = Color(0xFFFFFFFF)
private val BananaLightAccentContainer = Color(0xFFFFEEA2)
private val BananaLightOnAccentContainer = Color(0xFF241A00)

private val BananaDarkBackground = Color(0xFF15140F)
private val BananaDarkRaised = Color(0xFF211F18)
private val BananaDarkElevated = Color(0xFF2B281E)
private val BananaDarkInk = Color(0xFFE9E2D2)
private val BananaDarkHairline = Color(0xFF4B4638)
private val BananaDarkAccent = Color(0xFFE8C547)
private val BananaDarkAccentSoft = Color(0xFF554100)
private val BananaDarkOnAccent = Color(0xFF3B2F00)
private val BananaDarkAccentContainer = Color(0xFF554100)
private val BananaDarkOnAccentContainer = Color(0xFFFFEEA2)

/** Pale matte yellow palette: warm matte cream surfaces, harvest gold accent. */
fun bananaPalette(
    dark: Boolean,
    amoled: Boolean = false,
): NextPalette =
    if (dark) {
        NextPalette(
            background = if (amoled) Color.Black else BananaDarkBackground,
            raised = BananaDarkRaised,
            elevated = BananaDarkElevated,
            ink = BananaDarkInk,
            muted = BananaDarkInk.copy(alpha = 0.78f),
            faint = BananaDarkInk.copy(alpha = 0.65f),
            hairline = BananaDarkHairline,
            accent = BananaDarkAccent,
            accentSoft = BananaDarkAccentSoft,
            onAccent = BananaDarkOnAccent,
            accentContainer = BananaDarkAccentContainer,
            onAccentContainer = BananaDarkOnAccentContainer,
            dark = true,
            amoled = amoled,
        )
    } else {
        NextPalette(
            background = BananaLightBackground,
            raised = BananaLightRaised,
            elevated = BananaLightElevated,
            ink = BananaLightInk,
            muted = BananaLightInk.copy(alpha = 0.75f),
            faint = BananaLightInk.copy(alpha = 0.62f),
            hairline = BananaLightHairline,
            accent = BananaLightAccent,
            accentSoft = BananaLightAccentSoft,
            onAccent = BananaLightOnAccent,
            accentContainer = BananaLightAccentContainer,
            onAccentContainer = BananaLightOnAccentContainer,
            dark = false,
            amoled = false,
        )
    }

// ── Apple Theme (Pale matte green) ────────────────────────────────────────
private val AppleLightBackground = Color(0xFFF7FAF5)
private val AppleLightRaised = Color(0xFFEDF3EC)
private val AppleLightElevated = Color(0xFFE3ECE2)
private val AppleLightInk = Color(0xFF131D14)
private val AppleLightHairline = Color(0xFFD2DFD1)
private val AppleLightAccent = Color(0xFF366B37)
private val AppleLightAccentSoft = Color(0xFFCCE8CB)
private val AppleLightOnAccent = Color(0xFFFFFFFF)
private val AppleLightAccentContainer = Color(0xFFCCE8CB)
private val AppleLightOnAccentContainer = Color(0xFF002204)

private val AppleDarkBackground = Color(0xFF101611)
private val AppleDarkRaised = Color(0xFF1A221B)
private val AppleDarkElevated = Color(0xFF232E24)
private val AppleDarkInk = Color(0xFFDEE5DF)
private val AppleDarkHairline = Color(0xFF3E4C3F)
private val AppleDarkAccent = Color(0xFF9DD49B)
private val AppleDarkAccentSoft = Color(0xFF1C5220)
private val AppleDarkOnAccent = Color(0xFF033908)
private val AppleDarkAccentContainer = Color(0xFF1C5220)
private val AppleDarkOnAccentContainer = Color(0xFFB9F1B6)

/** Pale matte green palette: celadon surfaces, crisp deep apple green accent. */
fun applePalette(
    dark: Boolean,
    amoled: Boolean = false,
): NextPalette =
    if (dark) {
        NextPalette(
            background = if (amoled) Color.Black else AppleDarkBackground,
            raised = AppleDarkRaised,
            elevated = AppleDarkElevated,
            ink = AppleDarkInk,
            muted = AppleDarkInk.copy(alpha = 0.78f),
            faint = AppleDarkInk.copy(alpha = 0.65f),
            hairline = AppleDarkHairline,
            accent = AppleDarkAccent,
            accentSoft = AppleDarkAccentSoft,
            onAccent = AppleDarkOnAccent,
            accentContainer = AppleDarkAccentContainer,
            onAccentContainer = AppleDarkOnAccentContainer,
            dark = true,
            amoled = amoled,
        )
    } else {
        NextPalette(
            background = AppleLightBackground,
            raised = AppleLightRaised,
            elevated = AppleLightElevated,
            ink = AppleLightInk,
            muted = AppleLightInk.copy(alpha = 0.75f),
            faint = AppleLightInk.copy(alpha = 0.62f),
            hairline = AppleLightHairline,
            accent = AppleLightAccent,
            accentSoft = AppleLightAccentSoft,
            onAccent = AppleLightOnAccent,
            accentContainer = AppleLightAccentContainer,
            onAccentContainer = AppleLightOnAccentContainer,
            dark = false,
            amoled = false,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedSortSheet(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = next.raised,
        contentColor = next.ink,
        shape = RoundedCornerShape(topStart = NextRadius.sheet, topEnd = NextRadius.sheet),
        dragHandle = {
            BottomSheetDefaults.DragHandle()
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Sort your feed", style = NextType.footnote, color = next.muted, modifier = Modifier.padding(8.dp))
            labels.forEachIndexed { index, label ->
                InlineAction(
                    label =
                        if (index ==
                            selected
                        ) {
                            "$label ✓"
                        } else {
                            label
                        },
                    selected = index == selected,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    onSelect(index)
                    onDismiss()
                }
            }
        }
    }
}
