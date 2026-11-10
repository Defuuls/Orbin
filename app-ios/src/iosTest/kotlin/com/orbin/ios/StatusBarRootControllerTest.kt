@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.orbin.ios

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
    fun immersiveRoutesUseLightIconsAndSettingsOrPrivacyCoversRestoreThePalette() {
        val appearance = StatusBarAppearance()
        val content = UIViewController()
        val root = StatusBarRootController(content, appearance)
        val unlocked = LockState(obscured = false)
        val media = Route.Media(ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(1)), 0)

        appearance.setLightIcons(lightStatusBarIcons(Route.Feed, false, false, unlocked))
        assertEquals(UIStatusBarStyleLightContent, root.preferredStatusBarStyle())
        appearance.setLightIcons(lightStatusBarIcons(Route.Settings, false, false, unlocked))
        assertEquals(UIStatusBarStyleDarkContent, root.preferredStatusBarStyle())
        appearance.setLightIcons(lightStatusBarIcons(media, false, false, unlocked))
        assertEquals(UIStatusBarStyleLightContent, root.preferredStatusBarStyle())
        appearance.setLightIcons(lightStatusBarIcons(media, false, false, LockState(obscured = true)))
        assertEquals(UIStatusBarStyleDarkContent, root.preferredStatusBarStyle())
        appearance.setLightIcons(lightStatusBarIcons(Route.Feed, false, true, unlocked))
        assertEquals(UIStatusBarStyleDarkContent, root.preferredStatusBarStyle())
        appearance.setLightIcons(lightStatusBarIcons(Route.Settings, true, false, unlocked))
        assertEquals(UIStatusBarStyleLightContent, root.preferredStatusBarStyle())
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
