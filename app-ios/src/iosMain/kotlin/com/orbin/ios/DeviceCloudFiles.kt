package com.orbin.ios

import com.orbin.domain.repository.validManagedMediaKey
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask

private val cloudFileSystem get() = FileSystem.SYSTEM
private val cloudProtection =
    mapOf<Any?, Any?>(
        NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication,
    )

@OptIn(ExperimentalForeignApi::class)
private fun cloudSupport(): Path {
    val manager = NSFileManager.defaultManager
    val root =
        checkNotNull(manager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)?.path) +
            "/Orbin"
    check(manager.createDirectoryAtPath(root, true, cloudProtection, null))
    NSURL.fileURLWithPath(root).setResourceValue(true, NSURLIsExcludedFromBackupKey, null)
    return root.toPath()
}

/** Atomic, protected installation metadata. A corrupt ledger never silently resets tombstones. */
internal class DeviceCloudRevisionStore : CloudRevisionStore {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun read(): CloudRevisionState =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val file = cloudSupport() / "cloud-revisions.json"
                if (!cloudFileSystem.exists(file)) {
                    val fresh = CloudRevisionState(origin = NSUUID().UUIDString)
                    writeState(file, fresh)
                    fresh
                } else {
                    require((cloudFileSystem.metadata(file).size ?: Long.MAX_VALUE) <= MAX_CLOUD_LEDGER_BYTES)
                    cloudFileSystem
                        .source(
                            file,
                        ).buffer()
                        .use { json.decodeFromString<CloudRevisionState>(it.readUtf8()) }
                        .also { require(it.origin.isNotBlank() && it.items.size <= MAX_CLOUD_ITEMS && it.clock >= 0) }
                }
            }
        }

    override suspend fun write(state: CloudRevisionState) {
        mutex.withLock {
            withContext(Dispatchers.IO) { writeState(cloudSupport() / "cloud-revisions.json", state) }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun writeState(
        file: Path,
        state: CloudRevisionState,
    ) {
        require(state.items.size <= MAX_CLOUD_ITEMS)
        val text = json.encodeToString(state)
        require(text.encodeToByteArray().size <= MAX_CLOUD_LEDGER_BYTES)
        val pending = file.parent!! / ".cloud-${NSUUID().UUIDString}.part"
        try {
            cloudFileSystem.sink(pending).buffer().use { it.writeUtf8(text) }
            check(NSFileManager.defaultManager.setAttributes(cloudProtection, pending.toString(), null))
            cloudFileSystem.atomicMove(pending, file)
        } finally {
            cloudFileSystem.delete(pending, mustExist = false)
        }
    }
}

/** Resolves only managed originals and native-generated inbox files, never a cloud-provided path. */
internal class DeviceCloudAssetFiles : CloudAssetFiles {
    override suspend fun localPath(storageKey: String): String? =
        withContext(Dispatchers.IO) {
            require(validManagedMediaKey(storageKey))
            val root = cloudSupport() / "ManagedMedia"
            val path = root / storageKey
            if (!cloudFileSystem.exists(path)) return@withContext null
            val resolved = cloudFileSystem.canonicalize(path)
            require(resolved.parent == cloudFileSystem.canonicalize(root))
            val info = cloudFileSystem.metadata(resolved)
            require(info.isRegularFile && (info.size ?: Long.MAX_VALUE) <= MAX_CLOUD_MEDIA_BYTES)
            resolved.toString()
        }

    override suspend fun validateIncoming(
        path: String,
        expectedBytes: Long?,
    ) {
        withContext(Dispatchers.IO) {
            val resolved = cloudFileSystem.canonicalize(path.toPath())
            // Swift stages here, outside Documents. It never accepts a destination from a record.
            val inbox = cloudSupport().parent!! / "CloudInbox"
            val lease = resolved.parent
            require(
                lease?.parent == cloudFileSystem.canonicalize(inbox) &&
                    lease.name.length == CLOUD_LEASE_ID_LENGTH &&
                    lease.name.all { it.isLetterOrDigit() || it == '-' },
            ) { "Not a CloudKit inbox file" }
            val info = cloudFileSystem.metadata(resolved)
            require(info.isRegularFile && (info.size ?: Long.MAX_VALUE) <= MAX_CLOUD_MEDIA_BYTES)
            if (expectedBytes !=
                null
            ) {
                require(info.size == expectedBytes) { "Cloud asset size does not match its metadata" }
            }
        }
    }

    override suspend fun discardIncoming(path: String) {
        validateIncoming(path, expectedBytes = null)
        withContext(Dispatchers.IO) { cloudFileSystem.delete(path.toPath(), mustExist = false) }
    }
}

private const val MAX_CLOUD_LEDGER_BYTES = 67_108_864L
private const val MAX_CLOUD_ITEMS = 100_000

private const val CLOUD_LEASE_ID_LENGTH = 36
