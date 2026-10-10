package com.orbin.uinext

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WhatsNewTest {
    private val notes = ReleaseNotes("165-Currant", listOf(ReleaseNoteSection("Added", listOf("Thread sync"))))

    @Test
    fun showsOnceForANewRelease() {
        assertThat(shouldShowWhatsNew(notes, lastSeen = "164-Cranberry")).isTrue()
        assertThat(shouldShowWhatsNew(notes, lastSeen = null)).isTrue()
        assertThat(shouldShowWhatsNew(notes, lastSeen = "165-Currant")).isFalse()
    }

    @Test
    fun neverShowsEmptyNotes() {
        assertThat(shouldShowWhatsNew(ReleaseNotes("165-Currant", emptyList()), lastSeen = null)).isFalse()
    }

    @Test
    fun buildCarriesThisReleasesChangelogSection() {
        // Generated from CHANGELOG.md for gradle.properties' orbin.versionName.
        assertThat(CurrentReleaseNotes.version).isNotEmpty()
    }
}
