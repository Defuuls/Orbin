package com.orbin.app

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.AppIconVariant
import com.orbin.core.model.AppSettings
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the launcher-alias invariants against the source manifest.
 *
 * v57-Arcturus shipped an app that opened Android's App Info screen instead of itself: the
 * manifest's only enabled launcher entry was an alias [AppIconManager] never managed, so the app
 * ended up with two launcher entries and, once the unmanaged one was disabled, with none at all.
 * Every assertion here corresponds to a way that failure could recur.
 */
class AppIconAliasManifestTest {
    private data class ManifestAlias(
        val simpleName: String,
        val enabled: Boolean,
        val isLauncher: Boolean,
        val targetActivity: String,
    )

    private val aliases: List<ManifestAlias> = parseAliases()

    @Test
    fun everyVariantMapsToADistinctAlias() {
        val names = AppIconVariant.entries.map { AppIconAliases.simpleName(it) }

        assertThat(names).hasSize(AppIconVariant.entries.size)
        assertThat(names.toSet()).hasSize(names.size)
        assertThat(names.none { it.isBlank() }).isTrue()
    }

    @Test
    fun everyVariantAliasIsDeclaredInTheManifest() {
        val declared = aliases.map { it.simpleName }.toSet()

        AppIconVariant.entries.forEach { variant ->
            assertThat(declared).contains(AppIconAliases.simpleName(variant))
        }
    }

    /** The v57 regression: an alias in the manifest that no variant owns is never managed. */
    @Test
    fun noManifestAliasIsLeftUnmanaged() {
        val managed = AppIconVariant.entries.map { AppIconAliases.simpleName(it) }.toSet()
        val orphans = aliases.map { it.simpleName }.filterNot { it in managed }

        assertThat(orphans).isEmpty()
    }

    /** Zero enabled entries strands the app with no launcher icon; more than one shows it twice. */
    @Test
    fun exactlyOneAliasIsEnabledInTheManifest() {
        val enabled = aliases.filter { it.enabled }.map { it.simpleName }

        assertThat(enabled).hasSize(1)
    }

    /**
     * A fresh install shows the manifest-enabled alias before any code runs, so it has to be the
     * one backing the default setting or the launcher icon changes on first launch.
     */
    @Test
    fun theEnabledAliasBacksTheDefaultVariant() {
        val enabled = aliases.single { it.enabled }.simpleName

        assertThat(enabled).isEqualTo(AppIconAliases.simpleName(AppSettings.Default.appIconVariant))
    }

    @Test
    fun everyAliasIsALauncherEntryPointingAtMainActivity() {
        aliases.forEach { alias ->
            assertThat(alias.isLauncher).isTrue()
            assertThat(alias.targetActivity).isEqualTo(".MainActivity")
        }
    }

    private fun parseAliases(): List<ManifestAlias> {
        val document =
            DocumentBuilderFactory
                .newInstance()
                .apply { isNamespaceAware = false }
                .newDocumentBuilder()
                .parse(manifestFile())

        val nodes = document.getElementsByTagName("activity-alias")
        return (0 until nodes.length).map { index ->
            val element = nodes.item(index) as Element
            // An absent android:enabled means enabled, matching the platform default.
            val enabled = element.getAttribute("android:enabled").ifEmpty { "true" }.toBoolean()
            ManifestAlias(
                simpleName = element.getAttribute("android:name").substringAfterLast('.'),
                enabled = enabled,
                isLauncher = element.declaresLauncherCategory(),
                targetActivity = element.getAttribute("android:targetActivity"),
            )
        }
    }

    private fun Element.declaresLauncherCategory(): Boolean {
        val categories = getElementsByTagName("category")
        return (0 until categories.length).any { index ->
            val category = categories.item(index) as Element
            category.getAttribute("android:name") == "android.intent.category.LAUNCHER"
        }
    }

    /** Unit tests run with the module directory as the working directory; fall back for IDE runs. */
    private fun manifestFile(): File =
        listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
            .map(::File)
            .firstOrNull(File::exists)
            ?: error("Could not locate the app AndroidManifest.xml from ${File(".").absolutePath}")
}
