package com.orbin.uinext

/** Search display text and options without inspecting text-field contents or secret values. */
internal fun filterSettings(
    groups: List<Pair<String, List<SettingItem>>>,
    query: String,
): List<Pair<String, List<SettingItem>>> {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return groups
    return groups.mapNotNull { (heading, rows) ->
        val matches =
            rows.filter { item ->
                val searchable =
                    listOf(heading, item.label, item.hint.orEmpty())
                        .plus(if (item.secret) emptyList() else item.options + item.value)
                words.all { word -> searchable.any { it.contains(word, ignoreCase = true) } }
            }
        if (matches.isEmpty()) null else heading to matches
    }
}
