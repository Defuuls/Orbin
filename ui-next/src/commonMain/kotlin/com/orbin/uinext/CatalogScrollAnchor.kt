package com.orbin.uinext

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** Keep the current thread when a shorter presentation cannot accommodate its old pixel offset. */
@Composable
internal fun PreserveCatalogScrollAnchor(
    state: LazyGridState,
    mode: CatalogMode,
    onModeChange: () -> Unit = {},
) {
    var previousMode by rememberSaveable(state) { mutableStateOf(mode.name) }
    SideEffect {
        if (previousMode != mode.name) {
            onModeChange()
            // Request before the next measure: reusing a tall Card's offset can skip entire rows.
            // Initial/restored state keeps its saved offset because this only runs on a mode change.
            state.requestScrollToItem(state.firstVisibleItemIndex, scrollOffset = 0)
            previousMode = mode.name
        }
    }
}
