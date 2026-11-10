package com.orbin.uinext

/** Reader-facing source names, shared by cards, saved items and thread headers. */
fun sourceLabel(source: String): String =
    when (source.trim().lowercase()) {
        "fourchan", "4chan", "4chan (read-only example)" -> "4chan"
        "bbwchan", "bbw chan" -> "BBWChan"
        else -> source.trim()
    }
