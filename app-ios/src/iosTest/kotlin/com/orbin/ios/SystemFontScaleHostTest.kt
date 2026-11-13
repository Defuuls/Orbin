@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.orbin.ios

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryDidChangeNotification
import platform.UIKit.UIContentSizeCategoryLarge
import platform.UIKit.UIContentSizeCategoryNewValueKey
import platform.UIKit.UIContentSizeCategorySmall
import platform.UIKit.UIFont
import platform.UIKit.UIFontTextStyleBody
import platform.UIKit.UIWindow
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SystemFontScaleHostTest {
    @Test
    fun categoryNotificationsAndHostDensityChangesScalePlatformTextOnce() {
        val visible = mutableStateOf(true)
        val hostScale = mutableFloatStateOf(0.85f)
        var mounted = false
        var bridge: Bridge? = null
        var layout: TextLayoutResult? = null
        val root =
            ComposeUIViewController(configure = { enforceStrictPlistSanityCheck = false }) {
                if (visible.value) {
                    DisposableEffect(Unit) {
                        mounted = true
                        onDispose { mounted = false }
                    }
                    val nativeDensity = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(nativeDensity.density, hostScale.floatValue),
                    ) {
                        val ambient = LocalDensity.current
                        val multiplier = rememberSystemFontScale()
                        SideEffect { bridge = Bridge(ambient.density, ambient.fontScale, multiplier) }
                        CompositionLocalProvider(
                            LocalDensity provides Density(ambient.density, ambient.fontScale * multiplier),
                        ) {
                            // Default platform fonts keep this standalone host independent of
                            // the application resource bundle and the reader's Roboto family.
                            BasicText(
                                text = "Text",
                                style = TextStyle(fontSize = 14.sp),
                                onTextLayout = { layout = it },
                            )
                        }
                    }
                }
            }
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 390.0, 844.0))
        window.rootViewController = root
        window.makeKeyAndVisible()

        fun assertTextScale(
            expected: Float,
            ambient: Float,
            tolerance: Float = 0.002f,
        ) {
            await {
                val matchesBridge =
                    bridge?.let {
                        it.ambient == ambient && abs(it.ambient * it.multiplier - expected) <= tolerance
                    } == true
                val matchesText =
                    layout?.let { abs(it.layoutInput.density.fontScale - expected) <= tolerance } == true
                matchesBridge && matchesText
            }
            val observed = assertNotNull(bridge)
            val text = assertNotNull(layout)
            val fontSize = text.layoutInput.style.fontSize
            val pixels = with(text.layoutInput.density) { fontSize.toPx() }
            assertEquals(expected / ambient, observed.multiplier, tolerance / ambient)
            assertEquals(expected, text.layoutInput.density.fontScale, tolerance)
            assertEquals(
                14f * observed.pixelDensity * expected,
                pixels,
                14f * observed.pixelDensity * tolerance,
                "The actual platform text layout must apply the system category exactly once",
            )
            assertTrue(text.size.width > 0 && text.size.height > 0 && !text.hasVisualOverflow)
        }

        try {
            // The initial category comes from UIKit, before any notification. The app's
            // rounded body-scale table should remain close to UIKit's native body metrics.
            val initialScale = (UIFont.preferredFontForTextStyle(UIFontTextStyleBody).pointSize / 17.0).toFloat()
            listOf(0.85f, 1f, 1.8f).forEach { ambient ->
                hostScale.floatValue = ambient
                assertTextScale(initialScale, ambient, tolerance = 0.03f)
            }

            val center = NSNotificationCenter.defaultCenter
            listOf(
                UIContentSizeCategorySmall to 0.88f,
                UIContentSizeCategoryLarge to 1f,
                UIContentSizeCategoryAccessibilityExtraExtraExtraLarge to 3.12f,
            ).forEach { (category, expected) ->
                center.postNotificationName(
                    aName = UIContentSizeCategoryDidChangeNotification,
                    `object` = null,
                    userInfo = mapOf(UIContentSizeCategoryNewValueKey to category),
                )
                // Replacing ambient host density must recompute the correction without
                // forgetting the latest category received by the mounted observer.
                listOf(0.85f, 1f, 1.8f).forEach { ambient ->
                    hostScale.floatValue = ambient
                    assertTextScale(expected, ambient)
                }
            }

            center.postNotificationName(UIContentSizeCategoryDidChangeNotification, `object` = null)
            assertTextScale(initialScale, hostScale.floatValue, tolerance = 0.03f)
        } finally {
            try {
                visible.value = false
                await { !mounted }
            } finally {
                window.hidden = true
                window.rootViewController = null
            }
        }
    }

    private fun await(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
        }
        assertTrue(condition(), "The native font-scale bridge did not update the text layout within five seconds")
    }

    private data class Bridge(
        val pixelDensity: Float,
        val ambient: Float,
        val multiplier: Float,
    )
}
