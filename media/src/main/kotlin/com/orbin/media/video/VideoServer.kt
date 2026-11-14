package com.orbin.media.video

import com.orbin.network.di.VideoOkHttp
import com.orbin.network.interceptor.RetryAfter
import com.orbin.network.interceptor.RetryAfterTracker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local HTTP streaming and LRU disk cache server on `127.0.0.1`, translated from NekoSurf's
 * `VideoServer` (PR #89).
 *
 * ExoPlayer opens `http://127.0.0.1:<port>/<id>` and receives bytes as soon as they arrive from
 * upstream while the same download fills a cache file on disk. Complete files play directly from
 * disk. Seeking ahead (such as WebM cue indices at the end of a file) fetches the requested byte
 * range upstream without blocking the sequential download.
 */
@Singleton
class VideoServer internal constructor(
    private val cacheDirProvider: () -> File,
    private val client: OkHttpClient,
) {
    @Inject
    constructor(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
        @VideoOkHttp client: OkHttpClient,
    ) : this(
        cacheDirProvider = { File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() } },
        client = client,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    @Volatile
    private var maxCacheBytes: Long = DEFAULT_MAX_CACHE_BYTES

    private val downloads = ConcurrentHashMap<String, Download>()
    private val ids = ConcurrentHashMap<String, Download>()
    private val progressFlows = ConcurrentHashMap<String, MutableStateFlow<Float?>>()
    private val loadedFlows = ConcurrentHashMap<String, MutableStateFlow<List<Pair<Float, Float>>>>()
    private val watched = mutableSetOf<String>()
    private val awaited = mutableMapOf<String, Int>()
    private val queued = mutableListOf<Download>()

    private var serverSocket: ServerSocket? = null
    private var serverPort: Int = -1

    fun maxCacheBytes(): Long = maxCacheBytes

    suspend fun setMaxCacheBytes(bytes: Long) {
        if (bytes == maxCacheBytes) return
        maxCacheBytes = bytes.coerceAtLeast(0L)
        withContext(Dispatchers.IO) { pruneCache() }
    }

    /**
     * Returns what the player should open for [url]: a `file://` URI if cached completely on disk,
     * otherwise a local `http://127.0.0.1:<port>/<id>` stream that starts downloading immediately.
     */
    suspend fun source(url: String): String =
        withContext(Dispatchers.IO) {
            synchronized(lock) { watched.add(url) }
            val file = fileFor(url)
            if (file.exists() && !downloads.containsKey(url)) {
                progressFlow(url).value = 1f
                loadedFlow(url).value = ALL_LOADED
                touch(file)
                return@withContext file.toURI().toString()
            }
            val download = downloadFor(url, file)
            schedule()
            val port = ensureServer()
            "http://127.0.0.1:$port/${download.id}"
        }

    /** Marks [url] as actively watched so its download takes priority over background prefetches. */
    fun watch(url: String) {
        val changed = synchronized(lock) { watched.add(url) }
        if (changed) schedule()
    }

    /** Unmarks [url] as watched so its download continues in the background queue. */
    fun unwatch(url: String) {
        val changed = synchronized(lock) { watched.remove(url) }
        if (changed) schedule()
    }

    /** Queues a background prefetch for [url] without attaching a player. */
    suspend fun prefetch(url: String) =
        withContext(Dispatchers.IO) {
            val file = fileFor(url)
            synchronized(lock) {
                if (file.exists() || downloads.containsKey(url)) return@withContext
                val download = downloadForLocked(url, file)
                queued.add(download)
                var waiting = queued.count { !it.started }
                var i = 0
                while (waiting > MAX_QUEUED && i < queued.size) {
                    val dropped = queued[i]
                    if (dropped.started) {
                        i++
                        continue
                    }
                    queued.removeAt(i)
                    waiting--
                    if (downloads[dropped.url] === dropped) {
                        downloads.remove(dropped.url)
                        ids.remove(dropped.id)
                    }
                }
            }
            schedule()
        }

    fun progressOf(url: String): StateFlow<Float?> = progressFlow(url).asStateFlow()

    fun loadedOf(url: String): StateFlow<List<Pair<Float, Float>>> = loadedFlow(url).asStateFlow()

    suspend fun cachedFile(url: String): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = fileFor(url)
                if (file.exists() && !downloads.containsKey(url)) file else null
            }.getOrNull()
        }

    suspend fun downloadedFile(
        url: String,
        onProgress: ((Float?) -> Unit)? = null,
    ): File? {
        val download = downloads[url] ?: return cachedFile(url)
        synchronized(lock) {
            awaited[url] = (awaited[url] ?: 0) + 1
        }
        schedule()
        val flow = progressFlow(url)
        val collectorJob =
            onProgress?.let { cb ->
                scope.launch {
                    flow.collect { cb(it) }
                }
            }
        try {
            download.done.await()
        } finally {
            collectorJob?.cancel()
            synchronized(lock) {
                val left = (awaited[url] ?: 1) - 1
                if (left <= 0) awaited.remove(url) else awaited[url] = left
            }
            schedule()
        }
        return if (download.failed) null else cachedFile(url)
    }

    suspend fun evict(url: String) =
        withContext(Dispatchers.IO) {
            val download =
                synchronized(lock) {
                    downloads.remove(url)?.also { d ->
                        ids.remove(d.id)
                        queued.remove(d)
                    }
                }
            if (download != null) {
                download.cancel()
                schedule()
            }
            progressFlow(url).value = null
            loadedFlow(url).value = emptyList()
            runCatching {
                val file = fileFor(url)
                if (file.exists()) file.delete()
                val part = partFileFor(file)
                if (part.exists()) part.delete()
            }
            Unit
        }

    suspend fun cacheSize(): Long =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = cacheDirProvider()
                dir.listFiles()
                    ?.asSequence()
                    ?.filter { it.isFile && !it.name.endsWith(PART_SUFFIX) }
                    ?.sumOf { it.length() }
                    ?: 0L
            }.getOrDefault(0L)
        }

    suspend fun clearCache() =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = cacheDirProvider()
                dir.listFiles()
                    ?.filter { it.isFile && !it.name.endsWith(PART_SUFFIX) }
                    ?.forEach { it.delete() }
            }
            for ((url, flow) in progressFlows) {
                if (!downloads.containsKey(url)) {
                    flow.value = null
                    loadedFlow(url).value = emptyList()
                }
            }
        }

    fun close() {
        val socket =
            synchronized(lock) {
                val s = serverSocket
                serverSocket = null
                serverPort = -1
                s
            }
        runCatching { socket?.close() }
        downloads.values.forEach { it.cancel() }
        downloads.clear()
        ids.clear()
        queued.clear()
    }

    private fun ensureServer(): Int {
        synchronized(lock) {
            val existing = serverSocket
            if (existing != null && !existing.isClosed && serverPort > 0) {
                return serverPort
            }
            val socket = ServerSocket(0, 50, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
            serverSocket = socket
            serverPort = socket.localPort
            scope.launch { acceptLoop(socket) }
            return serverPort
        }
    }

    private suspend fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val clientSocket =
                try {
                    socket.accept()
                } catch (_: IOException) {
                    break
                }
            scope.launch { handleClient(clientSocket) }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            val input = s.getInputStream()
            val output = s.getOutputStream()
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0].uppercase(Locale.US)
            val path = parts[1].substringBefore('?').trimStart('/')

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    headers[line.substring(0, colon).trim().lowercase(Locale.US)] =
                        line.substring(colon + 1).trim()
                }
            }

            val download = ids[path]
            if (download == null) {
                writeStatus(output, 404, "Not Found")
                return
            }

            if (method != "GET" && method != "HEAD") {
                writeStatus(output, 405, "Method Not Allowed")
                return
            }

            download.serve(output, headers["range"], headOnly = method == "HEAD")
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) {
                if (prev == '\r'.code && sb.isNotEmpty()) {
                    sb.setLength(sb.length - 1)
                }
                return sb.toString()
            }
            sb.append(b.toChar())
            prev = b
        }
    }

    private fun writeStatus(
        output: OutputStream,
        code: Int,
        reason: String,
    ) {
        val response = "HTTP/1.1 $code $reason\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        runCatching {
            output.write(response.toByteArray(Charsets.US_ASCII))
            output.flush()
        }
    }

    private fun downloadFor(
        url: String,
        file: File,
    ): Download = synchronized(lock) { downloadForLocked(url, file) }

    private fun downloadForLocked(
        url: String,
        file: File,
    ): Download {
        downloads[url]?.let { return it }
        val id = sha256Hex(url).take(24)
        val download = Download(id = id, url = url, finalFile = file)
        downloads[url] = download
        ids[id] = download
        return download
    }

    private fun schedule() {
        val toStart = mutableListOf<Download>()
        synchronized(lock) {
            val activeWatched =
                downloads.values.filter { d ->
                    !d.cancelled && !d.done.isCompleted && (d.url in watched || (awaited[d.url] ?: 0) > 0)
                }
            for (d in activeWatched) {
                if (!d.started) {
                    d.started = true
                    toStart.add(d)
                }
                d.paused = false
            }

            val hasWatchedPending = activeWatched.isNotEmpty()
            var runningBg = 0
            for (d in downloads.values) {
                if (d.url !in watched && (awaited[d.url] ?: 0) == 0 && d.started && !d.done.isCompleted) {
                    if (hasWatchedPending) {
                        d.paused = true
                    } else {
                        d.paused = false
                        runningBg++
                    }
                }
            }

            if (!hasWatchedPending) {
                val iter = queued.iterator()
                while (iter.hasNext() && runningBg < MAX_BACKGROUND) {
                    val d = iter.next()
                    if (d.cancelled || d.done.isCompleted) {
                        iter.remove()
                        continue
                    }
                    if (!d.started) {
                        d.started = true
                        d.paused = false
                        toStart.add(d)
                        runningBg++
                    }
                }
            }
        }
        toStart.forEach { d -> scope.launch { d.run() } }
    }

    private fun fileFor(url: String): File {
        val dir = cacheDirProvider().apply { mkdirs() }
        val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase(Locale.US)
        val safeExt = if (ext in listOf("webm", "mp4", "m4v", "mov", "ogg", "mkv")) ".$ext" else ".bin"
        return File(dir, "${sha256Hex(url)}$safeExt")
    }

    private fun partFileFor(finalFile: File): File = File(finalFile.parentFile, "${finalFile.name}$PART_SUFFIX")

    private fun touch(file: File) {
        runCatching { file.setLastModified(System.currentTimeMillis()) }
    }

    private fun pruneCache() {
        val limit = maxCacheBytes
        val dir = cacheDirProvider()
        val files =
            dir.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(PART_SUFFIX) }
                ?.sortedBy { it.lastModified() }
                ?.toMutableList()
                ?: return
        var total = files.sumOf { it.length() }
        while (total > limit && files.isNotEmpty()) {
            val oldest = files.removeAt(0)
            val len = oldest.length()
            if (oldest.delete()) {
                total -= len
            }
        }
    }

    private fun progressFlow(url: String): MutableStateFlow<Float?> =
        progressFlows.getOrPut(url) { MutableStateFlow(null) }

    private fun loadedFlow(url: String): MutableStateFlow<List<Pair<Float, Float>>> =
        loadedFlows.getOrPut(url) { MutableStateFlow(emptyList()) }

    private inner class Download(
        val id: String,
        val url: String,
        val finalFile: File,
    ) {
        val partFile: File = partFileFor(finalFile)
        val done = CompletableDeferred<Unit>()
        private val dataLock = Object()

        @Volatile
        var started: Boolean = false

        @Volatile
        var paused: Boolean = false

        @Volatile
        var cancelled: Boolean = false

        @Volatile
        var failed: Boolean = false

        @Volatile
        var totalLength: Long = -1L

        @Volatile
        var contentType: String = "video/webm"

        // Sequential bytes written from offset 0.
        @Volatile
        var sequentialEnd: Long = 0L

        // Additional ranges fetched ahead of sequentialEnd (e.g., WebM cues at EOF).
        private val extraRanges = mutableListOf<LongRange>()

        fun cancel() {
            cancelled = true
            synchronized(dataLock) { dataLock.notifyAll() }
            if (!done.isCompleted) done.complete(Unit)
        }

        suspend fun run() {
            try {
                var attempt = 0
                while (!cancelled && (totalLength < 0L || sequentialEnd < totalLength)) {
                    while (paused && !cancelled) {
                        delay(50)
                    }
                    if (cancelled) break

                    val address = url.toHttpUrlOrNull() ?: throw IOException("Invalid URL: $url")
                    val offset = sequentialEnd
                    val reqBuilder = Request.Builder().url(address)
                    if (offset > 0L) {
                        reqBuilder.header("Range", "bytes=$offset-")
                    }
                    var retryDelayMs = 0L
                    try {
                        client.newCall(reqBuilder.build()).execute().use { response ->
                            if (response.code == RetryAfter.HTTP_TOO_MANY_REQUESTS) {
                                val blockedUntil = RetryAfterTracker.blockedUntilMs(address.host, address.toString())
                                val waitMs =
                                    if (blockedUntil != null) {
                                        (blockedUntil - System.currentTimeMillis()).coerceAtLeast(0L)
                                    } else {
                                        0L
                                    }
                                retryDelayMs = maxOf(waitMs, 1500L)
                            } else if (!response.isSuccessful && response.code != 206) {
                                throw IOException("HTTP ${response.code}")
                            } else {
                                consumeResponse(response, offset)
                            }
                        }
                    } catch (e: IOException) {
                        if (cancelled) break
                        attempt++
                        if (attempt > MAX_RETRIES) {
                            throw e
                        }
                        retryDelayMs = minOf(500L * (1 shl (attempt - 1)), 5_000L)
                    }
                    if (retryDelayMs > 0L && !cancelled) {
                        delay(retryDelayMs)
                    }
                }

                if (!cancelled && (totalLength < 0L || sequentialEnd >= totalLength)) {
                    finalizeFile()
                } else {
                    failed = true
                }
            } catch (_: Throwable) {
                failed = true
            } finally {
                synchronized(dataLock) { dataLock.notifyAll() }
                synchronized(lock) {
                    if (downloads[url] === this@Download) {
                        downloads.remove(url)
                        ids.remove(id)
                    }
                    queued.remove(this@Download)
                }
                if (!done.isCompleted) done.complete(Unit)
                schedule()
            }
        }

        private fun consumeResponse(
            response: Response,
            requestedOffset: Long,
        ) {
            val body = response.body
            response.header("Content-Type")?.let { contentType = it }
            val resolvedTotal =
                if (response.code == 206) {
                    parseContentRangeTotal(response.header("Content-Range"))
                        ?: (requestedOffset + body.contentLength().coerceAtLeast(0L))
                } else {
                    if (requestedOffset > 0L && response.code == 200) {
                        // Server ignored Range; restart from 0.
                        sequentialEnd = 0L
                    }
                    body.contentLength()
                }
            if (resolvedTotal > 0L) {
                totalLength = resolvedTotal
            }
            synchronized(dataLock) { dataLock.notifyAll() }

            partFile.parentFile?.mkdirs()
            RandomAccessFile(partFile, "rw").use { raf ->
                if (totalLength > 0L && raf.length() < totalLength) {
                    raf.setLength(totalLength)
                }
                var writePos = if (response.code == 200 && requestedOffset > 0L) 0L else requestedOffset
                raf.seek(writePos)
                val buffer = ByteArray(16 * 1024)
                val stream = body.byteStream()
                while (!cancelled) {
                    if (paused) {
                        // Release network stream when paused for a watched video; resume later via Range.
                        break
                    }
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (count > 0) {
                        raf.write(buffer, 0, count)
                        writePos += count
                        synchronized(dataLock) {
                            if (writePos > sequentialEnd) {
                                sequentialEnd = writePos
                            }
                            dataLock.notifyAll()
                        }
                        publishProgress()
                    }
                }
            }
            if (totalLength < 0L && !paused && !cancelled) {
                totalLength = sequentialEnd
            }
        }

        private fun finalizeFile() {
            if (partFile.exists()) {
                if (totalLength > 0L) {
                    RandomAccessFile(partFile, "rw").use { it.setLength(totalLength) }
                }
                if (finalFile.exists()) finalFile.delete()
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }
                touch(finalFile)
            }
            progressFlow(url).value = 1f
            loadedFlow(url).value = ALL_LOADED
            pruneCache()
        }

        fun serve(
            output: OutputStream,
            rangeHeader: String?,
            headOnly: Boolean,
        ) {
            // Wait briefly for headers/totalLength if download just started.
            val deadline = System.currentTimeMillis() + 10_000L
            synchronized(dataLock) {
                while (totalLength < 0L && !failed && !cancelled && !done.isCompleted && System.currentTimeMillis() < deadline) {
                    dataLock.wait(50)
                }
            }
            if (failed && totalLength < 0L) {
                writeStatus(output, 502, "Bad Gateway")
                return
            }

            val total = totalLength
            val range = parseRequestRange(rangeHeader, total)
            if (rangeHeader != null && range == null && total > 0L) {
                val resp =
                    "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$total\r\nConnection: close\r\n\r\n"
                output.write(resp.toByteArray(Charsets.US_ASCII))
                output.flush()
                return
            }

            val start = range?.first ?: 0L
            val end =
                when {
                    range != null -> range.last
                    total > 0L -> total - 1L
                    else -> -1L
                }
            val length = if (end >= start) end - start + 1L else -1L

            val headerBuilder = StringBuilder()
            if (range != null && total > 0L) {
                headerBuilder.append("HTTP/1.1 206 Partial Content\r\n")
                headerBuilder.append("Content-Range: bytes $start-$end/$total\r\n")
            } else {
                headerBuilder.append("HTTP/1.1 200 OK\r\n")
            }
            headerBuilder.append("Content-Type: $contentType\r\n")
            headerBuilder.append("Accept-Ranges: bytes\r\n")
            if (length >= 0L) {
                headerBuilder.append("Content-Length: $length\r\n")
            }
            headerBuilder.append("Connection: close\r\n\r\n")

            output.write(headerBuilder.toString().toByteArray(Charsets.US_ASCII))
            output.flush()
            if (headOnly) return

            // If requesting far ahead of sequentialEnd (e.g., WebM cue index at EOF), fetch directly upstream.
            if (start > sequentialEnd + AHEAD_FETCH_THRESHOLD_BYTES && !hasRange(start, end)) {
                fetchAheadRange(start, end)
            }

            var pos = start
            val buffer = ByteArray(16 * 1024)
            while (!cancelled && (end < 0L || pos <= end)) {
                val availableEnd = availableEndAt(pos)
                if (availableEnd > pos) {
                    val toRead =
                        if (end >= 0L) {
                            minOf(buffer.size.toLong(), availableEnd - pos, end - pos + 1L).toInt()
                        } else {
                            minOf(buffer.size.toLong(), availableEnd - pos).toInt()
                        }
                    val sourceFile = if (finalFile.exists()) finalFile else partFile
                    if (!sourceFile.exists()) break
                    val read =
                        RandomAccessFile(sourceFile, "r").use { raf ->
                            raf.seek(pos)
                            raf.read(buffer, 0, toRead)
                        }
                    if (read <= 0) break
                    try {
                        output.write(buffer, 0, read)
                        output.flush()
                    } catch (_: IOException) {
                        break
                    }
                    pos += read
                } else {
                    if (done.isCompleted) {
                        // Download finished while serving; check once more for finalFile.
                        if (finalFile.exists() && pos < finalFile.length()) continue
                        break
                    }
                    synchronized(dataLock) {
                        dataLock.wait(50)
                    }
                }
            }
        }

        private fun hasRange(
            start: Long,
            end: Long,
        ): Boolean =
            synchronized(dataLock) {
                if (end >= 0L && end < sequentialEnd) return@synchronized true
                extraRanges.any { start in it && (end < 0L || end in it) }
            }

        private fun availableEndAt(pos: Long): Long =
            synchronized(dataLock) {
                if (pos < sequentialEnd) return@synchronized sequentialEnd
                val match = extraRanges.firstOrNull { pos in it }
                if (match != null) match.last + 1L else pos
            }

        private fun fetchAheadRange(
            start: Long,
            end: Long,
        ) {
            val address = url.toHttpUrlOrNull() ?: return
            val rangeEnd = if (end >= start) end else start + AHEAD_FETCH_THRESHOLD_BYTES - 1L
            val request =
                Request
                    .Builder()
                    .url(address)
                    .header("Range", "bytes=$start-$rangeEnd")
                    .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) return@use
                    val bytes = response.body.bytes()
                    if (bytes.isEmpty()) return@use
                    partFile.parentFile?.mkdirs()
                    RandomAccessFile(partFile, "rw").use { raf ->
                        raf.seek(start)
                        raf.write(bytes)
                    }
                    synchronized(dataLock) {
                        extraRanges.add(start until (start + bytes.size))
                        dataLock.notifyAll()
                    }
                    publishProgress()
                }
            }
        }

        private fun publishProgress() {
            val total = totalLength
            if (total <= 0L) return
            val seq = (sequentialEnd.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            progressFlow(url).value = seq
            val ranges =
                synchronized(dataLock) {
                    buildList {
                        if (sequentialEnd > 0L) add(0f to seq)
                        for (r in extraRanges) {
                            val s = (r.first.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            val e = ((r.last + 1L).toFloat() / total.toFloat()).coerceIn(s, 1f)
                            add(s to e)
                        }
                    }
                }
            loadedFlow(url).value = ranges
        }
    }

    companion object {
        private const val CACHE_DIR_NAME = "orbin_video_cache"
        private const val PART_SUFFIX = ".part"
        const val DEFAULT_MAX_CACHE_BYTES = 300L * 1024 * 1024
        private const val MAX_BACKGROUND = 2
        private const val MAX_QUEUED = 6
        private const val MAX_RETRIES = 4
        private const val AHEAD_FETCH_THRESHOLD_BYTES = 256L * 1024L
        private val ALL_LOADED = listOf(0f to 1f)

        private fun sha256Hex(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        private fun parseContentRangeTotal(header: String?): Long? {
            if (header == null) return null
            val slash = header.lastIndexOf('/')
            if (slash < 0) return null
            return header.substring(slash + 1).trim().toLongOrNull()
        }

        private fun parseRequestRange(
            header: String?,
            totalLength: Long,
        ): LongRange? {
            if (header == null || !header.startsWith("bytes=", ignoreCase = true)) return null
            val spec = header.substringAfter('=').substringBefore(',').trim()
            val dash = spec.indexOf('-')
            if (dash < 0) return null
            val startPart = spec.substring(0, dash).trim()
            val endPart = spec.substring(dash + 1).trim()
            return if (startPart.isEmpty()) {
                val suffix = endPart.toLongOrNull() ?: return null
                if (totalLength <= 0L) return null
                val start = (totalLength - suffix).coerceAtLeast(0L)
                start until totalLength
            } else {
                val start = startPart.toLongOrNull() ?: return null
                if (totalLength > 0L && start >= totalLength) return null
                val end =
                    endPart.toLongOrNull()?.let {
                        if (totalLength > 0L) minOf(it, totalLength - 1L) else it
                    } ?: if (totalLength > 0L) totalLength - 1L else Long.MAX_VALUE - 1L
                if (end < start) return null
                start..end
            }
        }
    }
}
