package com.orbin.ios

internal expect fun triggerLightHaptic()

internal expect fun triggerMediumHaptic()

internal expect fun triggerSuccessHaptic()

/** Tactile feedback for key user interactions. */
internal object Haptics {
    fun light() = triggerLightHaptic()

    fun medium() = triggerMediumHaptic()

    fun success() = triggerSuccessHaptic()
}
