package com.orbin.app.routing

import android.content.Intent
import com.orbin.data.notification.AndroidThreadNotifier
import com.orbin.data.notification.NotificationLaunchToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [35])
class IntentRouteTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun threadIntent(token: String?) =
        Intent().apply {
            putExtra(AndroidThreadNotifier.EXTRA_PROVIDER, "4chan")
            putExtra(AndroidThreadNotifier.EXTRA_BOARD, "g")
            putExtra(AndroidThreadNotifier.EXTRA_THREAD, 1L)
            putExtra(AndroidThreadNotifier.EXTRA_TITLE, "title")
            if (token != null) putExtra(NotificationLaunchToken.EXTRA_TOKEN, token)
        }

    private fun Intent.route() = parseRoute { NotificationLaunchToken.matches(context, it) }

    @Test
    fun `a notification tap carrying the install token opens the thread`() {
        val route = threadIntent(NotificationLaunchToken.get(context)).route()
        assertEquals(IntentRoute.Thread("4chan", "g", 1L, "title"), route)
    }

    @Test
    fun `thread extras from another app without the token are ignored`() {
        assertEquals(IntentRoute.Unknown, threadIntent(null).route())
        assertEquals(IntentRoute.Unknown, threadIntent("guess").route())
    }

    @Test
    fun `the token is stable and random per install`() {
        val token = NotificationLaunchToken.get(context)
        assertEquals(token, NotificationLaunchToken.get(context))
        assertNotEquals("", token)
    }
}
