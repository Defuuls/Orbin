package com.orbin.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.FormFactor
import com.orbin.domain.repository.WhatsNewRepository
import com.orbin.uinext.LockScreen
import com.orbin.uinext.NextColorTheme
import com.orbin.uinext.NextError
import com.orbin.uinext.NextLoading
import com.orbin.uinext.NextPlatform
import com.orbin.uinext.NextTheme
import com.orbin.uinext.OnboardingBoard
import com.orbin.uinext.OnboardingBoards
import com.orbin.uinext.OnboardingScreen
import com.orbin.uinext.WhatsNewOnUpdate
import com.orbin.uinext.colorThemePalette
import com.orbin.uinext.next
import kotlinx.coroutines.flow.map

/**
 * The whole iOS app: the shared `ui-next` screens over [browser]. Back is the system edge swipe,
 * which Compose Multiplatform delivers to [NavigationBackHandler] on iOS.
 */
@Composable
fun OrbinApp(
    browser: Browser,
    lock: AppLock,
    downloads: MediaDownloads,
    backup: IosBackup,
    formFactor: FormFactor = FormFactor.PHONE,
    version: AppVersion = AppVersion(),
    whatsNew: WhatsNewRepository? = null,
    pages: ThreadPages? = null,
    onStatusBarAppearance: (lightIcons: Boolean) -> Unit = {},
) {
    val backStack by browser.backStack.collectAsState()
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = backStack.size > 1,
        onBackCompleted = { browser.back() },
    )

    val settings by browser.settings.current.collectAsState()
    val lockState by lock.state.collectAsState()
    val firstRun by browser.firstRun.state.collectAsState()
    val readerReady = firstRun.initialized && !firstRun.needed

    // A home-screen quick action opens its tab, as an Android app shortcut does.
    val quickAction by QuickActions.pending.collectAsState()
    LaunchedEffect(quickAction, lockState.locked, lockState.obscured, readerReady) {
        if (!lockState.locked && !lockState.obscured && readerReady) {
            quickAction?.let { tab ->
                if (tab == Route.Downloads) browser.open(tab) else browser.openTab(tab)
                QuickActions.opened()
            }
        }
    }
    val fontScale = rememberSystemFontScale()
    val dark =
        when (settings.themeMode) {
            AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
        }
    val palette = colorThemePalette(NextColorTheme.valueOf(settings.colorTheme.name), dark, settings.amoled)
    val lightIcons = lightStatusBarIcons(backStack.last(), palette.dark, firstRun.needed, lockState)
    SideEffect { onStatusBarAppearance(lightIcons) }
    NextTheme(
        darkTheme = palette.dark,
        amoled = palette.amoled,
        palette = palette,
        fontScale = fontScale,
        platform = NextPlatform.IOS,
    ) {
        // Each screen's scroll position outlives it being covered or left, as on Android.
        val stateHolder = rememberSaveableStateHolder()
        val routeStates = remember { RouteStates() }
        LaunchedEffect(backStack) { routeStates.visit(backStack).forEach(stateHolder::removeState) }
        val readerContent: @Composable (Boolean) -> Unit = { covered ->
            Box(Modifier.fillMaxSize()) {
                if (!lockState.locked && !firstRun.initialized) {
                    NextLoading(Modifier.fillMaxSize())
                } else if (!lockState.locked && firstRun.needed) {
                    OnboardingDestination(browser)
                } else if (!lockState.locked) {
                    val route = backStack.last()
                    val boardReader = boardReaderRoute(backStack)
                    if (boardReader != null) {
                        BoardReaderDestination(browser, boardReader, downloads, pages, stateHolder)
                    } else {
                        stateHolder.SaveableStateProvider(route.stateKey) {
                            Destination(
                                browser,
                                lock,
                                downloads,
                                backup,
                                formFactor,
                                version,
                                route,
                                pages,
                                playbackAllowed = !covered && !lockState.obscured,
                            )
                        }
                    }
                }
                LockCover(lock, lockState)
            }
        }
        // This release's notes, once after an update, never over the lock.
        if (whatsNew != null) {
            WhatsNewOnUpdate(
                whatsNew.lastSeen,
                whatsNew::markSeen,
                visible = !lockState.locked && !lockState.obscured && readerReady,
                content = readerContent,
            )
        } else {
            readerContent(false)
        }
    }
}

