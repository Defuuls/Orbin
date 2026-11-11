package com.orbin.uinext

import androidx.compose.runtime.Composable

/** Connects the open drawer to the same native Back dispatcher as its containing routes. */
@Composable
internal expect fun ReaderDrawerBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)
