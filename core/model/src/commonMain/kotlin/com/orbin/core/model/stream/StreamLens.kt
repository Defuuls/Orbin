package com.orbin.core.model.stream

/**
 * The views of the single-stream design. Every lens re-sorts the same keyed lines, so switching
 * lens never navigates away.
 *
 * [hash] is the stable deep-link fragment. Settings uses `prefs` because `settings` is a name
 * other hosts reserve for themselves.
 */
enum class StreamLens(
    val id: String,
    val label: String,
    val hash: String,
    val hint: String,
) {
    Home("home", "Home", "home", "Followed boards, newest first"),
    Watched("watched", "Watched", "watched", "Threads you track"),
    Feeds("feeds", "Feeds", "feeds", "Boards you can follow"),
    Saved("saved", "Saved", "saved", "Threads kept for later"),
    History("history", "History", "history", "Recently opened"),
    Clips("clips", "Clips", "clips", "Links you set aside, exportable"),
    Settings("settings", "Settings", "prefs", "Appearance and behavior"),
    ;

    companion object {
        /** The lens for [id], or `null` so callers can ignore an unknown value instead of blanking the stream. */
        fun fromId(id: String?): StreamLens? = entries.firstOrNull { it.id == id }

        /** The lens for a URL fragment such as `#prefs` (the leading `#` is optional), or `null`. */
        fun fromHash(hash: String?): StreamLens? {
            val fragment = hash.orEmpty().removePrefix("#")
            return entries.firstOrNull { it.hash == fragment }
        }
    }
}
