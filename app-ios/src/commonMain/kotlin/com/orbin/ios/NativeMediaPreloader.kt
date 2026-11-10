package com.orbin.ios

/** Owns at most one silent upcoming clip and transfers it to the visible platform player. */
internal interface NativeMediaPreloader {
    fun prepare(target: MediaPreloadKey?)

    fun close()
}

internal expect fun createNativeMediaPreloader(): NativeMediaPreloader
