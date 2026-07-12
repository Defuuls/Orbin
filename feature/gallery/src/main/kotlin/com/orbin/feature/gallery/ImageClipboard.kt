package com.orbin.feature.gallery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal enum class ImageCopyResult {
    IMAGE,
    URL,
}

internal suspend fun copyImageToClipboard(
    context: Context,
    imageUrl: String,
): ImageCopyResult {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val imageUri = runCatching { cacheImageForClipboard(context, imageUrl) }.getOrNull()

    return if (imageUri != null) {
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "Image", imageUri))
        ImageCopyResult.IMAGE
    } else {
        clipboard.setPrimaryClip(ClipData.newPlainText("Image URL", imageUrl))
        ImageCopyResult.URL
    }
}

private suspend fun cacheImageForClipboard(
    context: Context,
    imageUrl: String,
): Uri =
    withContext(Dispatchers.IO) {
        val connection =
            (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                requestMethod = "GET"
            }

        try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "Image request failed with HTTP ${connection.responseCode}"
            }

            val contentLength = connection.contentLengthLong
            check(contentLength <= MAX_IMAGE_BYTES || contentLength < 0) {
                "Image exceeds clipboard cache limit"
            }

            val extension = extensionFor(connection.contentType, imageUrl)
            val directory = File(context.cacheDir, CLIPBOARD_DIRECTORY).apply { mkdirs() }
            val file = File(directory, "${imageUrl.sha256()}.$extension")

            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        check(total <= MAX_IMAGE_BYTES) { "Image exceeds clipboard cache limit" }
                        output.write(buffer, 0, read)
                    }
                }
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        } finally {
            connection.disconnect()
        }
    }

private fun extensionFor(
    contentType: String?,
    imageUrl: String,
): String =
    when (contentType?.substringBefore(';')?.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/avif" -> "avif"
        else ->
            imageUrl
                .substringBefore('?')
                .substringAfterLast('.', missingDelimiterValue = "img")
                .lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]{2,5}")) }
                ?: "img"
    }

private fun String.sha256(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

private const val CLIPBOARD_DIRECTORY = "clipboard_images"
private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000
private const val MAX_IMAGE_BYTES = 50L * 1024L * 1024L
