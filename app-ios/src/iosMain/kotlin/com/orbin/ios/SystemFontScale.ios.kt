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

private fun iosContentSizeScale(): Float =
    CONTENT_SIZE_SCALES[UIApplication.sharedApplication.preferredContentSizeCategory] ?: 1f

/**
 * Each Dynamic Type size's scale against Large, the system default. Unspecified, and any size a
 * later iOS adds, read as Large.
 */
private val CONTENT_SIZE_SCALES: Map<String?, Float> =
    mapOf(
        UIContentSizeCategoryExtraSmall to 0.82f,
        UIContentSizeCategorySmall to 0.88f,
        UIContentSizeCategoryMedium to 0.94f,
        UIContentSizeCategoryLarge to 1f,
        UIContentSizeCategoryUnspecified to 1f,
        UIContentSizeCategoryExtraLarge to 1.12f,
        UIContentSizeCategoryExtraExtraLarge to 1.23f,
        UIContentSizeCategoryExtraExtraExtraLarge to 1.35f,
        UIContentSizeCategoryAccessibilityMedium to 1.64f,
        UIContentSizeCategoryAccessibilityLarge to 1.95f,
        UIContentSizeCategoryAccessibilityExtraLarge to 2.35f,
        UIContentSizeCategoryAccessibilityExtraExtraLarge to 2.76f,
        UIContentSizeCategoryAccessibilityExtraExtraExtraLarge to 3.12f,
    )
