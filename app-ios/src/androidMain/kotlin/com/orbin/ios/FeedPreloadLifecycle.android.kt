package com.orbin.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect

/** Android exists here only for shared host tests; production Android uses its lifecycle owner. */
@Composable
internal actual fun FeedPreloadLifecycle(
    preloader: NativeMediaPreloader,
    target: MediaPreloadKey?,
): Boolean {
    LaunchedEffect(preloader, target) { preloader.prepare(target) }
    DisposableEffect(preloader) { onDispose { preloader.prepare(null) } }
    return true
}
