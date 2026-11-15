@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.orbin.ios

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.orbin.core.model.ProviderId
import com.orbin.provider.api.FourchanSearchSession
import com.orbin.provider.api.ProviderMetadata
import kotlinx.cinterop.objcPtr
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.NSUUID
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.WebKit.WKWebView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A real native verification document survives covering Search and returning, without a reload. */
class NativeSearchContinuityHostTest {
    @Test
    fun coveringAndReturningRetainsTheSameWebKitViewAndItsLiveDOM() {
        val session =
            FourchanSearchSession(
                ProviderMetadata(ProviderId("fourchan"), "4chan", "https://boards.4chan.org"),
            ) { true }
        val scope = MainScope()
        val transport = NativeSearchTransport(session, scope)
        val visible = mutableStateOf(true)
        val root = ComposeUIViewController { if (visible.value) transport.Content(Modifier.fillMaxSize()) }
        val window =
            UIWindow(CGRectMake(0.0, 0.0, 390.0, 844.0)).apply {
                rootViewController = root
                makeKeyAndVisible()
            }
        try {
            await { root.view.webViews().size == 1 }
            val original = root.view.webViews().single()
            original.loadHTMLString("<html><body>Local verification fixture</body></html>", null)
            await { original.estimatedProgress >= 1.0 }
            val token = NSUUID().UUIDString
            var inserted: String? = null
            original.evaluateJavaScript(
                "window.__orbinVerificationToken='$token'; window.__orbinVerificationToken",
            ) { value, _ ->
                inserted =
                    value as? String
            }
            await { inserted == token }
            visible.value = false
            await { root.view.webViews().isEmpty() }
            visible.value = true
            await { root.view.webViews().size == 1 }
            val restored = root.view.webViews().single()
            assertEquals(original.objcPtr(), restored.objcPtr(), "Back reattaches the retained browser")
            var readBack: String? = null
            restored.evaluateJavaScript("window.__orbinVerificationToken") { value, _ -> readBack = value as? String }
            await { readBack != null }
            assertEquals(token, assertNotNull(readBack), "Verification DOM must not be reloaded or recreated")
            assertEquals(0L, session.state.value.requestId, "Reattachment does not submit a search")
        } finally {
            visible.value = false
            transport.close()
            scope.cancel()
            window.hidden = true
            window.rootViewController = null
        }
    }

    private fun await(condition: () -> Boolean) {
        repeat(300) {
            if (condition()) return
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
        }
        assertTrue(condition(), "Native search view did not settle within fifteen seconds")
    }

    private fun UIView.webViews(): List<WKWebView> =
        subviews.filterIsInstance<UIView>().flatMap {
            if (it is WKWebView) listOf(it) else it.webViews()
        }
}
