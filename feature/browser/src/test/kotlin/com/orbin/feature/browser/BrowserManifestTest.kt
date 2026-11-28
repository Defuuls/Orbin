package com.orbin.feature.browser

import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.link.InAppBrowser
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The browser's isolation rests on these manifest attributes; a change here must fail loudly. */
class BrowserManifestTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val components: List<Element> = parseComponents()

    @Test
    fun `the browser and its data service run in the browser process`() {
        assertThat(components.map { it.getAttributeNS(android, "name") })
            .containsExactly(".BrowserActivity", ".BrowserDataService")
        components.forEach { assertThat(it.getAttributeNS(android, "process")).isEqualTo(InAppBrowser.PROCESS_SUFFIX) }
    }

    @Test
    fun `no other app can open the browser or clear its data`() {
        components.forEach { assertThat(it.getAttributeNS(android, "exported")).isEqualTo("false") }
    }

    @Test
    fun `the browser answers only https links on the in-app action`() {
        val activity = components.single { it.tagName == "activity" }
        val actions = activity.getElementsByTagName("action")
        val data = activity.getElementsByTagName("data")

        assertThat(actions.length).isEqualTo(1)
        assertThat((actions.item(0) as Element).getAttributeNS(android, "name")).isEqualTo(InAppBrowser.ACTION_OPEN)
        assertThat(data.length).isEqualTo(1)
        assertThat((data.item(0) as Element).getAttributeNS(android, "scheme")).isEqualTo("https")
    }

    private fun parseComponents(): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val application = document.getElementsByTagName("application").item(0) as Element
        return (0 until application.childNodes.length)
            .map { application.childNodes.item(it) }
            .filterIsInstance<Element>()
    }
}
