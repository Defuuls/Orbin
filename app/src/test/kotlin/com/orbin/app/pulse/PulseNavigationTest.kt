package com.orbin.app.pulse

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.stream.StreamLens
import com.orbin.uinext.components.NextDestination
import org.junit.Test

class PulseNavigationTest {
    @Test
    fun everyLensPulseOffersHasAScreenToOpen() {
        PulseLenses.forEach { assertThat(it.destination()).isNotNull() }
    }

    @Test
    fun lensesMapToTheScreensTheyNameAndTheRestToNothing() {
        assertThat(StreamLens.Home.destination()).isEqualTo(NextDestination.FEED)
        assertThat(StreamLens.Watched.destination()).isEqualTo(NextDestination.WATCHED)
        assertThat(StreamLens.Saved.destination()).isEqualTo(NextDestination.SAVED)
        assertThat(StreamLens.Settings.destination()).isEqualTo(NextDestination.SETTINGS)
        assertThat(StreamLens.Feeds.destination()).isNull()
        assertThat(StreamLens.History.destination()).isNull()
        assertThat(StreamLens.Clips.destination()).isNull()
    }

    @Test
    fun settingsIsAlwaysReachableSoPulseCanBeSwitchedOff() {
        assertThat(PulseLenses).contains(StreamLens.Settings)
    }
}
