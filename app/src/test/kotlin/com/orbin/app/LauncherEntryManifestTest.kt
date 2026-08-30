package com.orbin.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the one thing that decides whether the app can be opened at all.
 *
 * This has now gone wrong twice. v57-Arcturus shipped with two launcher entries, one of them an
 * alias `AppIconManager` never managed; disabling it left none. 109-Koharu removed the app-icon
 * picker by deleting four of its five aliases and keeping the fifth — and `AppIconManager` had
 * spent every launch writing *explicit* component enabled states, which survive an app update.
 * Anyone who had ever chosen a non-default icon carried an explicit DISABLED on the alias that
 * survived, with the one they were using deleted, so the app had no enabled launcher component
 * and could not be started. The only recovery was a reinstall, which loses the encrypted local
 * database with it.
 *
 * The invariant that prevents both: the launcher entry lives on a plain `<activity>` whose enabled
 * state nothing has ever written, so the manifest is what decides on every install. An
 * `activity-alias` is not that — the aliases are precisely the components whose state was written
 * — so this asserts there are none, rather than trying to enumerate which ones would be safe.
 */
class LauncherEntryManifestTest {
    private data class Component(
        val tag: String,
        val name: String,
        val enabled: Boolean,
        val isLauncher: Boolean,
    )

    private val components: List<Component> = parseComponents()

    @Test
    fun exactlyOneComponentIsALauncherEntry() {
        val launchers = components.filter { it.isLauncher && it.enabled }

        assertThat(launchers.map { it.name }).hasSize(1)
    }

    @Test
    fun theLauncherEntryIsTheActivityItselfRatherThanAnAlias() {
        val launcher = components.single { it.isLauncher && it.enabled }

        assertThat(launcher.tag).isEqualTo("activity")
        assertThat(launcher.name).isEqualTo(".MainActivity")
    }

    /**
     * No aliases at all. An alias is only reachable if its persisted enabled state allows it, and
     * every alias this app has ever declared has had that state written explicitly on real
     * devices — so one reintroduced here is a launcher entry that some existing installs would
     * silently refuse to show.
     */
    @Test
    fun noActivityAliasSurvives() {
        assertThat(components.filter { it.tag == "activity-alias" }).isEmpty()
    }

    private fun parseComponents(): List<Component> {
        val document =
            DocumentBuilderFactory
                .newInstance()
                .apply { isNamespaceAware = false }
                .newDocumentBuilder()
                .parse(manifestFile())

        return listOf("activity", "activity-alias").flatMap { tag ->
            val nodes = document.getElementsByTagName(tag)
            (0 until nodes.length).map { index ->
                val element = nodes.item(index) as Element
                Component(
                    tag = tag,
                    name = element.getAttribute("android:name"),
                    // An absent android:enabled means enabled, matching the platform default.
                    enabled = element.getAttribute("android:enabled").ifEmpty { "true" }.toBoolean(),
                    isLauncher = element.declaresLauncherCategory(),
                )
            }
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
