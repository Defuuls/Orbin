package com.orbin.app

import android.content.ActivityNotFoundException
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.link.InAppBrowser
import com.orbin.core.model.AppSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [35])
class OrbinUriHandlerTest {
    private val context = RuntimeEnvironment.getApplication()
    private val started = mutableListOf<Intent>()
    private val external = mutableListOf<String>()

    @Test
    fun `with the setting on, links open in the in-app browser`() {
        handler(AppSettings(openLinksInApp = true)).openUri("https://example.org/a")

        assertThat(started.single().action).isEqualTo(InAppBrowser.ACTION_OPEN)
        assertThat(started.single().dataString).isEqualTo("https://example.org/a")
        assertThat(external).isEmpty()
    }

    @Test
    fun `with the setting off, links open in the system browser`() {
        handler(AppSettings(openLinksInApp = false)).openUri("https://example.org/a")

        assertThat(started).isEmpty()
        assertThat(external).containsExactly("https://example.org/a")
    }

    @Test
    fun `unsafe links open nowhere, whatever the setting`() {
        for (inApp in listOf(true, false)) {
            val handler = handler(AppSettings(openLinksInApp = inApp))
            listOf("http://example.org", "javascript:alert(1)", "intent://x#Intent;end").forEach(handler::openUri)
        }

        assertThat(started).isEmpty()
        assertThat(external).isEmpty()
    }

    @Test
    fun `if the browser cannot start, the link still opens externally`() {
        val handler =
            OrbinUriHandler(
                context,
                settings = { AppSettings(openLinksInApp = true) },
                dark = { false },
                startBrowser = { throw ActivityNotFoundException() },
                openExternal = {
                    external += it
                    true
                },
            )

        handler.openUri("https://example.org/a")

        assertThat(external).containsExactly("https://example.org/a")
    }

    @Test
    fun `the setting is read when the link is tapped`() {
        var settings = AppSettings(openLinksInApp = true)
        val handler = handler { settings }

        settings = settings.copy(openLinksInApp = false)
        handler.openUri("https://example.org/a")

        assertThat(started).isEmpty()
        assertThat(external).containsExactly("https://example.org/a")
    }

    @Test
    fun `the browser inherits the theme, screen privacy and the app lock`() {
        val plain = browserAppearance(AppSettings(), dark = false)
        assertThat(plain.secure).isFalse()
        assertThat(plain.closeWhenHidden).isFalse()

        val private = browserAppearance(AppSettings(screenPrivacyEnabled = true, amoled = true), dark = true)
        assertThat(private.secure).isTrue()
        assertThat(private.closeWhenHidden).isFalse()
        assertThat(private.dark).isTrue()
        assertThat(private.amoled).isTrue()

        // The lock covers only the reader, so a locked app's browser closes when it leaves the screen.
        val locked = browserAppearance(AppSettings(biometricLockEnabled = true), dark = false)
        assertThat(locked.secure).isTrue()
        assertThat(locked.closeWhenHidden).isTrue()
    }

    private fun handler(settings: AppSettings) = handler { settings }

    private fun handler(settings: () -> AppSettings) =
        OrbinUriHandler(
            context,
            settings = settings,
            dark = { false },
            startBrowser = { started += it },
            openExternal = {
                external += it
                true
            },
        )
}
