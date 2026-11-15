@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.orbin.ios

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.WebKit.WKWebView

/** WebKit needs a file-backed document to read an owned WebM; access is one staged UUID folder. */
internal fun WKWebView.loadWebMDocument(
    html: String,
    mediaUrl: String,
    delegate: WebMPageDelegate,
) {
    val media = NSURL.URLWithString(mediaUrl)
    if (media?.scheme != "file") {
        loadHTMLString(html, baseURL = null)
        return
    }
    val manager = NSFileManager.defaultManager
    val support = manager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
    val root = checkNotNull(support?.path) + "/Orbin/MediaExport/"
    val path = checkNotNull(media.path)
    val parts = path.removePrefix(root).split('/')
    require(
        path.startsWith(root) &&
            media.host.isNullOrEmpty() &&
            parts.size == 2 &&
            parts[0].length == 36 &&
            parts[0].all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == '-' } &&
            parts[1].isNotBlank() &&
            parts[1] != "." &&
            parts[1] != ".." &&
            '\\' !in path,
    ) {
        "Local playback requires an app-owned staged original"
    }
    val directory = path.substringBeforeLast('/')
    val documentPath = "$directory/orbin-player-${NSUUID().UUIDString}.html"
    val bytes = html.encodeToByteArray()
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    check(data.writeToFile(documentPath, atomically = true)) { "Could not prepare offline playback" }
    manager.setAttributes(
        mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication),
        documentPath,
        null,
    )
    val document = NSURL.fileURLWithPath(documentPath)
    delegate.localDocumentUrl = document.absoluteString
    loadFileURL(document, allowingReadAccessToURL = NSURL.fileURLWithPath(directory, true))
}

internal fun WebMPageDelegate.releaseLocalDocument() {
    localDocumentUrl
        ?.let {
            NSURL
                .URLWithString(
                    it,
                )?.path
        }?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
    localDocumentUrl = null
}
