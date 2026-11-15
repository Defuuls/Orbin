package com.orbin.ios

import com.orbin.domain.repository.ManagedMediaFiles
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
class DeviceMediaStore(
    private val ownedFiles: ManagedMediaFiles = IosManagedMediaFiles(),
) : MediaStore {
    /** Exports an owned original without buffering a whole saved video in memory. */
    suspend fun exportLocalFile(
        path: String,
        fileName: String,
        target: SaveTarget,
    ) {
        val safeName =
            fileName
                .substringAfterLast(
                    '/',
                ).substringAfterLast('\\')
                .replace("..", "_")
                .ifBlank { "download" }
        if (target != SaveTarget.FILE && saveLocalToPhotos(path, safeName, target)) return
        val manager = NSFileManager.defaultManager
        val documents = manager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, true, null)
        val directory = NSURL.fileURLWithPath(checkNotNull(documents?.path) + "/Orbin", true)
        val protection = mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication)
        check(manager.createDirectoryAtURL(directory, true, protection, null)) { "Could not create export folder" }
        val destination = checkNotNull(freeName(directory, safeName))
        check(manager.copyItemAtPath(path, checkNotNull(destination.path), null)) { "Could not export saved media" }
        manager.setAttributes(protection, checkNotNull(destination.path), null)
    }

    override suspend fun ownedFileExists(storageKey: String): Boolean = ownedFiles.contains(storageKey)

    override suspend fun saveOwned(
        bytes: ByteArray,
        storageKey: String,
    ): Boolean {
        val temporary = NSURL.fileURLWithPath(NSTemporaryDirectory() + NSUUID().UUIDString + ".media")
        return try {
            check(bytes.toNSData().writeToURL(temporary, atomically = true)) { "Could not stage saved media" }
            ownedFiles.importFile(storageKey, checkNotNull(temporary.path))
            true
        } finally {
            NSFileManager.defaultManager.removeItemAtURL(temporary, error = null)
        }
    }

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
        // Photos imports from a file, preserving GIF animation and avoiding a second video buffer.
        val temporary = NSURL.fileURLWithPath(NSTemporaryDirectory() + NSUUID().UUIDString + "-" + fileName)
        if (!bytes.toNSData().writeToURL(temporary, atomically = true)) return false
        return try {
            saveLocalToPhotos(checkNotNull(temporary.path), fileName, target)
        } finally {
            NSFileManager.defaultManager.removeItemAtURL(temporary, error = null)
        }
    }

    private suspend fun saveLocalToPhotos(
        path: String,
        fileName: String,
        target: SaveTarget,
    ): Boolean {
        if (!photosAccess()) return false
        var converted: String? = null
        return try {
            val webM = fileName.substringAfterLast('.', "").equals("webm", ignoreCase = true)
            if (webM) converted = convertWebMForPhotos(path)
            val importedName = if (webM) fileName.substringBeforeLast('.') + ".mp4" else fileName
            suspendCoroutine { continuation ->
                PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                    {
                        val options = PHAssetResourceCreationOptions().apply { originalFilename = importedName }
                        PHAssetCreationRequest.creationRequestForAsset().addResourceWithType(
                            if (target == SaveTarget.VIDEO) PHAssetResourceTypeVideo else PHAssetResourceTypePhoto,
                            fileURL = NSURL.fileURLWithPath(converted ?: path),
                            options = options,
                        )
                    },
                    completionHandler = { success, _ -> continuation.resume(success) },
                )
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            // A failed codec or denied Photos import still exports the original to Files.
            false
        } finally {
            withContext(NonCancellable) { converted?.let { releaseSavedMedia(it) } }
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
