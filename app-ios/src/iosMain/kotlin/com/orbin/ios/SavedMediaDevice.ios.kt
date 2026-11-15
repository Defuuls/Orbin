package com.orbin.ios

import com.orbin.core.model.MediaLibraryRecord
import com.orbin.domain.repository.ManagedMediaFiles
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController

@OptIn(ExperimentalForeignApi::class)
private fun exportRoot(): String {
    val manager = NSFileManager.defaultManager
    val support = manager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
    return checkNotNull(support?.path) + "/Orbin/MediaExport"
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun stageSavedMedia(
    files: ManagedMediaFiles,
    record: MediaLibraryRecord,
): String {
    val directory = "${exportRoot()}/${NSUUID().UUIDString}"
    val name =
        record.fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filterNot(Char::isISOControl)
            .replace("..", "_")
            .ifBlank { "saved-media" }
    val destination = "$directory/$name"
    val protection = mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication)
    check(
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, protection, null),
    ) { "Could not stage media" }
    var published = false
    try {
        check(files.exportFile(record.storageKey, destination)) { "The saved original is missing" }
        published = true
        return destination
    } finally {
        if (!published) NSFileManager.defaultManager.removeItemAtPath(directory, null)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun releaseSavedMedia(path: String) {
    withContext(Dispatchers.Default) {
        val directory = path.substringBeforeLast('/')
        val root = exportRoot()
        if (directory.startsWith("$root/") && directory.removePrefix("$root/").none { it == '/' || it == '\\' }) {
            NSFileManager.defaultManager.removeItemAtPath(directory, null)
        }
    }
}

internal actual fun savedMediaFileUrl(path: String): String = checkNotNull(NSURL.fileURLWithPath(path).absoluteString)

@OptIn(ExperimentalForeignApi::class)
internal actual fun shareSavedMedia(
    paths: List<String>,
    onFinished: () -> Unit,
): Boolean {
    val presenter = topViewController()
    if (presenter == null || paths.isEmpty()) return false
    val sheet = UIActivityViewController(paths.map { NSURL.fileURLWithPath(it) }, null)
    sheet.completionWithItemsHandler = { _, _, _, _ -> onFinished() }
    sheet.popoverPresentationController?.let {
        it.sourceView = presenter.view
        it.sourceRect = presenter.view.bounds.useContents { CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0) }
        it.permittedArrowDirections = 0uL
    }
    presenter.presentViewController(sheet, true, null)
    return true
}

internal actual suspend fun exportSavedMedia(
    path: String,
    record: MediaLibraryRecord,
) {
    DeviceMediaStore().exportLocalFile(path, record.fileName, saveTargetOf(record.fileName))
}
