package com.orbin.ios

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.getBytes
import platform.Foundation.setValue

private const val BACKGROUND_DOWNLOAD_SESSION = "io.github.defuuls.orbin.media-downloads"

/**
 * Device-managed downloads for iOS. Background URLSession keeps transfers moving while Orbin is
 * suspended, and each finished response is moved into Application Support before the temporary
 * URLSession file expires. The database record ID reconnects the result after process recreation.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal object IosBackgroundMediaFetch : DurableMediaFetch {
    private val continuations = mutableMapOf<String, CancellableContinuation<ByteArray>>()
    private val progressCallbacks = mutableMapOf<String, (Long, Long?) -> Unit>()
    private var backgroundEventsCompletion: (() -> Unit)? = null

    private val session: NSURLSession by lazy {
        val configuration =
            NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(
                BACKGROUND_DOWNLOAD_SESSION,
            )
        configuration.setSessionSendsLaunchEvents(true)
        configuration.setDiscretionary(false)
        configuration.setAllowsCellularAccess(true)
        NSURLSession.sessionWithConfiguration(
            configuration = configuration,
            delegate = SessionDelegate(),
            delegateQueue = NSOperationQueue.mainQueue,
        )
    }

    override suspend fun fetch(
        id: Long,
        url: String,
        onProgress: (received: Long, total: Long?) -> Unit,
    ): ByteArray {
        val key = id.toString()
        val destination = downloadedFile(key)
        if (NSFileManager.defaultManager.fileExistsAtPath(checkNotNull(destination.path))) {
            return readBytes(destination)
        }
        val source = NSURL.URLWithString(url) ?: error("Invalid download URL")
        return suspendCancellableCoroutine { continuation ->
            continuations[key] = continuation
            progressCallbacks[key] = onProgress
            continuation.invokeOnCancellation {
                // Leave the URLSession task running. It can complete in the background and be
                // recovered from its durable file when the app opens again.
                continuations.remove(key)
                progressCallbacks.remove(key)
            }
            session.getAllTasksWithCompletionHandler { tasks ->
                if (!continuation.isActive) return@getAllTasksWithCompletionHandler
                // The background task may finish between the first file check and this callback.
                if (NSFileManager.defaultManager.fileExistsAtPath(checkNotNull(destination.path))) {
                    finish(key, runCatching { readBytes(destination) })
                    return@getAllTasksWithCompletionHandler
                }
                val existing =
                    (tasks ?: emptyList<Any>())
                        .asSequence()
                        .mapNotNull { it as? NSURLSessionDownloadTask }
                        .firstOrNull { it.taskDescription == key }
                if (existing != null) {
                    existing.resume()
                } else {
                    val request = NSMutableURLRequest(uRL = source)
                    request.setValue(ORBIN_USER_AGENT, forHTTPHeaderField = "User-Agent")
                    val task = session.downloadTaskWithRequest(request)
                    task.taskDescription = key
                    task.resume()
                }
            }
        }
    }

    override suspend fun hasPendingTransfer(
        id: Long,
        url: String,
    ): Boolean {
        val key = id.toString()
        if (NSFileManager.defaultManager.fileExistsAtPath(checkNotNull(downloadedFile(key).path))) return true
        return suspendCancellableCoroutine { continuation ->
            session.getAllTasksWithCompletionHandler { tasks ->
                val found =
                    (tasks ?: emptyList<Any>())
                        .asSequence()
                        .mapNotNull { it as? NSURLSessionDownloadTask }
                        .any { it.taskDescription == key }
                if (continuation.isActive) continuation.resume(found)
            }
        }
    }

    override fun didSave(id: Long) {
        NSFileManager.defaultManager.removeItemAtURL(downloadedFile(id.toString()), error = null)
    }

    internal fun onProgress(
        downloadTask: NSURLSessionDownloadTask,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long,
    ) {
        val key = downloadTask.taskDescription ?: return
        progressCallbacks[key]?.invoke(totalBytesWritten, totalBytesExpectedToWrite.takeIf { it >= 0L })
    }

    internal fun onDownloaded(
        downloadTask: NSURLSessionDownloadTask,
        temporaryUrl: NSURL,
    ) {
        val key = downloadTask.taskDescription ?: return
        val statusCode = (downloadTask.response as? NSHTTPURLResponse)?.statusCode?.toInt()
        if (statusCode == null || statusCode !in 200..299) {
            finish(key, Result.failure(IllegalStateException("HTTP ${statusCode ?: "unknown"}")))
            return
        }
        val destination = downloadedFile(key)
        val manager = NSFileManager.defaultManager
        manager.removeItemAtURL(destination, error = null)
        val moved = manager.moveItemAtURL(srcURL = temporaryUrl, toURL = destination, error = null)
        if (!moved) {
            finish(key, Result.failure(IllegalStateException("Could not preserve the completed download")))
        } else {
            finish(key, runCatching { readBytes(destination) })
        }
    }

    internal fun onTaskCompleted(
        task: NSURLSessionTask,
        error: NSError?,
    ) {
        if (error == null) return
        val key = task.taskDescription ?: return
        finish(key, Result.failure(IllegalStateException(error.localizedDescription)))
    }

    internal fun onSessionEventsFinished() {
        val completion = backgroundEventsCompletion
        backgroundEventsCompletion = null
        completion?.invoke()
    }

    fun handleBackgroundEvents(completionHandler: () -> Unit) {
        backgroundEventsCompletion = completionHandler
        session
    }

    private fun finish(
        key: String,
        result: Result<ByteArray>,
    ) {
        val continuation = continuations.remove(key) ?: return
        progressCallbacks.remove(key)
        result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
    }

    private fun downloadedFile(key: String): NSURL {
        val manager = NSFileManager.defaultManager
        val support =
            checkNotNull(
                manager.URLForDirectory(
                    directory = NSApplicationSupportDirectory,
                    inDomain = NSUserDomainMask,
                    appropriateForURL = null,
                    create = true,
                    error = null,
                ),
            ) { "No Application Support directory" }
        support.setResourceValue(value = true, forKey = NSURLIsExcludedFromBackupKey, error = null)
        val directory = checkNotNull(support.URLByAppendingPathComponent("background-downloads", isDirectory = true))
        manager.createDirectoryAtURL(directory, withIntermediateDirectories = true, attributes = null, error = null)
        return checkNotNull(directory.URLByAppendingPathComponent("$key.download"))
    }

    private fun readBytes(url: NSURL): ByteArray {
        val data = NSData.dataWithContentsOfURL(url) ?: error("Could not read completed download")
        val bytes = ByteArray(data.length.toInt())
        if (bytes.isNotEmpty()) {
            bytes.usePinned { data.getBytes(it.addressOf(0), data.length) }
        }
        return bytes
    }
}

/** Called by the Swift app delegate before the system delivers background URLSession events. */
@Suppress("unused")
fun handleBackgroundMediaDownloadEvents(
    identifier: String,
    completionHandler: () -> Unit,
) {
    if (identifier == BACKGROUND_DOWNLOAD_SESSION) {
        IosBackgroundMediaFetch.handleBackgroundEvents(completionHandler)
    } else {
        completionHandler()
    }
}

private class SessionDelegate :
    platform.darwin.NSObject(),
    NSURLSessionDownloadDelegateProtocol {
    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: Long,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long,
    ) {
        IosBackgroundMediaFetch.onProgress(downloadTask, totalBytesWritten, totalBytesExpectedToWrite)
    }

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didFinishDownloadingToURL: NSURL,
    ) {
        IosBackgroundMediaFetch.onDownloaded(downloadTask, didFinishDownloadingToURL)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        IosBackgroundMediaFetch.onTaskCompleted(task, didCompleteWithError)
    }

    override fun URLSessionDidFinishEventsForBackgroundURLSession(session: NSURLSession) {
        IosBackgroundMediaFetch.onSessionEventsFinished()
    }
}
