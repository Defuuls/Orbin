package com.orbin.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

/**
 * The one catalog preview allowed to loop, as Android's feed allows one autoplaying player. Every
 * preview is a web view with its own video decoder; a screen of them at once is what made the
 * catalog stutter. The first cell on screen that can loop takes the slot and gives it up when it
 * scrolls away, so the next one on screen picks it up.
 */
internal object InlineLoops {
    val owner = mutableStateOf<String?>(null)
}

/** Whether the cell [id], which [wants] to loop, holds the slot. */
@Composable
internal fun rememberInlineLoopSlot(
    id: String,
    wants: Boolean,
): Boolean {
    val owner by InlineLoops.owner
    LaunchedEffect(id, wants, owner) {
        if (wants && owner == null) InlineLoops.owner.value = id
        if (!wants && owner == id) InlineLoops.owner.value = null
    }
    DisposableEffect(id) {
        onDispose { if (InlineLoops.owner.value == id) InlineLoops.owner.value = null }
    }
    return wants && owner == id
}
