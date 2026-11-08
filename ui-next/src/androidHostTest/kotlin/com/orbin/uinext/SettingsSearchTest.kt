package com.orbin.uinext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private val groups =
        listOf(
            "Appearance" to
                listOf(
                    SettingItem("theme", "Theme", "System", SettingKind.CHOICE, options = listOf("Light", "Dark")),
                ),
            "Privacy and safety" to
                listOf(
                    SettingItem("lock", "App lock", "Off", hint = "Authenticate before browsing"),
                    SettingItem("secret", "Password", "hidden-token", SettingKind.TEXT, text = "hidden-token", secret = true),
                ),
        )

    @Test
    fun searchMatchesSectionsOptionsAndHintsIgnoringCaseAndWhitespace() {
        assertEquals(listOf("theme"), ids("  APPEARANCE   dark "))
        assertEquals(listOf("lock"), ids("authenticate"))
        assertEquals(listOf("lock", "secret"), ids("safety"))
    }

    @Test
    fun searchRequiresEveryWordAndOmitsEmptyGroups() {
        assertTrue(filterSettings(groups, "theme lock").isEmpty())
        assertEquals(listOf("Appearance"), filterSettings(groups, "dark").map { it.first })
        assertEquals(groups, filterSettings(groups, "  "))
    }

    @Test
    fun searchNeverReadsSecretValuesOrTextDrafts() {
        assertTrue(filterSettings(groups, "hidden-token").isEmpty())
        assertEquals(listOf("secret"), ids("password"))
    }

    private fun ids(query: String) = filterSettings(groups, query).flatMap { it.second }.map { it.id }
}
