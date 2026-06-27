package com.orbin.core.model

/** Lifecycle of a media download. Mirrors the states the platform download manager reports. */
enum class DownloadStatus { QUEUED, RUNNING, COMPLETED, FAILED }

/** A record of a media download, surfaced in the downloads screen. */
data class DownloadRecord(
    /** Platform download id (from the system DownloadManager). */
    val id: Long,
    val url: String,
    val fileName: String,
    val status: DownloadStatus,
    val createdAtMillis: Long,
)
