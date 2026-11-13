package com.orbin.ios

/** Coral reader pages follow their palette; uncovered black media chrome keeps light icons. */
internal fun lightStatusBarIcons(
    route: Route,
    darkTheme: Boolean,
    firstRun: Boolean,
    lockState: LockState,
): Boolean = darkTheme || (!firstRun && !lockState.locked && !lockState.obscured && route.hasBlackMediaChrome)

private val Route.hasBlackMediaChrome: Boolean
    get() =
        when (this) {
            Route.Feed, Route.Boards, Route.Watched, Route.Saved, Route.Search,
            Route.Settings, Route.Downloads, Route.History,
            is Route.Catalog, is Route.BoardMedia, is Route.ThreadPage,
            -> false
            is Route.Media, is Route.ThreadVideos -> true
        }
