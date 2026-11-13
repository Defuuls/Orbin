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

    @Test
    fun `settings groups keep every preference editable in place`() {
        assertThat(buildModel().groups.map { rows -> rows.second.map { it.id } })
            .containsExactly(
                listOf("themeMode", "amoled", "catalogMode"),
                listOf(
                    "hideNsfw",
                    "coverViolent",
                    "biometric",
                    "screenPrivacy",
                    "dohProvider",
                    "saveSearchHistory",
                    "saveBrowsingHistory",
                    "strictDns",
                ),
                listOf("clearActivity", "clearImageCache", "exportBackup", "importBackup"),
                listOf("updateOnLaunch", "checkUpdates"),
            ).inOrder()
    }

    @Test
    fun `settings have named sections for each task`() {
        assertThat(buildModel().groups.map { it.first })
            .containsExactly("Appearance", "Privacy and safety", "Data and storage", "Updates")
            .inOrder()
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

    @Test
    fun `appearance has only brightness AMOLED and catalog view`() {
        val appearance = buildModel().groups.single { it.first == "Appearance" }.second
        assertThat(appearance.map { it.label }).containsExactly("Theme", "AMOLED black", "Catalog view").inOrder()
        assertThat(appearance.map { it.id }).containsExactly("themeMode", "amoled", "catalogMode").inOrder()
    }

    @Test
    fun `catalog view defaults to grid with list and card choices`() {
        val catalog = allRows().single { it.id == "catalogMode" }
        assertThat(catalog.kind).isEqualTo(SettingKind.CHOICE)
        assertThat(catalog.options).containsExactly("List", "Grid", "Card").inOrder()
        assertThat(catalog.value).isEqualTo("Grid")
        assertThat(catalog.selected).isEqualTo(1)
    }

    /**
     * The strict DNS row is the only place the app says its lookups stopped being private, so its
     * hint has to change to a warning while that is happening — and drop it once strict is on.
     */
    @Test
    fun `the strict DNS row warns while lookups fall back`() {
        fun strictRow(dns: DnsRowState) =
            checkNotNull(buildModel(dns = dns).groups.flatMap { it.second }.firstOrNull { it.id == "strictDns" })

        with(strictRow(DnsRowState())) {
            assertThat(label).isEqualTo("Strict encrypted DNS")
            assertThat(kind).isEqualTo(SettingKind.TOGGLE)
            assertThat(hint).contains("Turn this on to stop loading instead")
        }
        assertThat(strictRow(DnsRowState(fallbackActive = true)).hint)
            .contains("Encrypted DNS is blocked on this network")
        assertThat(strictRow(DnsRowState(strict = true, fallbackActive = true)).hint)
            .contains("never falls back")
    }

    private fun allRows() = buildModel().groups.flatMap { it.second }

    /**
     * The registry only reads values off [com.orbin.core.model.AppSettings] and records the view
     * model's setters as closures it never calls here, so a relaxed mock is enough to build it.
     */
    private fun buildModel(dns: DnsRowState = DnsRowState()) =
        buildSettings(
            settings =
                com.orbin.core.model
                    .AppSettings(),
            vm = io.mockk.mockk(relaxed = true),
            updateState = "Up to date",
            strings = ResourceFileStrings,
            dns = dns,
        )
}
