package com.orbin.ios

import androidx.compose.runtime.Composable

/** Owns at most one silent upcoming clip and transfers it to the visible platform player. */
internal interface NativeMediaPreloader {
    fun prepare(target: MediaPreloadKey?)

    fun close()
}

internal expect fun createNativeMediaPreloader(): NativeMediaPreloader

/** Keeps the upcoming WebKit document in the viewer's live window without playing or taking input. */
@Composable
internal expect fun NativeMediaPreloadHost(preloader: NativeMediaPreloader)
