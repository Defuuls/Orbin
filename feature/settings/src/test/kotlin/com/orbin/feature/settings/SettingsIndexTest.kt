package com.orbin.feature.settings

import com.google.common.truth.Truth.assertThat
import com.orbin.uinext.SettingKind
import org.junit.Test

/**
 * The search index and the settings list have to name the same rows.
 *
 * They are two lists because the index is built without live settings or a view model, and the
 * previous version of that split is exactly what broke: the index named *screens*, so typing a
 * setting's name opened a category screen rather than the setting. Now it names row ids, and an id
 * that matches nothing would scroll the list nowhere at all — silently. This is the test that makes
 * the drift loud, in both directions: an index entry for a row the list no longer offers is the
 * same bug wearing the other hat.
 */
class SettingsIndexTest {
    @Test
    fun `every indexed id exists in the settings list`() {
        val ids = allRows().map { it.id }.toSet()
        val missing = settingsSearchIndex.map { it.id }.filterNot { it in ids }
        assertThat(missing).isEmpty()
    }

    @Test
    fun `every row in the settings list is indexed`() {
        val indexed = settingsSearchIndex.map { it.id }.toSet()
        val missing = allRows().map { it.id }.filterNot { it in indexed }
        assertThat(missing).isEmpty()
    }

    @Test
    fun `an indexed entry carries the heading its row is drawn under`() {
        val headings = buildModel().groups.flatMap { (heading, rows) -> rows.map { it.id to heading } }.toMap()
        val wrong = settingsSearchIndex.filter { headings[it.id] != null && headings[it.id] != it.group }
        assertThat(wrong).isEmpty()
    }

    @Test
    fun `an indexed entry carries the label its row is drawn with`() {
        val labels = allRows().associate { it.id to it.label }
        val wrong = settingsSearchIndex.filter { labels[it.id] != null && labels[it.id] != it.label }
        assertThat(wrong).isEmpty()
    }

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
