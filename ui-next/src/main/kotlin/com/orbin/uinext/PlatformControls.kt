package com.orbin.uinext

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.NextMotion
import com.orbin.uinext.tokens.NextType

/** Material color roles for the handoff's Android counterpart, including dark/AMOLED support. */
fun materialPalette(
    dark: Boolean,
    amoled: Boolean = false,
): NextPalette =
    if (dark) {
        DarkPalette.copy(
            background = if (amoled) Color.Black else Color(0xFF141218),
            raised = Color(0xFF211F26),
            elevated = Color(0xFF2B2930),
            accent = Color(0xFFD0BCFF),
            accentSoft = Color(0xFF4A4458),
            onAccent = Color(0xFF381E72),
            amoled = amoled,
        )
    } else {
        LightPalette.copy(
            background = Color(0xFFFEF7FF),
            raised = Color(0xFFF7F2FA),
            elevated = Color(0xFFF3EDF7),
            ink = Color(0xFF1D1B20),
            muted = Color(0xFF49454F),
            faint = Color(0xFF49454F),
            hairline = Color(0xFFE7E0EC),
            accent = Color(0xFF6750A4),
            accentSoft = Color(0xFFE8DEF8),
        )
    }

@Composable
fun PlatformSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalNextPlatform.current == NextPlatform.ANDROID) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier)
        return
    }
    val offset by animateDpAsState(if (checked) 20.dp else 0.dp, tween(320, easing = NextMotion.Ease), label = "switch")
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = 51.dp, minHeight = 48.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(51.dp, 31.dp).clip(CircleShape).background(if (checked) Color(0xFF34C759) else next.hairline),
        ) {
            Box(
                Modifier
                    .padding(2.dp)
                    .offset(x = offset)
                    .size(27.dp)
                    .background(Color.White, CircleShape),
            )
        }
    }
}

@Composable
fun PlatformSegments(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val android = LocalNextPlatform.current == NextPlatform.ANDROID
    val shape = RoundedCornerShape(if (android) 20.dp else 9.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (android) {
                    Modifier.border(
                        1.dp,
                        next.muted,
                        shape,
                    )
                } else {
                    Modifier.background(next.ink.copy(alpha = 0.06f))
                },
            ).selectableGroup()
            .padding(2.dp),
    ) {
        labels.forEachIndexed { index, label ->
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(if (android) 18.dp else 7.dp))
                    .background(
                        if (index ==
                            selected
                        ) {
                            if (android) next.accentSoft else next.raised
                        } else {
                            Color.Transparent
                        },
                    ).selectable(selected = index == selected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = NextType.footnote, color = next.ink, fontWeight = FontWeight.SemiBold)
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
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = next.muted,
            modifier = Modifier.padding(end = 8.dp).size(18.dp),
        )
        NextTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            modifier = Modifier.weight(1f),
            trailing = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = next.muted)
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedSortSheet(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val ios = LocalNextPlatform.current == NextPlatform.IOS
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = next.raised,
        contentColor = next.ink,
        shape = RoundedCornerShape(topStart = if (ios) 14.dp else 28.dp, topEnd = if (ios) 14.dp else 28.dp),
        modifier = if (ios) Modifier.padding(horizontal = 8.dp) else Modifier,
        dragHandle =
            if (ios) {
                null
            } else {
                {
                    androidx.compose.material3.BottomSheetDefaults
                        .DragHandle()
                }
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
            if (ios) InlineAction("Cancel", modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}
