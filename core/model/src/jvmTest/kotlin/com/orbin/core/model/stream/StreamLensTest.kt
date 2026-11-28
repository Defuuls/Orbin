package com.orbin.core.model.stream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreamLensTest {
    @Test
    fun idsAndHashesAreUnique() {
        assertThat(StreamLens.entries.map { it.id }.toSet()).hasSize(StreamLens.entries.size)
        assertThat(StreamLens.entries.map { it.hash }.toSet()).hasSize(StreamLens.entries.size)
    }

    @Test
    fun settingsUsesPrefsBecauseSettingsIsReservedByHosts() {
        assertThat(StreamLens.fromHash("#prefs")).isEqualTo(StreamLens.Settings)
        assertThat(StreamLens.fromHash("#settings")).isNull()
    }

    @Test
    fun fromHashAcceptsTheFragmentWithOrWithoutTheHashSign() {
        assertThat(StreamLens.fromHash("watched")).isEqualTo(StreamLens.Watched)
        assertThat(StreamLens.fromHash("#clips")).isEqualTo(StreamLens.Clips)
    }

    @Test
    fun unknownEmptyAndNullInputYieldNoLens() {
        assertThat(StreamLens.fromHash("")).isNull()
        assertThat(StreamLens.fromHash("#")).isNull()
        assertThat(StreamLens.fromHash(null)).isNull()
        assertThat(StreamLens.fromHash("#HOME")).isNull()
        assertThat(StreamLens.fromId("nope")).isNull()
        assertThat(StreamLens.fromId(null)).isNull()
        assertThat(StreamLens.fromId("history")).isEqualTo(StreamLens.History)
    }

    @Test
    fun everyLensRoundTripsThroughItsOwnIdAndHash() {
        StreamLens.entries.forEach {
            assertThat(StreamLens.fromId(it.id)).isEqualTo(it)
            assertThat(StreamLens.fromHash("#${it.hash}")).isEqualTo(it)
        }
    }
}