/** First run: Android's board picker, over the active site's boards. */
@Composable
private fun OnboardingDestination(browser: Browser) {
    val setup by browser.firstRun.state.collectAsState()
    val boards by browser.boards.collectAsState()
    val followed by browser.followed.collectAsState()
    val site by browser.activeSite.collectAsState()
    val onSite =
        remember(boards, site) {
            (boards as? Load.Ready)?.value.orEmpty().filter { it.provider == site }
        }
    val byId = remember(onSite) { onSite.associateBy { it.board.id.value } }
    OnboardingScreen(
        boards =
            remember(boards, onSite) {
                when (val loaded = boards) {
                    Load.Loading -> OnboardingBoards.Loading
                    is Load.Failed -> OnboardingBoards.Failed(loaded.message)
                    is Load.Ready ->
                        OnboardingBoards.Ready(
                            onSite.map {
                                OnboardingBoard(
                                    it.board.id.value,
                                    it.board.title,
                                    it.board.description,
                                    it.board.isNsfw,
                                )
                            },
                        )
                }
            },
        following =
            remember(followed, site) {
                followed.filter { it.provider == site }.mapTo(mutableSetOf()) { it.board.value }
            },
        sites = remember { browser.sites.map { it.name } },
        selectedSite = browser.sites.indexOfFirst { it.id == site },
        onSelectSite = { index -> browser.selectSite(browser.sites[index].id) },
        onFollow = { id, follow -> byId[id]?.let { browser.setFollowed(it, follow) } },
        onRetry = browser::retry,
        onStart = { browser.completeFirstRun() },
        totalFollowing = followed.size,
        isSaving = setup.isSaving,
        errorMessage = setup.errorMessage,
        canStart = setup.canStart,
    )
}

@Composable
private fun Destination(
    browser: Browser,
    lock: AppLock,
    downloads: MediaDownloads,
    backup: IosBackup,
    formFactor: FormFactor,
    version: AppVersion,
    route: Route,
    pages: ThreadPages?,
    playbackAllowed: Boolean = true,
) {
    when (route) {
        Route.Feed -> FeedDestination(browser, formFactor, playbackAllowed)
        Route.Boards -> BoardsDestination(browser)
        Route.Downloads -> DownloadsDestination(browser, downloads)
        Route.Saved, Route.History, Route.Watched -> ThreadCollectionsDestination(browser, route)
        Route.Search -> SearchDestination(browser)
        Route.Settings -> SettingsDestination(browser, lock, backup, formFactor, version)
        is Route.Catalog -> CatalogDestination(browser, route.board)
        is Route.BoardMedia -> BoardMediaDestination(browser, route.board)
        is Route.ThreadPage -> ThreadDestination(browser, downloads, pages)
        is Route.Media -> MediaDestination(browser, downloads, route)
        is Route.ThreadVideos -> ThreadVideosDestination(browser, downloads, route)
    }
}

/** The app lock over everything: the lock screen while locked, a blank cover while hidden. */
@Composable
private fun LockCover(
    lock: AppLock,
    state: LockState,
) {
    if (!state.locked && !state.obscured) return
    // Taps stop here rather than reaching the app underneath.
    Box(Modifier.fillMaxSize().background(next.background).pointerInput(Unit) {}) {
        if (state.locked) LockScreen(message = state.message, unlocking = state.unlocking, onUnlock = { lock.unlock() })
    }
}

@Composable
internal fun <T> Loaded(
    load: Load<T>,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    when (load) {
        Load.Loading -> NextLoading(Modifier.fillMaxSize())
        is Load.Failed -> NextError(load.message, onRetry = onRetry)
        is Load.Ready -> content(load.value)
    }
}
