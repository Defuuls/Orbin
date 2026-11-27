package com.orbin.uinext.components

import androidx.compose.runtime.getValue

/** Source and board labels are formatted by adapters (`boardLabel` in `:core:reader`); shown as given. */
internal val FeedRow.sourceAndBoard: String
    get() = if (site.isBlank()) board else "$site · $board"
