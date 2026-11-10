package com.orbin.ios

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleDefault
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

/** Controller-owned style, independent of UIKit traits and the reader's persisted color scheme. */
@OptIn(ExperimentalForeignApi::class)
internal class StatusBarAppearance {
    var style: UIStatusBarStyle = UIStatusBarStyleDefault
        private set

    var onChanged: (() -> Unit)? = null

    fun setLightIcons(light: Boolean) {
        val next = if (light) UIStatusBarStyleLightContent else UIStatusBarStyleDarkContent
        if (style == next) return
        style = next
        onChanged?.invoke()
    }
}

/**
 * The window's actual UIKit root owns status-bar appearance. The Compose child fills its bounds
 * and receives normal containment, appearance, rotation, and safe-area propagation.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
internal class StatusBarRootController(
    private val content: UIViewController,
    private val appearance: StatusBarAppearance,
) : UIViewController(nibName = null, bundle = null) {
    init {
        // Compose retains its callback. Keep that callback from retaining this native controller.
        val owner = WeakReference(this)
        appearance.onChanged = { owner.get()?.setNeedsStatusBarAppearanceUpdate() }
    }

    override fun preferredStatusBarStyle(): UIStatusBarStyle = appearance.style

    override fun viewDidLoad() {
        super.viewDidLoad()
        addChildViewController(content)
        content.view.setFrame(view.bounds)
        content.view.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        view.addSubview(content.view)
        content.didMoveToParentViewController(this)
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        content.view.setFrame(view.bounds)
    }
}
