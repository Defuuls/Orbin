package com.orbin.feature.settings

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.FormFactor
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

    /** The whole surface: preferences, then data, with nothing else on it. */
    @Test
    fun `settings is a short list with nothing hidden behind it`() {
        assertThat(buildModel().groups.map { rows -> rows.second.map { it.id } })
            .containsExactly(
                listOf("hideNsfw", "coverViolent", "themeMode", "amoled", "biometric", "updateOnLaunch"),
                listOf("clearActivity", "clearImageCache", "checkUpdates", "exportBackup", "importBackup"),
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

    /** Only a device with a wide screen offers a column count, and only as many as it allows. */
    @Test
    fun `feed columns show only on foldables and tablets`() {
        fun columnsRow(formFactor: FormFactor) =
            buildModel(formFactor).groups.flatMap { it.second }.firstOrNull { it.id == "feedColumns" }

        assertThat(columnsRow(FormFactor.PHONE)).isNull()
        with(checkNotNull(columnsRow(FormFactor.FOLDABLE))) {
            assertThat(label).isEqualTo("Feed columns when unfolded")
            assertThat(options).containsExactly("1 column", "2 columns", "3 columns").inOrder()
            assertThat(value).isEqualTo("2 columns")
        }
        with(checkNotNull(columnsRow(FormFactor.TABLET))) {
            assertThat(label).isEqualTo("Feed columns")
            assertThat(options).containsExactly("1 column", "2 columns", "3 columns", "4 columns").inOrder()
        }
    }

    private fun allRows() = buildModel().groups.flatMap { it.second }

    /**
     * The registry only reads values off [com.orbin.core.model.AppSettings] and records the view
     * model's setters as closures it never calls here, so a relaxed mock is enough to build it.
     */
    private fun buildModel(formFactor: FormFactor = FormFactor.PHONE) =
        buildSettings(
            settings =
                com.orbin.core.model
                    .AppSettings(),
            vm = io.mockk.mockk(relaxed = true),
            updateState = "Up to date",
            formFactor = formFactor,
        )
}
