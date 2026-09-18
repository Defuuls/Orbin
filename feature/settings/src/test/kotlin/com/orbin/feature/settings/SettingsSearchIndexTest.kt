package com.orbin.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsSearchIndexTest {
    @Test
    fun `matches is case-insensitive against the label`() {
        val entry = SettingsSearchEntry("biometric", "App lock", PRIVACY)

        assertThat(entry.matches("APP LOCK")).isTrue()
        assertThat(entry.matches("lock")).isTrue()
        assertThat(entry.matches("nonexistent")).isFalse()
    }

    @Test
    fun `matches also checks the group heading`() {
        val entry = SettingsSearchEntry("biometric", "App lock", PRIVACY)

        assertThat(entry.matches("privacy")).isTrue()
    }

    @Test
    fun `the index has no duplicate ids`() {
        val duplicates = settingsSearchIndex.groupBy { it.id }.filterValues { it.size > 1 }

        assertThat(duplicates).isEmpty()
    }
}
