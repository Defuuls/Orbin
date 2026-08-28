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
 * the drift loud.
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

    /**
     * Quiet hours only mean anything while there are notifications to be quiet about.
     *
     * Asserted here rather than on a device: the list is lazy, so on screen an absent row and a row
     * merely scrolled out of view look exactly the same.
     */
    @Test
    fun `quiet hours appear only when watch notifications are on`() {
        val on = buildModel(watchNotifications = true).groups.flatMap { it.second }.map { it.id }
        val off = buildModel(watchNotifications = false).groups.flatMap { it.second }.map { it.id }

        assertThat(on).containsAtLeast("quietStart", "quietEnd")
        assertThat(off).containsNoneOf("quietStart", "quietEnd")
    }

    /** HTTPS-only is shown for transparency and must read as on without being switchable. */
    @Test
    fun `https only is stated rather than offered`() {
        val row = allRows().first { it.id == "httpsOnly" }

        assertThat(row.kind).isEqualTo(SettingKind.INFO)
        assertThat(row.value).isEqualTo("Always enforced")
    }

    /**
     * Encrypted DNS has no off switch, so this notice is the only way a user learns their lookups
     * have stopped being private. It has to actually change when the monitor says so.
     */
    @Test
    fun `the dns notice reflects whether lookups are encrypted`() {
        val encrypted = buildModel().groups.flatMap { it.second }.first { it.id == "dnsPrivacy" }
        val fallingBack =
            buildModel(fallback = true).groups.flatMap { it.second }.first { it.id == "dnsPrivacy" }

        assertThat(encrypted.value).isEqualTo("Encrypted")
        assertThat(encrypted.hint).contains("Encrypted DNS is always on")
        assertThat(fallingBack.value).isEqualTo("Not private right now")
        assertThat(fallingBack.hint).contains("system resolver")
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
    private fun buildModel(
        internalUpdater: Boolean = true,
        watchNotifications: Boolean = true,
        fallback: Boolean = false,
    ) = buildSettings(
        settings =
            com.orbin.core.model
                .AppSettings(
                    internalUpdaterEnabled = internalUpdater,
                    threadWatchNotificationsEnabled = watchNotifications,
                ),
        vm = io.mockk.mockk(relaxed = true),
        updateState = "Up to date",
        dnsFallbackActive = fallback,
    )
}
