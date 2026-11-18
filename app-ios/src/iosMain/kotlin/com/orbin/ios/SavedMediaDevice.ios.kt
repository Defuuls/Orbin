package com.orbin.ios

import com.orbin.core.model.MediaLibraryRecord
import com.orbin.domain.repository.ManagedMediaFiles
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
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
): String =
    stageInto(record.fileName) { destination ->
        check(files.exportFile(record.storageKey, destination)) { "The saved original is missing" }
    }

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual suspend fun stageFetchedMedia(
    bytes: ByteArray,
    fileName: String,
): String =
    withContext(Dispatchers.Default) {
        check(bytes.isNotEmpty()) { "Empty media" }
        stageInto(fileName) { destination ->
            val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
            check(data.writeToFile(destination, atomically = true)) { "Could not stage media" }
            NSFileManager.defaultManager.setAttributes(stagedFileProtection, destination, null)
        }
    }

private val stagedFileProtection =
    mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication)

/** One protected folder per staged file; removed again if [write] does not finish. */
@OptIn(ExperimentalForeignApi::class)
private suspend fun stageInto(
    fileName: String,
    write: suspend (destination: String) -> Unit,
): String {
    val directory = "${exportRoot()}/${NSUUID().UUIDString}"
    val destination = "$directory/${stagedMediaName(fileName)}"
    check(
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, stagedFileProtection, null),
    ) { "Could not stage media" }
    var published = false
    try {
        write(destination)
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
