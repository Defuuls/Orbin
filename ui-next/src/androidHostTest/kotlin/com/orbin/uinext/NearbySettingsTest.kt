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
}
