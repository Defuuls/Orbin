package com.orbin.provider.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CodePointsTest {
    @Test
    fun `matches Character toChars for every code point`() {
        val mismatches =
            (0..MAX_CODE_POINT).filter { codePoint ->
                codePointToString(codePoint) !=
                    String(Character.toChars(codePoint))
            }
        assertThat(mismatches).isEmpty()
    }

    @Test
    fun `constants match java lang Character`() {
        assertThat(MAX_CODE_POINT).isEqualTo(Character.MAX_CODE_POINT)
        assertThat(SURROGATE_CODE_UNITS).isEqualTo(Character.MIN_SURROGATE.code..Character.MAX_SURROGATE.code)
    }
}
