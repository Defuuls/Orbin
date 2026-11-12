@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.orbin.ios

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.additionalSafeAreaInsets
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusBarRootControllerTest {
    @Test
    fun readerRoutesUseLightIconsAndPrivacyOrFirstRunCoversRestoreThePalette() {
        val appearance = StatusBarAppearance()
        val content = UIViewController()
        val root = StatusBarRootController(content, appearance)
        val unlocked = LockState(obscured = false)
        val media = Route.Media(ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(1)), 0)
        val board = SiteBoard(media.thread.provider, "4chan", Board(media.thread.board, "Technology"))
        val routes =
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
                Route.ThreadPage(media.thread),
                media,
                Route.ThreadVideos(media.thread),
            )

        routes.forEach { route ->
            // The reader app bar extends its green/dark ground beneath the status bar.
            appearance.setLightIcons(lightStatusBarIcons(route, false, false, unlocked))
            assertEquals(UIStatusBarStyleLightContent, root.preferredStatusBarStyle(), "$route reader bar")
            appearance.setLightIcons(lightStatusBarIcons(route, false, true, unlocked))
            assertEquals(UIStatusBarStyleDarkContent, root.preferredStatusBarStyle(), "$route light setup")
            listOf(LockState(locked = true, obscured = false), LockState(obscured = true)).forEach { cover ->
                appearance.setLightIcons(lightStatusBarIcons(route, false, false, cover))
                assertEquals(UIStatusBarStyleDarkContent, root.preferredStatusBarStyle(), "$route light $cover")
            }
            listOf(unlocked, LockState(locked = true, obscured = false), LockState(obscured = true)).forEach { cover ->
                listOf(false, true).forEach { setup ->
                    appearance.setLightIcons(lightStatusBarIcons(route, true, setup, cover))
                    assertEquals(
                        UIStatusBarStyleLightContent,
                        root.preferredStatusBarStyle(),
                        "$route dark $cover setup=$setup",
                    )
                }
            }
        }
        assertEquals(UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified, root.overrideUserInterfaceStyle)
        assertEquals(UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified, content.overrideUserInterfaceStyle)
    }

    @Test
    fun composeContainerRemainsTheWindowRootAndItsChildFillsResizedBounds() {
        val content = UIViewController()
        val root = StatusBarRootController(content, StatusBarAppearance())
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 390.0, 844.0))
        window.rootViewController = root
        window.makeKeyAndVisible()
        try {
            root.view.layoutIfNeeded()
            assertEquals(root.objcPtr(), content.parentViewController.objcPtr())
            assertEquals(window.objcPtr(), content.view.window.objcPtr())
            assertEquals(390.0, content.view.frame.useContents { size.width })
            assertEquals(844.0, content.view.frame.useContents { size.height })
            assertEquals(root.view.safeAreaInsets.useContents { top }, content.view.safeAreaInsets.useContents { top })
            assertEquals(
                root.view.safeAreaInsets.useContents { bottom },
                content.view.safeAreaInsets.useContents { bottom },
            )
            root.view.setBounds(CGRectMake(0.0, 0.0, 640.0, 480.0))
            root.viewDidLayoutSubviews()
            assertEquals(640.0, content.view.frame.useContents { size.width })
            assertEquals(480.0, content.view.frame.useContents { size.height })
            assertEquals(0.0, content.additionalSafeAreaInsets.useContents { top })
            assertEquals(0.0, content.additionalSafeAreaInsets.useContents { bottom })
        } finally {
            window.hidden = true
            window.rootViewController = null
        }
    }
}
