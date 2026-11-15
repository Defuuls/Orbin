package com.orbin.ios

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebMNavigationPolicyTest {
    @Test
    fun ownedPlaybackAllowsOnlyTheExactGeneratedDocument() {
        val document = "file:///private/app/Orbin/MediaExport/uuid/orbin-player.html"
        assertTrue(shouldAllowWebMNavigation(document, document))
        assertFalse(shouldAllowWebMNavigation("file:///etc/passwd", document))
        assertFalse(shouldAllowWebMNavigation("$document/../other.html", document))
        assertFalse(shouldAllowWebMNavigation("$document?redirect=1", document))
        assertFalse(shouldAllowWebMNavigation("https://example.com", document))
    }

    @Test
    fun allowsTheLocalPlayerDocument() {
        assertTrue(shouldAllowWebMNavigation(null))
        assertTrue(shouldAllowWebMNavigation(""))
        assertTrue(shouldAllowWebMNavigation("about:blank"))
        assertTrue(shouldAllowWebMNavigation("about:blank#"))
        assertTrue(shouldAllowWebMNavigation("about:blank?foo=1"))
        assertTrue(shouldAllowWebMNavigation("about:srcdoc"))
        assertTrue(shouldAllowWebMNavigation("ABOUT:BLANK"))
    }

    @Test
    fun blocksNavigationAwayFromThePlayer() {
        assertFalse(shouldAllowWebMNavigation("https://example.com"))
        assertFalse(shouldAllowWebMNavigation("http://example.com"))
        assertFalse(shouldAllowWebMNavigation("file:///etc/passwd"))
        assertFalse(shouldAllowWebMNavigation("javascript:alert(1)"))
        assertFalse(shouldAllowWebMNavigation("data:text/html,hi"))
        assertFalse(shouldAllowWebMNavigation("blob:https://example.com/1"))
        assertFalse(shouldAllowWebMNavigation("about:webkit"))
        assertFalse(shouldAllowWebMNavigation("ftp://example.com"))
        assertFalse(shouldAllowWebMNavigation("intent://scan"))
    }
}
