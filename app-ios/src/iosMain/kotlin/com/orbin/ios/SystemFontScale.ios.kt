package com.orbin.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityMedium
import platform.UIKit.UIContentSizeCategoryExtraExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryExtraLarge
import platform.UIKit.UIContentSizeCategoryExtraSmall
import platform.UIKit.UIContentSizeCategoryLarge
import platform.UIKit.UIContentSizeCategoryMedium
import platform.UIKit.UIContentSizeCategorySmall
import platform.UIKit.UIContentSizeCategoryUnspecified
import platform.darwin.NSObjectProtocol

/**
 * Maps UIKit Dynamic Type into [com.orbin.uinext.NextTheme]'s fontScale.
 *
 * Large (the system default) is 1f. Accessibility sizes step up aggressively so Orbin body text
 * remains readable when the reader enlarges text system-wide.
 */
@Composable
internal actual fun rememberSystemFontScale(): Float {
    var scale by remember { mutableFloatStateOf(iosContentSizeScale()) }
    DisposableEffect(Unit) {
        val observer: NSObjectProtocol =
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = platform.UIKit.UIContentSizeCategoryDidChangeNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ ->
                scale = iosContentSizeScale()
            }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }
    return scale
}

private fun iosContentSizeScale(): Float {
    val category = UIApplication.sharedApplication.preferredContentSizeCategory
    return when (category) {
        UIContentSizeCategoryExtraSmall -> 0.82f
        UIContentSizeCategorySmall -> 0.88f
        UIContentSizeCategoryMedium -> 0.94f
        UIContentSizeCategoryLarge, UIContentSizeCategoryUnspecified -> 1f
        UIContentSizeCategoryExtraLarge -> 1.12f
        UIContentSizeCategoryExtraExtraLarge -> 1.23f
        UIContentSizeCategoryExtraExtraExtraLarge -> 1.35f
        UIContentSizeCategoryAccessibilityMedium -> 1.64f
        UIContentSizeCategoryAccessibilityLarge -> 1.95f
        UIContentSizeCategoryAccessibilityExtraLarge -> 2.35f
        UIContentSizeCategoryAccessibilityExtraExtraLarge -> 2.76f
        UIContentSizeCategoryAccessibilityExtraExtraExtraLarge -> 3.12f
        else -> 1f
    }
}
