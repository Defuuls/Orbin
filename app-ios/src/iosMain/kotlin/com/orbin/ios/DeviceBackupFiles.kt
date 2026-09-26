package com.orbin.ios

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Backups through the system sheets: the share sheet to save or send an export (Save to Files,
 * AirDrop, Mail), and the document picker to choose a file to restore.
 */
@OptIn(ExperimentalForeignApi::class)
class DeviceBackupFiles : BackupFiles {
    // Held while the picker is up: the picker keeps only a weak reference to its delegate.
    private var pickerDelegate: PickerDelegate? = null

    override suspend fun share(
        fileName: String,
        contents: String,
    ): Boolean {
        val presenter = topViewController() ?: return false
        val file = NSURL.fileURLWithPath(NSTemporaryDirectory() + fileName)
        if (!contents.encodeToByteArray().toNSData().writeToURL(file, atomically = true)) return false
        return suspendCoroutine { continuation ->
            val sheet = UIActivityViewController(activityItems = listOf(file), applicationActivities = null)
            sheet.completionWithItemsHandler = { _, completed, _, _ -> continuation.resume(completed) }
            presenter.presentViewController(sheet, animated = true, completion = null)
        }
    }

    override suspend fun pick(): String? {
        val presenter = topViewController() ?: return null
        return suspendCoroutine { continuation ->
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON), asCopy = true)
            val delegate =
                PickerDelegate { url ->
                    pickerDelegate = null
                    continuation.resume(url?.let { NSString.stringWithContentsOfURL(it, NSUTF8StringEncoding, null) })
                }
            pickerDelegate = delegate
            picker.delegate = delegate
            picker.allowsMultipleSelection = false
            presenter.presentViewController(picker, animated = true, completion = null)
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
