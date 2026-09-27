package com.orbin.ios

/** Tactile feedback for key user interactions. */
internal expect object Haptics {
    fun light()

    fun medium()

    fun success()
}
