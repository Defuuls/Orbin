package com.orbin.app

import com.orbin.core.model.AppColorTheme
import com.orbin.uinext.NextColorTheme
import org.junit.Assert.assertEquals
import org.junit.Test

/** Both app roots adapt storage names at the presentation boundary. */
class ColorThemeAdapterTest {
    @Test
    fun everySavedColorHasExactlyOnePresentationPalette() {
        assertEquals(NextColorTheme.entries, AppColorTheme.entries.map { NextColorTheme.valueOf(it.name) })
    }
}
