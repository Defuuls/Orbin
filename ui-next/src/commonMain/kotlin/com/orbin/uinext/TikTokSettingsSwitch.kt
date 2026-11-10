package com.orbin.uinext

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Decorative control: its enclosing setting row owns the labeled switch action and state. */
@Composable
internal fun TikTokSettingsSwitch(checked: Boolean) {
    val track by animateColorAsState(
        if (checked) {
            TikTokDesign.Pink
        } else if (next.dark) {
            Color(0xFF3B3B3B)
        } else {
            Color(0xFFE3E3E4)
        },
        label = "settings-switch-track",
    )
    val offset by animateDpAsState(if (checked) 18.dp else 0.dp, label = "settings-switch-thumb")
    Box(
        Modifier.size(width = 44.dp, height = 26.dp).clip(RoundedCornerShape(13.dp)).background(track),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(2.dp)
                .offset(x = offset)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
