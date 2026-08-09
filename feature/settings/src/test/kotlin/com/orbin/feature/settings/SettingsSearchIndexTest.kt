package com.orbin.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsSearchIndexTest {
    @Test
    fun `matches is case-insensitive against the label`() {
        val entry = SettingsSearchEntry("Lock with biometrics", SettingsSection.PRIVACY)

        assertThat(entry.matches("BIOMETRICS")).isTrue()
        assertThat(entry.matches("lock")).isTrue()
        assertThat(entry.matches("nonexistent")).isFalse()
    }

    @Test
    fun `matches also checks the section title`() {
        val entry = SettingsSearchEntry("Custom user agent", SettingsSection.ADVANCED)

        assertThat(entry.matches("advanced")).isTrue()
    }

    @Test
    fun `the index has no duplicate label-section pairs`() {
        val duplicates =
            settingsSearchIndex
                .groupBy { it.label to it.section }
                .filterValues { it.size > 1 }

        assertThat(duplicates).isEmpty()
    }
}
