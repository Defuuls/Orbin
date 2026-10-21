package com.orbin.uinext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbySettingsTest {
    @Test
    fun disabledSyncExposesOnlyOptIn() {
        val rows = nearbySyncRows(false, false, false, null, emptyList(), null)
        assertEquals(listOf(NearbySettingIds.ENABLED), rows.map { it.id })
    }

    @Test
    fun trustIsExplicitAndSeparateFromRemovingAPairing() {
        val devices = listOf("one" to "My tablet", "two" to "My phone")
        val rows = nearbySyncRows(true, true, false, null, devices, null, trustedIds = setOf("one"))
        val trusted = rows.single { it.id == NearbySettingIds.TRUST_PREFIX + "one" }
        assertEquals(SettingKind.TOGGLE, trusted.kind)
        assertEquals("On", trusted.value)
        assertEquals("Off", rows.single { it.id == NearbySettingIds.TRUST_PREFIX + "two" }.value)
        assertEquals(2, rows.count { it.id.startsWith(NearbySettingIds.FORGET_PREFIX) })
    }

    @Test
    fun codeIsSelectableOnlyWhenPresentAndJoinNeverPrefillsSecrets() {
        val rows = nearbySyncRows(true, true, false, "private-code", listOf("device" to "Other device"), null)
        val code = rows.single { it.id == NearbySettingIds.CODE }
        assertTrue(code.copyable)
        assertEquals("private-code", code.text)
        val join = rows.single { it.id == NearbySettingIds.JOIN }
        assertTrue(join.secret)
        assertTrue(join.text.isEmpty())
        assertTrue(rows.single { it.id == NearbySettingIds.FORGET_PREFIX + "device" }.destructive)
        assertFalse(nearbySyncRows(true, true, false, null, emptyList(), null).any { it.copyable })
    }

    @Test
    fun statusShowsTheCurrentStepWithProgressThenTheResult() {
        val working =
            nearbySyncRows(true, true, true, null, emptyList(), null, step = "Connecting over Wi-Fi to 10.0.0.2…")
                .single { it.id == NearbySettingIds.STATUS }
        assertTrue(working.progress)
        assertEquals("Connecting over Wi-Fi to 10.0.0.2…", working.hint)

        val failed =
            nearbySyncRows(true, true, false, null, emptyList(), "Wi-Fi: couldn't connect.", messageIsError = true)
                .single { it.id == NearbySettingIds.STATUS }
        assertFalse(failed.progress)
        assertTrue(failed.error)
        assertEquals("Failed", failed.value)

        val waiting =
            nearbySyncRows(true, true, false, "code", emptyList(), null).single { it.id == NearbySettingIds.STATUS }
        assertTrue(waiting.progress)
        assertFalse(nearbySyncRows(true, true, false, null, emptyList(), null).any { it.id == NearbySettingIds.STATUS })
    }
}
