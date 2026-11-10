package com.orbin.ios

// The Android target runs common iOS tests; Android's app has its own Media3 preloader.
internal actual fun createNativeMediaPreloader(): NativeMediaPreloader =
    object : NativeMediaPreloader {
        override fun prepare(target: MediaPreloadKey?) = Unit

        override fun close() = Unit
    }
