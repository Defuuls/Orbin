package com.orbin.core.model

import kotlinx.serialization.Serializable

/** A file owned by Orbin, separate from the operating system's download history. */
@Serializable
data class MediaLibraryRecord(
    val id: String,
    val providerId: String,
    val boardId: String? = null,
    val threadId: Long? = null,
    val postId: Long? = null,
    val attachmentId: String = "",
    val sourceUrl: String = "",
    val fileName: String,
    val mediaType: MediaType = MediaType.UNKNOWN,
    val mimeType: String = "application/octet-stream",
    val byteCount: Long = 0,
    val savedAtMillis: Long = 0,
    /** An opaque relative key resolved only by the platform's owned-file store. */
    val storageKey: String,
    val thumbnailStorageKey: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)

/** Source identity captured when a download is queued, retained through retries. */
@Serializable
data class DownloadMediaContext(
    val providerId: String,
    val boardId: String? = null,
    val threadId: Long? = null,
    val postId: Long? = null,
    val attachmentId: String = "",
    val mediaType: MediaType = MediaType.UNKNOWN,
    val mimeType: String = "application/octet-stream",
    val width: Int = 0,
    val height: Int = 0,
)
