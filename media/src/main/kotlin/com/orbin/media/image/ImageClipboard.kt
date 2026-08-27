package com.orbin.media.image

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.orbin.network.di.BaseOkHttp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the clipboard ended up holding the image itself, or only a link to it. */
enum class ImageCopyResult {
    IMAGE,
    URL,
}

/**
 * Copies a viewed image to the system clipboard, fetching it through the app's own HTTP stack.
 *
 * It used to open a raw [java.net.HttpURLConnection]. That still spoke HTTPS — the platform
 * enforces it — but it went around everything the shared client exists to provide: the request
 * resolved through the system resolver rather than DNS-over-HTTPS, so it announced to the network
 * which host was being read from at the moment of the copy, and it carried the JVM's default
 * user-agent rather than the configured one, making that single request identifiably different
 * from every other request the app makes. One copy was enough to undo settings the user had
 * deliberately turned on.
 *
 * Lives in :media rather than the gallery feature because this is the layer that already holds a
 * network client, and a feature reaching for OkHttp directly is how that boundary erodes.
 */
@Singleton
class ImageClipboard
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @BaseOkHttp private val okHttpClient: OkHttpClient,
    ) {
        /**
         * Puts [imageUrl]'s contents on the clipboard, falling back to the URL as plain text when
         * the file cannot be fetched — a failed copy still leaves the reader something to paste.
         */
        suspend fun copy(imageUrl: String): ImageCopyResult {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val imageUri = runCatching { cache(imageUrl) }.getOrNull()

            return if (imageUri != null) {
                clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "Image", imageUri))
                ImageCopyResult.IMAGE
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("Image URL", imageUrl))
                ImageCopyResult.URL
            }
        }

        private suspend fun cache(imageUrl: String): Uri =
            withContext(Dispatchers.IO) {
                okHttpClient
                    .newCall(Request.Builder().url(imageUrl).build())
                    .execute()
                    .use { response ->
                        check(response.isSuccessful) {
                            "Image request failed with HTTP ${response.code}"
                        }
                        val body = response.body

                        // A declared length over the cap is refused before a byte is read; the
                        // running total below is what catches a server that under-declares or
                        // sends no length at all.
                        val declaredLength = body.contentLength()
                        check(declaredLength <= MAX_IMAGE_BYTES) {
                            "Image exceeds clipboard cache limit"
                        }

                        val extension = extensionFor(response.header("Content-Type"), imageUrl)
                        val directory = File(context.cacheDir, CLIPBOARD_DIRECTORY).apply { mkdirs() }
                        val file = File(directory, "${imageUrl.sha256()}.$extension")

                        body.byteStream().use { input ->
                            file.outputStream().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                var total = 0L
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    total += read
                                    check(total <= MAX_IMAGE_BYTES) {
                                        "Image exceeds clipboard cache limit"
                                    }
                                    output.write(buffer, 0, read)
                                }
                            }
                        }

                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                    }
            }
    }

internal fun extensionFor(
    contentType: String?,
    imageUrl: String,
): String =
    when (contentType?.substringBefore(';')?.trim()?.lowercase()) {
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
                .takeIf { it.matches(FILE_EXTENSION) }
                ?: "img"
    }

private fun String.sha256(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

private const val CLIPBOARD_DIRECTORY = "clipboard_images"
private const val MAX_IMAGE_BYTES = 50L * 1024L * 1024L

/**
 * The shape a URL's trailing segment must have to be treated as a file extension. Compiled once;
 * it was being rebuilt for every image copied to the clipboard.
 */
private val FILE_EXTENSION = Regex("[a-z0-9]{2,5}")
