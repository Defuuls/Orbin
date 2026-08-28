package com.orbin.app.command

import com.orbin.feature.settings.SettingsSection

/**
 * Something the command surface can send you to, or do.
 *
 * Deliberately one type across four very different kinds of thing. The whole argument of the
 * command surface is that a board, a thread you were reading, a setting and an action are the same
 * shape to the person typing: a name, and what happens when you pick it.
 */
sealed interface CommandTarget {
    val label: String
    val hint: String
    val kind: String

    /** The text a query is matched against. Broader than the label, so "media" finds Media & Playback settings. */
    val haystack: String get() = "$label $hint"

    data class OpenBoard(
        override val label: String,
        override val hint: String,
        val provider: String,
        val board: String,
        val title: String,
    ) : CommandTarget {
        override val kind: String get() = "board"
    }

    data class OpenThread(
        override val label: String,
        override val hint: String,
        val provider: String,
        val board: String,
        val thread: Long,
    ) : CommandTarget {
        override val kind: String get() = "thread"
    }

    data class OpenSetting(
        override val label: String,
        override val hint: String,
        val section: SettingsSection,
    ) : CommandTarget {
        override val kind: String get() = "setting"
    }

    data class Go(
        override val label: String,
        override val hint: String,
        val destination: CommandDestination,
    ) : CommandTarget {
        override val kind: String get() = "go"
    }

    data class Act(
        override val label: String,
        override val hint: String,
        val action: CommandAction,
    ) : CommandTarget {
        override val kind: String get() = "do"
    }
}

/** A place the command surface can navigate to that is not a board, thread or setting. */
enum class CommandDestination {
    GALLERY,
    ALL_MEDIA,
    BOARDS,
    SUBSCRIPTIONS,
    HISTORY,
    DOWNLOADS,
    SEARCH,
    SETTINGS,
    CLASSIC_FEED,
}

/** Something that happens where you are, rather than somewhere you go. */
enum class CommandAction {
    REFRESH_FEED,
    SCROLL_TO_TOP,
    LOCK_NOW,
}

/**
 * The destinations and actions that exist regardless of what is loaded.
 *
 * The three actions are the ones the tablet feed dock used to carry as icon buttons, and the
 * phone feed's top bar carried as an overflow menu. Neither surface exists in the new interface,
 * so they live here, where they can be typed rather than hunted for.
 */
internal fun staticTargets(): List<CommandTarget> =
    listOf(
        CommandTarget.Go("Gallery", "Browse saved and downloaded media", CommandDestination.GALLERY),
        CommandTarget.Go("All media", "Every file from every board you follow", CommandDestination.ALL_MEDIA),
        CommandTarget.Go("All boards", "Browse and subscribe", CommandDestination.BOARDS),
        CommandTarget.Go("Subscriptions", "Boards in your feed", CommandDestination.SUBSCRIPTIONS),
        CommandTarget.Go("History", "Threads you have read", CommandDestination.HISTORY),
        CommandTarget.Go("Downloads", "Files saved to this device", CommandDestination.DOWNLOADS),
        CommandTarget.Go("Search", "Search threads across your boards", CommandDestination.SEARCH),
        CommandTarget.Go("Settings", "All 59 of them", CommandDestination.SETTINGS),
        CommandTarget.Go("Classic feed", "The previous feed, grouped by board", CommandDestination.CLASSIC_FEED),
        CommandTarget.Act("Refresh feed", "Reload every subscribed board", CommandAction.REFRESH_FEED),
        CommandTarget.Act("Scroll to top", "Back to the newest thread", CommandAction.SCROLL_TO_TOP),
        CommandTarget.Act("Lock Orbin", "Lock now, without waiting for the timeout", CommandAction.LOCK_NOW),
    )

/**
 * Narrows the catalogue to what was typed.
 *
 * An empty query is not an empty list: it is the places and actions, which is what someone who
 * opened the surface without knowing what they wanted is looking for. Ranking puts a label that
 * starts with the query above one that merely contains it, so typing "his" reaches History rather
 * than every board whose description happens to contain those letters.
 */
internal fun filterCommands(
    targets: List<CommandTarget>,
    query: String,
): List<CommandTarget> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return targets.filter { it is CommandTarget.Go || it is CommandTarget.Act }
    return targets
        .filter { target -> target.haystack.contains(trimmed, ignoreCase = true) }
        .sortedWith(
            compareBy(
                { target -> if (target.label.startsWith(trimmed, ignoreCase = true)) 0 else 1 },
                { target -> if (target.label.contains(trimmed, ignoreCase = true)) 0 else 1 },
                { target -> target.label.length },
            ),
        )
}
