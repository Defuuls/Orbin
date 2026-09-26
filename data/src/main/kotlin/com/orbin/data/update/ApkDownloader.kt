package com.orbin.data.update

import com.orbin.network.di.BaseOkHttp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Downloads a release APK and checks it against the release's published SHA-256.
 *
 * The hash is computed while the bytes are written, so a file that reaches the installer is
 * exactly the one the release describes; one that does not match is deleted, never kept.
 */
class ApkDownloader
    @Inject
    constructor(
        @BaseOkHttp baseClient: OkHttpClient,
    ) {
        // An APK is megabytes over a possibly slow connection: give each read longer than an API
        // call gets, and no overall cap, since progress is visible and the reader can cancel.
        private val client =
            baseClient
                .newBuilder()
                .readTimeout(DOWNLOAD_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(0, TimeUnit.SECONDS)
                .build()

        /**
         * @param onProgress 0..1 as bytes arrive, or null when the server gives no length.
         * @throws UpdateVerificationException when the download does not match its checksum.
         */
        suspend fun download(
            apkUrl: String,
            checksumUrl: String,
            target: File,
            onProgress: (Float?) -> Unit,
        ): File {
            val expected = parseSha256(fetchText(checksumUrl))
            target.parentFile?.mkdirs()
            var verified = false
            try {
                val actual = fetchHashing(apkUrl, target, onProgress)
                if (actual != expected) {
                    throw UpdateVerificationException("The download doesn't match its published checksum")
                }
                verified = true
                return target
            } finally {
                // Cancelled, failed or mismatched: an unverified file is never left for the installer.
                if (!verified) target.delete()
            }
        }

        /** Writes [url] to [target] and returns the SHA-256 of exactly the bytes written, as hex. */
        private suspend fun fetchHashing(
            url: String,
            target: File,
            onProgress: (Float?) -> Unit,
        ): String {
            val digest = MessageDigest.getInstance("SHA-256")
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
                val total = response.body.contentLength().takeIf { it > 0 }
                response.body.byteStream().use { input ->
                    target.outputStream().use { output -> copyHashing(input, output, digest, total, onProgress) }
                }
            }
            return digest.digest().toHex()
        }

        /** Copies [input] to [output], feeding [digest] and reporting progress; stops when cancelled. */
        private suspend fun copyHashing(
            input: InputStream,
            output: OutputStream,
            digest: MessageDigest,
            total: Long?,
            onProgress: (Float?) -> Unit,
        ) {
            val buffer = ByteArray(BUFFER_BYTES)
            var received = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) return
                output.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                received += read
                onProgress(total?.let { received.toFloat() / it })
            }
        }

        private fun fetchText(url: String): String =
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Checksum download failed (HTTP ${response.code})")
                response.body.string()
            }

        private companion object {
            const val DOWNLOAD_READ_TIMEOUT_SECONDS = 60L
            const val BUFFER_BYTES = 64 * 1024
        }
    }

/** An update that downloaded but must not be installed. Its message is shown to the reader. */
class UpdateVerificationException(
    message: String,
) : IOException(message)

/**
 * The hash from a `sha256sum`-style line: `<64 hex digits>  <file name>`.
 *
 * @throws UpdateVerificationException when there is no well-formed hash to check against.
 */
internal fun parseSha256(text: String): String {
    val hash =
        text
            .trim()
            .substringBefore(' ')
            .lowercase()
    if (hash.length != SHA256_HEX_LENGTH || !hash.all { it in '0'..'9' || it in 'a'..'f' }) {
        throw UpdateVerificationException("The release's checksum file is unreadable")
    }
    return hash
}

private const val SHA256_HEX_LENGTH = 64

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
