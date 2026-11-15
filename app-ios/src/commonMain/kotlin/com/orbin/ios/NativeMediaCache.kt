package com.orbin.ios

/** Owned video-cache bytes; playback buffers controlled by AVFoundation/WebKit are transient. */
internal expect suspend fun nativeVideoCacheUsageBytes(): Long

/** Clears cached frames, upcoming clips and WebKit data, without touching owned saved files. */
internal expect suspend fun clearNativeVideoCache()

internal fun mediaCacheSizeLabel(bytes: Long): String =
    when {
        bytes <= 0 -> "Empty"
        bytes < CACHE_KIBIBYTE -> "$bytes B"
        bytes < CACHE_MEBIBYTE -> "${(bytes + CACHE_KIBIBYTE - 1) / CACHE_KIBIBYTE} KiB"
        else -> "${bytes / CACHE_MEBIBYTE} MiB · $bytes B"
    }

private const val CACHE_KIBIBYTE = 1024L
private const val CACHE_MEBIBYTE = CACHE_KIBIBYTE * CACHE_KIBIBYTE
