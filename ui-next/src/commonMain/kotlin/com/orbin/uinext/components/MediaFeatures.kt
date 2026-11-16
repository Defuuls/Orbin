package com.orbin.uinext.components

/** Presentation kinds stay independent of provider models, and preserve nonvisual attachments in All. */
enum class MediaKind {
    IMAGE,
    VIDEO,
    AUDIO,
    UNKNOWN,
}

/** Maps provider model enum names without making the presentation module depend on those models. */
fun mediaKindOf(typeName: String): MediaKind =
    when (typeName) {
        "IMAGE", "ANIMATED_IMAGE" -> MediaKind.IMAGE
        "VIDEO" -> MediaKind.VIDEO
        "AUDIO" -> MediaKind.AUDIO
        else -> MediaKind.UNKNOWN
    }

enum class MediaKindFilter {
    ALL,
    IMAGES,
    VIDEOS,
    ;

    fun shows(kind: MediaKind): Boolean =
        when (this) {
            ALL -> true
            IMAGES -> kind == MediaKind.IMAGE
            VIDEOS -> kind == MediaKind.VIDEO
        }
}

/** Keep canonical indices, rather than re-identifying attachments by a possibly reused file id. */
fun mediaFilterIndices(
    kinds: List<MediaKind>,
    filter: MediaKindFilter,
): List<Int> = kinds.indices.filter { filter.shows(kinds[it]) }

enum class ReverseImageSearchEngine {
    GOOGLE_LENS,
    BING,
    YANDEX,
    SAUCENAO,
    ;

    fun searchUrl(imageUrl: String): String? {
        val encoded = safeMediaSearchTarget(imageUrl)?.let(::encodeMediaSearchParameter) ?: return null
        return when (this) {
            GOOGLE_LENS -> "https://lens.google.com/uploadbyurl?url=$encoded"
            BING -> "https://www.bing.com/images/search?view=detailv2&iss=sbi&q=imgurl:$encoded"
            YANDEX -> "https://yandex.com/images/search?rpt=imageview&url=$encoded"
            SAUCENAO -> "https://saucenao.com/search.php?url=$encoded"
        }
    }
}

/** Search services accept images. A video uses its provider preview and never its video bytes. */
fun mediaSearchTarget(
    sourceUrl: String,
    thumbnailUrl: String,
    kind: MediaKind,
): String? =
    when (kind) {
        MediaKind.IMAGE -> safeMediaSearchTarget(sourceUrl) ?: safeMediaSearchTarget(thumbnailUrl)
        MediaKind.VIDEO -> safeMediaSearchTarget(thumbnailUrl)
        MediaKind.AUDIO, MediaKind.UNKNOWN -> null
    }

private fun safeMediaSearchTarget(value: String): String? {
    if (value.any { it.isWhitespace() || it.isISOControl() || it == '\\' }) return null
    if (!value.startsWith("https://", ignoreCase = true)) return null
    val authority =
        value
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
    if (authority.isBlank() || '@' in authority) return null
    return value
}

private fun encodeMediaSearchParameter(value: String): String =
    buildString {
        value.encodeToByteArray().forEach { byte ->
            val number = byte.toInt() and BYTE_MASK
            if (number.toChar() in URI_UNRESERVED) {
                append(number.toChar())
            } else {
                append('%')
                append(HEX[number ushr NIBBLE_BITS])
                append(HEX[number and NIBBLE_MASK])
            }
        }
    }

data class MediaFileInfo(
    val fileName: String,
    val format: String,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0,
    val durationMs: Long? = null,
)

/** A completion can advance only the same settled page that produced it, while foregrounded. */
fun nextMediaPageAfterCompletion(
    completedKey: String,
    activeKey: String,
    index: Int,
    itemCount: Int,
    enabled: Boolean,
    foreground: Boolean,
    scrolling: Boolean,
): Int? {
    val canAdvance = enabled && foreground && !scrolling
    val samePageWithSuccessor = completedKey == activeKey && index >= 0 && index < itemCount - 1
    return if (canAdvance && samePageWithSuccessor) index + 1 else null
}

private const val HEX = "0123456789ABCDEF"

private const val BYTE_MASK = 0xff
private const val NIBBLE_BITS = 4
private const val NIBBLE_MASK = 15
private const val URI_UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
