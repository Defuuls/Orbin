package com.orbin.feature.settings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * [SettingsStrings] read straight from this module's `values/strings.xml`, so a JVM test sees the
 * text the app shows without a device. Resource ids are matched by name through the generated R.
 */
internal object ResourceFileStrings : SettingsStrings {
    private val byName: Map<String, String> by lazy {
        val document =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(File("src/main/res/values/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent.replace("\\'", "'")
        }
    }

    private val names: Map<Int, String> by lazy {
        R.string::class.java.fields.associate { it.getInt(null) to it.name }
    }

    override fun get(
        id: Int,
        vararg args: Any,
    ): String {
        val text = byName.getValue(names.getValue(id))
        return if (args.isEmpty()) text else String.format(text, *args)
    }
}
