package com.orbin.uinext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbySettingsTest {
    private val now = 1_000_000L

    private fun rows(ui: NearbyUi) = nearbySyncGroups(ui, now).flatMap { it.second }

    @Test
    fun settingsShowsOneRowThatSummarisesTheScreen() {
        assertEquals("Off", nearbyEntryRow(NearbyUi()).value)
        val two = NearbyUi(enabled = true, devices = listOf(NearbyUiDevice("a", "A"), NearbyUiDevice("b", "B")))
        assertEquals("2 devices", nearbyEntryRow(two).value)
        assertEquals(NearbySettingIds.OPEN, nearbyEntryRow(two).id)
    }

    @Test
    fun turnedOffExplainsWithoutPairingControls() {
        val ids = rows(NearbyUi()).map { it.id }
        assertTrue(NearbySettingIds.ENABLED in ids)
        assertFalse(NearbySettingIds.ADD in ids)
        assertFalse(NearbySettingIds.JOIN in ids)
    }

    @Test
    fun thisDeviceShowsItsAddressForComparison() {
        val ui = NearbyUi(enabled = true, listening = true, localAddresses = listOf("192.168.1.20"), port = 39157)
        val row = rows(ui).single { it.id == NearbySettingIds.THIS_DEVICE }
        assertEquals("Ready", row.value)
        assertTrue(row.hint!!.contains("192.168.1.20"))
        assertTrue(row.hint!!.contains("192.168.1."))
    }

    @Test
    fun trustIsExplicitAndSeparateFromRemovingAPairing() {
        val ui =
            NearbyUi(
                enabled = true,
                listening = true,
                devices = listOf(NearbyUiDevice("one", "My tablet", trusted = true), NearbyUiDevice("two", "My phone")),
            )
        val all = rows(ui)
        val trusted = all.single { it.id == NearbySettingIds.TRUST_PREFIX + "one" }
        assertEquals(SettingKind.TOGGLE, trusted.kind)
        assertEquals("On", trusted.value)
        assertEquals("Off", all.single { it.id == NearbySettingIds.TRUST_PREFIX + "two" }.value)
        assertEquals(2, all.count { it.id.startsWith(NearbySettingIds.FORGET_PREFIX) })
        assertTrue(all.any { it.id == NearbySettingIds.SYNC })
        assertFalse(all.any { it.id == NearbySettingIds.NO_DEVICES })
    }

    @Test
    fun codeIsSelectableOnlyWhenPresentAndJoinNeverPrefillsSecrets() {
        val all = rows(NearbyUi(enabled = true, listening = true, pairingCode = "private-code"))
        val code = all.single { it.id == NearbySettingIds.CODE }
        assertTrue(code.copyable)
        assertEquals("private-code", code.text)
        val join = all.single { it.id == NearbySettingIds.JOIN }
        assertTrue(join.secret)
        assertTrue(join.text.isEmpty())
        assertFalse(rows(NearbyUi(enabled = true)).any { it.id == NearbySettingIds.CODE })
    }

    @Test
    fun statusShowsTheCurrentStepWithProgressThenTheResult() {
        val working =
            rows(NearbyUi(enabled = true, busy = true, step = "Connecting over Wi-Fi to 10.0.0.2…"))
                .single { it.id == NearbySettingIds.STATUS }
        assertTrue(working.progress)
        assertEquals("Connecting over Wi-Fi to 10.0.0.2…", working.hint)

        val failed =
            rows(NearbyUi(enabled = true, message = "Wi-Fi: couldn't connect.", messageIsError = true))
                .single { it.id == NearbySettingIds.STATUS }
        assertFalse(failed.progress)
        assertTrue(failed.error)
        assertEquals("Failed", failed.value)

        val waiting = rows(NearbyUi(enabled = true, pairingCode = "code")).single { it.id == NearbySettingIds.STATUS }
        assertTrue(waiting.progress)
        assertFalse(rows(NearbyUi(enabled = true)).any { it.id == NearbySettingIds.STATUS })
    }

    @Test
    fun activityLogIsCopyableNewestFirst() {
        val ui =
            NearbyUi(
                enabled = true,
                log = listOf(NearbyUiLogEntry(now, "first"), NearbyUiLogEntry(now, "second", error = true)),
            )
        val log = rows(ui).single { it.id == NearbySettingIds.LOG }
        assertTrue(log.copyable)
        assertTrue(log.text.indexOf("second") < log.text.indexOf("first"))
        assertTrue(log.text.startsWith("✕"))
    }
}
