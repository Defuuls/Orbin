package com.orbin.ios

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** White Coral reader bars use dark icons; only the full-screen black media chrome uses light. */
class ReaderStatusBarTest {
    @Test
    fun everyPrimaryAndChildReaderRouteUsesDarkIconsWithTheLightPalette() {
        readerRoutes.forEach { route ->
            assertFalse(
                lightStatusBarIcons(route, darkTheme = false, firstRun = false, lockState = unlocked),
                "$route must keep readable status icons over the reader frame",
            )
        }
    }

    @Test
    fun fullScreenMediaLoadingErrorAndReadyRoutesKeepLightIconsOverTheirBlackChrome() {
        mediaRoutes.forEach { route ->
            assertTrue(
                lightStatusBarIcons(route, darkTheme = false, firstRun = false, lockState = unlocked),
                "$route keeps black chrome while loading, failed, empty or ready",
            )
        }
    }

    @Test
    fun onboardingAndPrivacyCoversUseDarkIconsWithTheLightPalette() {
        (readerRoutes + mediaRoutes).forEach { route ->
            assertFalse(
                lightStatusBarIcons(route, darkTheme = false, firstRun = true, lockState = unlocked),
                "Onboarding over $route uses its own light ground",
            )
            listOf(LockState(locked = true, obscured = false), LockState(obscured = true)).forEach { covered ->
                assertFalse(
                    lightStatusBarIcons(route, darkTheme = false, firstRun = false, lockState = covered),
                    "The privacy cover over $route uses its own light ground",
                )
            }
        }
    }

    @Test
    fun readerOnboardingAndPrivacyCoversKeepLightIconsWithTheDarkPalette() {
        (readerRoutes + mediaRoutes).forEach { route ->
            listOf(unlocked, LockState(locked = true), LockState(obscured = true)).forEach { lock ->
                listOf(false, true).forEach { firstRun ->
                    assertTrue(
                        lightStatusBarIcons(route, darkTheme = true, firstRun = firstRun, lockState = lock),
                        "The dark palette must keep light status icons for $route, setup=$firstRun, lock=$lock",
                    )
                }
            }
        }
    }

    private companion object {
        val unlocked = LockState(obscured = false)
        val board = SiteBoard(ProviderId("fourchan"), "4chan", Board(BoardId("g"), "Technology"))
        val thread = ThreadKey(board.provider, board.board.id, ThreadId(7))
        val readerRoutes =
            listOf(
                Route.Feed,
                Route.Boards,
                Route.Watched,
                Route.Saved,
                Route.Search,
                Route.Settings,
                Route.Downloads,
                Route.History,
                Route.Catalog(board),
                Route.BoardMedia(board),
                Route.ThreadPage(thread),
            )
        val mediaRoutes = listOf(Route.Media(thread, index = 0), Route.ThreadVideos(thread))
    }
}
