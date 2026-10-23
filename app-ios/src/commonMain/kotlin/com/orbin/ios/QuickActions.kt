package com.orbin.ios

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The home-screen quick actions, as Android's app shortcuts: Feed, Downloads and Boards, each
 * opening its tab. The items are declared in the app's Info.plist; the scene delegate hands the
 * tapped one's type to [perform], at launch or while running, and the app opens [pending].
 */
object QuickActions {
    const val FEED = "io.github.defuuls.orbin.feed"
    const val DOWNLOADS = "io.github.defuuls.orbin.downloads"
    const val BOARDS = "io.github.defuuls.orbin.boards"

    private val _pending = MutableStateFlow<Route?>(null)

    /** The tab a quick action asked for and the app has not opened yet. */
    val pending: StateFlow<Route?> = _pending.asStateFlow()

    /** Takes the quick action [type]; false when it is not one of Orbin's. */
    fun perform(type: String): Boolean {
        val tab = tabFor(type) ?: return false
        _pending.value = tab
        return true
    }

    /** Called once the app has opened [pending]. */
    fun opened() {
        _pending.value = null
    }

    internal fun tabFor(type: String): Route? =
        when (type) {
            FEED -> Route.Feed
            DOWNLOADS -> Route.Downloads
            BOARDS -> Route.Boards
            else -> null
        }
}
