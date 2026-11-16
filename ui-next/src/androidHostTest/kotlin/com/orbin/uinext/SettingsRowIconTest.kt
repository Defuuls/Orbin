package com.orbin.uinext

import com.google.common.truth.Truth.assertThat
import com.orbin.uinext.screens.settingsRowIcon
import org.junit.Test

/** Rule: neighbouring appearance settings never share a symbol, so each row reads on its own. */
class SettingsRowIconTest {
    @Test
    fun `theme and true black have distinct icons`() {
        assertThat(settingsRowIcon("amoled")).isNotSameInstanceAs(settingsRowIcon("themeMode"))
        assertThat(settingsRowIcon("amoled").name).isNotEqualTo(settingsRowIcon("themeMode").name)
    }
}
