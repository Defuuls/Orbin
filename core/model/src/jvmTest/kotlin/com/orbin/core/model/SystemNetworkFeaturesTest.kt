package com.orbin.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SystemNetworkFeaturesTest {
    @Test
    fun systemNetworkFeaturesAreAllowedByDefault() {
        assertThat(AppSettings.Default.allowsSystemNetworkFeatures).isTrue()
    }

    @Test
    fun strictEncryptedDnsTurnsThemOff() {
        assertThat(AppSettings.Default.copy(strictDohEnabled = true).allowsSystemNetworkFeatures).isFalse()
    }
}
