package com.orbin.ios

import com.orbin.data.util.MAX_BACKUP_INPUT_BYTES
import com.orbin.data.util.readBoundedBytes
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypeZIP
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Backups through the system sheets: the share sheet to save or send an export (Save to Files,
 * AirDrop, Mail), and the document picker to choose a file to restore.
 */
@OptIn(ExperimentalForeignApi::class)
class DeviceBackupFiles : BackupFiles {
    private var pickerDelegate: PickerDelegate? = null
    override val supportsArchives: Boolean = true

    override suspend fun share(
        fileName: String,
        contents: String,
    ): Boolean {
        val safeName =
            fileName.substringAfterLast('/').substringAfterLast('\\').replace("..", "_").ifBlank {
                "orbin-backup.json"
            }
        val file = NSURL.fileURLWithPath(NSTemporaryDirectory() + safeName)
        if (!contents.encodeToByteArray().toNSData().writeToURL(file, atomically = true)) return false
        return shareUrl(file, deleteAfter = true)
    }

    override suspend fun shareFile(
        fileName: String,
        sourcePath: String,
    ): Boolean = shareUrl(NSURL.fileURLWithPath(sourcePath), deleteAfter = false)

    private suspend fun shareUrl(
        file: NSURL,
        deleteAfter: Boolean,
    ): Boolean {
        val presenter =
            topViewController() ?: run {
                if (deleteAfter) NSFileManager.defaultManager.removeItemAtURL(file, error = null)
                return false
            }
        return suspendCancellableCoroutine { continuation ->
            val sheet = UIActivityViewController(activityItems = listOf(file), applicationActivities = null)
            sheet.completionWithItemsHandler = { _, completed, _, _ ->
                if (deleteAfter) NSFileManager.defaultManager.removeItemAtURL(file, error = null)
                if (continuation.isActive) continuation.resume(completed)
            }
            continuation.invokeOnCancellation {
                sheet.completionWithItemsHandler = null
                if (deleteAfter) NSFileManager.defaultManager.removeItemAtURL(file, error = null)
                if (presenter.presentedViewController ===
                    sheet
                ) {
                    presenter.dismissViewControllerAnimated(false, completion = null)
                }
            }
            sheet.popoverPresentationController?.let { popover ->
                popover.sourceView = presenter.view
                popover.sourceRect =
                    presenter.view.bounds.useContents { CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0) }
                popover.permittedArrowDirections = 0uL
            }
            if (continuation.isActive) presenter.presentViewController(sheet, animated = true, completion = null)
        }
    }

    override suspend fun pick(): String? {
        val path = pickFile() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                FileSystem.SYSTEM.source(path.toPath()).buffer().use {
                    it.readBoundedBytes(MAX_BACKUP_INPUT_BYTES).decodeToString(throwOnInvalidSequence = true)
                }
            } finally {
                FileSystem.SYSTEM.delete(path.toPath(), mustExist = false)
            }
        }
    }

    override suspend fun pickFile(): String? {
        val presenter = topViewController() ?: return null
        return suspendCancellableCoroutine { continuation ->
            val picker =
                UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeZIP, UTTypeJSON), asCopy = true)
            val delegate =
                PickerDelegate { url ->
                    if (!continuation.isActive) {
                        url?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }
                        return@PickerDelegate
                    }
                    pickerDelegate = null
                    continuation.resume(url?.path)
                }
            pickerDelegate = delegate
            picker.delegate = delegate
            picker.allowsMultipleSelection = false
            continuation.invokeOnCancellation {
                picker.delegate = null
                if (pickerDelegate === delegate) pickerDelegate = null
                if (presenter.presentedViewController ===
                    picker
                ) {
                    presenter.dismissViewControllerAnimated(false, completion = null)
                }
            }
            if (continuation.isActive) presenter.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class PickerDelegate(
    private val onDone: (NSURL?) -> Unit,
) : NSObject(),
    UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        onDone(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onDone(null)
    }
}

/** The controller on top of the app's window, which is where a sheet has to be presented from. */
internal fun topViewController(): UIViewController? {
    val window =
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
            .let { windows -> windows.firstOrNull { it.keyWindow } ?: windows.firstOrNull() }
    var top = window?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
