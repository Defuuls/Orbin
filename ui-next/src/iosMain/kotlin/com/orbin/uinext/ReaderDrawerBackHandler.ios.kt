package com.orbin.uinext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

@Composable
internal actual fun ReaderDrawerBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    val currentOnBack by rememberUpdatedState(onBack)
    val handler =
        remember(dispatcher) {
            object : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, enabled) {
                override fun onBackCompleted() = currentOnBack()
            }
        }
    SideEffect { handler.isBackEnabled = enabled }
    DisposableEffect(dispatcher, handler) {
        dispatcher?.addHandler(handler, NavigationEventDispatcher.PRIORITY_OVERLAY)
        onDispose { handler.remove() }
    }
}
