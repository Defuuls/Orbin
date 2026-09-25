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

    /** Three headings, in the order the list draws them. */
    @Test
    fun `the list is the three headings and nothing else`() {
        assertThat(buildModel().groups.map { it.first }).containsExactly(GENERAL, DISPLAY, PRIVACY).inOrder()
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

    /** The updater's own check only exists while the updater does. */
    @Test
    fun `the update check appears only when in-app updates are on`() {
        assertThat(rowIds(internalUpdater = true)).contains("checkUpdates")
        assertThat(rowIds(internalUpdater = false)).doesNotContain("checkUpdates")
    }

    private fun rowIds(internalUpdater: Boolean) =
        buildModel(internalUpdater).groups.flatMap { it.second }.map { it.id }

    private fun allRows() = buildModel().groups.flatMap { it.second }

    /**
     * The registry only reads values off [com.orbin.core.model.AppSettings] and records the view
     * model's setters as closures it never calls here, so a relaxed mock is enough to build it.
     */
    private fun buildModel(internalUpdater: Boolean = true) =
        buildSettings(
            settings =
                com.orbin.core.model
                    .AppSettings(internalUpdaterEnabled = internalUpdater),
            vm = io.mockk.mockk(relaxed = true),
            updateState = "Up to date",
        )
}
