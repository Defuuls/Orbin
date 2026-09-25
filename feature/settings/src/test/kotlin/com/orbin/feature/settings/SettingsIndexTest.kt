package com.orbin.feature.settings

import com.google.common.truth.Truth.assertThat
import com.orbin.uinext.SettingKind
import org.junit.Test

/** The shape of the settings list: its headings, its row ids and what each row is allowed to be. */
class SettingsIndexTest {
    /**
     * Nothing navigates any more, so no row may be anything but editable where it stands.
     *
     * The kind that used to send you to a category screen is gone from the enum, so this also
     * fails to compile if one is reintroduced — which is the stronger half of the guarantee.
     */
    @Test
    fun `no row sends you anywhere`() {
        val inPlace =
            setOf(SettingKind.TOGGLE, SettingKind.CHOICE, SettingKind.TEXT, SettingKind.ACTION, SettingKind.INFO)
        assertThat(allRows().map { it.kind }.toSet()).containsAnyIn(inPlace)
        assertThat(allRows().filterNot { it.kind in inPlace }).isEmpty()
    }

    /** The whole surface: preferences, then data, then places, with nothing else on it. */
    @Test
    fun `settings is a short list with nothing hidden behind it`() {
        assertThat(buildModel(includePlaces = true).groups.map { rows -> rows.second.map { it.id } })
            .containsExactly(
                listOf("hideNsfw", "themeMode", "amoled", "biometric"),
                listOf("clearActivity", "clearImageCache", "checkUpdates", "exportBackup", "importBackup"),
                listOf("openDownloads", "openSearch"),
            ).inOrder()
    }

    @Test
    fun `the cards carry no headings`() {
        assertThat(buildModel().groups.map { it.first }.toSet()).containsExactly(NO_HEADING)
    }

    /** No heading may end up empty: a heading with nothing under it is a heading you scroll past. */
    @Test
    fun `every heading has rows under it`() {
        val empty = buildModel().groups.filter { it.second.isEmpty() }.map { it.first }
        assertThat(empty).isEmpty()
    }

    /** Two rows cannot share an id: the id is what a search result and a tap both key off. */
    @Test
    fun `no row id appears twice`() {
        val duplicates = allRows().groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertThat(duplicates).isEmpty()
    }

    private fun allRows() = buildModel().groups.flatMap { it.second }

    /**
     * The registry only reads values off [com.orbin.core.model.AppSettings] and records the view
     * model's setters as closures it never calls here, so a relaxed mock is enough to build it.
     */
    private fun buildModel(includePlaces: Boolean = false) =
        buildSettings(
            settings =
                com.orbin.core.model
                    .AppSettings(),
            vm = io.mockk.mockk(relaxed = true),
            updateState = "Up to date",
            includePlaces = includePlaces,
        )
}
