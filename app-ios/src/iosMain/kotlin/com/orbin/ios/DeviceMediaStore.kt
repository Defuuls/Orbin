package com.orbin.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetCreationRequest
import platform.Photos.PHAssetResourceCreationOptions
import platform.Photos.PHAssetResourceTypePhoto
import platform.Photos.PHAssetResourceTypeVideo
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHPhotoLibrary
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Saving on the device: photos and the videos iOS plays go into the Photos library (asking once
 * for add-only access), and everything else — or anything Photos refuses — into the app's
 * Documents folder, which the Files app shows under On My iPhone › Orbin.
 */
@OptIn(ExperimentalForeignApi::class)
class DeviceMediaStore : MediaStore {
    override suspend fun save(
        bytes: ByteArray,
        fileName: String,
        folder: String,
        target: SaveTarget,
    ) {
        val safeFileName =
            fileName
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .replace("..", "_")
                .ifBlank { "download" }
        val savedToPhotos = target != SaveTarget.FILE && saveToPhotos(bytes, safeFileName, target)
        if (!savedToPhotos) saveToFiles(bytes, safeFileName, folder)
        Haptics.success()
    }

    private suspend fun saveToPhotos(
        bytes: ByteArray,
        fileName: String,
        target: SaveTarget,
    ): Boolean {
        if (!photosAccess()) return false
        // Photos imports from a file, which keeps a GIF animated and a video's original encoding.
        val temporary = NSURL.fileURLWithPath(NSTemporaryDirectory() + NSUUID().UUIDString + "-" + fileName)
        if (!bytes.toNSData().writeToURL(temporary, atomically = true)) return false
        return try {
            suspendCoroutine { continuation ->
                PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                    {
                        val options = PHAssetResourceCreationOptions().apply { originalFilename = fileName }
                        PHAssetCreationRequest.creationRequestForAsset().addResourceWithType(
                            if (target == SaveTarget.VIDEO) PHAssetResourceTypeVideo else PHAssetResourceTypePhoto,
                            fileURL = temporary,
                            options = options,
                        )
                    },
                    completionHandler = { success, _ -> continuation.resume(success) },
                )
            }
        } finally {
            NSFileManager.defaultManager.removeItemAtURL(temporary, error = null)
        }
    }

    private suspend fun photosAccess(): Boolean =
        suspendCoroutine { continuation ->
            PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
                continuation.resume(status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited)
            }
        }

    private fun saveToFiles(
        bytes: ByteArray,
        fileName: String,
        folder: String,
    ) {
        val manager = NSFileManager.defaultManager
        val documents =
            manager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            )
        val basePath = checkNotNull(documents?.path) { "No Documents folder" }
        val cleanFolder =
            folder
                .split('/', '\\')
                .map { it.replace("..", "_").trim() }
                .filter { it.isNotEmpty() && it != "." }
                .joinToString("/")
        val rootPath = "$basePath/Orbin"
        val rootDirectory = NSURL.fileURLWithPath(rootPath, isDirectory = true)
        val directory = NSURL.fileURLWithPath("$rootPath/$cleanFolder", isDirectory = true)
        val protection =
            mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication)
        manager.createDirectoryAtURL(
            directory,
            withIntermediateDirectories = true,
            attributes = protection,
            error = null,
        )
        rootDirectory.setResourceValue(value = true, forKey = NSURLIsExcludedFromBackupKey, error = null)
        directory.setResourceValue(value = true, forKey = NSURLIsExcludedFromBackupKey, error = null)
        manager.setAttributes(attributes = protection, ofItemAtPath = rootPath, error = null)
        directory.path?.let { manager.setAttributes(attributes = protection, ofItemAtPath = it, error = null) }
        val destination = checkNotNull(freeName(directory, fileName)) { "No file name free for $fileName" }
        check(bytes.toNSData().writeToURL(destination, atomically = true)) { "Could not write $fileName" }
        destination.path?.let { manager.setAttributes(attributes = protection, ofItemAtPath = it, error = null) }
    }

    /** [fileName] in [directory], or "name (2).ext" and so on when a file of that name is there. */
    private fun freeName(
        directory: NSURL,
        fileName: String,
    ): NSURL? {
        val stem = fileName.substringBeforeLast('.')
        val extension = fileName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        return (1..MAX_NAME_ATTEMPTS)
            .asSequence()
            .map { attempt -> if (attempt == 1) fileName else "$stem ($attempt)$extension" }
            .mapNotNull { directory.URLByAppendingPathComponent(it) }
            .firstOrNull { url -> url.path?.let { !NSFileManager.defaultManager.fileExistsAtPath(it) } ?: false }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) {
        NSData()
    } else {
        usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
    }

private const val MAX_NAME_ATTEMPTS = 1_000
