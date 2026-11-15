package com.orbin.ios

import com.orbin.domain.repository.ManagedMediaFiles
import com.orbin.domain.repository.validManagedMediaKey
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask

/** App-private originals use iOS Data Protection and are never exposed in Documents/Files. */
@OptIn(ExperimentalForeignApi::class)
class IosManagedMediaFiles : ManagedMediaFiles {
    private val manager get() = NSFileManager.defaultManager
    private val protection =
        mapOf<Any?, Any?>(
            NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication,
        )

    private fun root(): String {
        val support = manager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
        val root = checkNotNull(support?.path) + "/Orbin/ManagedMedia"
        check(manager.createDirectoryAtPath(root, true, protection, null)) { "Could not create media storage" }
        NSURL.fileURLWithPath(root).setResourceValue(true, NSURLIsExcludedFromBackupKey, null)
        return root
    }

    private fun path(key: String): String {
        require(validManagedMediaKey(key)) { "Invalid media storage key" }
        return "${root()}/$key"
    }

    override suspend fun contains(storageKey: String): Boolean =
        withContext(Dispatchers.Default) {
            manager.fileExistsAtPath(path(storageKey))
        }

    override suspend fun size(storageKey: String): Long? =
        withContext(Dispatchers.Default) {
            (manager.attributesOfItemAtPath(path(storageKey), null)?.get(NSFileSize) as? NSNumber)?.longLongValue
        }

    override suspend fun importFile(
        storageKey: String,
        sourcePath: String,
    ) {
        withContext(Dispatchers.Default) { atomicCopy(sourcePath, path(storageKey)) }
    }

    override suspend fun exportFile(
        storageKey: String,
        destinationPath: String,
    ): Boolean =
        withContext(Dispatchers.Default) {
            val source = path(storageKey)
            if (!manager.fileExistsAtPath(source)) return@withContext false
            require(source != destinationPath) { "An export cannot replace its original" }
            atomicCopy(source, destinationPath)
            true
        }

    override suspend fun remove(storageKey: String) {
        withContext(Dispatchers.Default) {
            val target = path(storageKey)
            check(
                !manager.fileExistsAtPath(target) || manager.removeItemAtPath(target, null),
            ) { "Could not remove saved media" }
        }
    }

    private fun atomicCopy(
        source: String,
        destination: String,
    ) {
        val parent = destination.substringBeforeLast('/')
        check(manager.createDirectoryAtPath(parent, true, protection, null)) { "Could not create media staging" }
        val pending = "$parent/.${NSUUID().UUIDString}.part"
        try {
            check(manager.copyItemAtPath(source, pending, null)) { "Could not copy saved media" }
            check(manager.setAttributes(protection, pending, null)) { "Could not protect saved media" }
            // replaceItemAtURL keeps the old bytes intact if an imported replacement fails.
            if (manager.fileExistsAtPath(destination)) {
                check(
                    manager.replaceItemAtURL(
                        NSURL.fileURLWithPath(destination),
                        NSURL.fileURLWithPath(pending),
                        null,
                        0u,
                        null,
                        null,
                    ),
                ) { "Could not replace saved media" }
            } else {
                check(manager.moveItemAtPath(pending, destination, null)) { "Could not commit saved media" }
            }
        } finally {
            manager.removeItemAtPath(pending, null)
        }
    }
}
