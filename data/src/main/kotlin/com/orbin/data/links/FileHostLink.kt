package com.orbin.data.links

import com.orbin.core.model.LinkStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A file-host link parsed down to what its host's API needs to look it up. */
internal sealed interface FileHostLink {
    data class GoFile(
        val contentId: String,
    ) : FileHostLink

    data class Mega(
        val handle: String,
        val isFolder: Boolean,
    ) : FileHostLink

    data class FastFile(
        val url: String,
    ) : FileHostLink

    companion object {
        private val gofileContentId = Regex("""(?i)gofile\.io/d/([A-Za-z0-9]+)""")
        private val megaModern = Regex("""(?i)mega(?:\.co)?\.nz/(file|folder|embed)/([\w-]+)""")
        private val megaLegacy = Regex("""(?i)mega(?:\.co)?\.nz/#(F?)!([\w-]+)""")
        private val fastFileHost = Regex("""(?i)^https?://(?:[\w-]+\.)*fast-file\.ru/\S+""")

        /** Parses [url] into a host-specific lookup, or null when no supported host matches. */
        fun parse(url: String): FileHostLink? {
            val gofile = gofileContentId.find(url)?.let { GoFile(contentId = it.groupValues[1]) }
            val megaModernLink =
                megaModern.find(url)?.let {
                    Mega(handle = it.groupValues[2], isFolder = it.groupValues[1].lowercase() != "file")
                }
            val megaLegacyLink =
                megaLegacy.find(url)?.let {
                    Mega(handle = it.groupValues[2], isFolder = it.groupValues[1].isNotEmpty())
                }
            val fastFile = if (fastFileHost.matches(url)) FastFile(url) else null
            return gofile ?: megaModernLink ?: megaLegacyLink ?: fastFile
        }
    }
}

/**
 * Pure response-to-status interpreters, separated from the HTTP plumbing so they are unit-testable
 * against captured API payloads.
 */
internal object LinkProbeResponses {
    /** MEGA error code for "object not found" — the canonical dead-link answer. */
    private const val MEGA_ENOENT = -9L

    /** MEGA error code for links disabled by takedown. */
    private const val MEGA_ETOOMANY = -16L

    /**
     * MEGA's `cs` endpoint answers a lookup with either a bare error number, an array holding an
     * error number, or an array holding the node object. Only the documented "gone" codes count
     * as dead; anything unexpected stays [LinkStatus.UNKNOWN] rather than guessing.
     */
    fun megaStatus(body: String): LinkStatus {
        val element = runCatching { Json.parseToJsonElement(body.trim()) }.getOrNull() ?: return LinkStatus.UNKNOWN
        val first =
            when (element) {
                is JsonArray -> element.firstOrNull() ?: return LinkStatus.UNKNOWN
                else -> element
            }
        return when (first) {
            is JsonObject -> LinkStatus.VALID
            is JsonPrimitive ->
                when (first.longOrNull) {
                    MEGA_ENOENT, MEGA_ETOOMANY -> LinkStatus.INVALID
                    else -> LinkStatus.UNKNOWN
                }
            else -> LinkStatus.UNKNOWN
        }
    }

    /**
     * gofile's contents endpoint reports a `status` string. Password-protected or link-restricted
     * content still exists, so those count as alive; only an explicit not-found is dead.
     */
    fun gofileStatus(body: String): LinkStatus {
        val status =
            runCatching { Json.parseToJsonElement(body) }
                .getOrNull()
                ?.let { (it as? JsonObject)?.get("status") }
                ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
                ?: return LinkStatus.UNKNOWN
        return when (status) {
            "ok", "error-passwordRequired", "error-notPublic" -> LinkStatus.VALID
            "error-notFound" -> LinkStatus.INVALID
            else -> LinkStatus.UNKNOWN
        }
    }

    private val fastFileDeadMarkers =
        listOf(
            // The host serves its "gone" page in Russian and English depending on locale.
            "файл не найден",
            "файл удал",
            "file not found",
            "file was deleted",
            "file has been deleted",
            "file does not exist",
        )

    /** fast-file.ru has no API: a plain page fetch answers via status code and page copy. */
    fun fastFileStatus(
        httpCode: Int,
        body: String,
    ): LinkStatus =
        when {
            httpCode == HTTP_NOT_FOUND || httpCode == HTTP_GONE -> LinkStatus.INVALID
            httpCode !in HTTP_OK_RANGE -> LinkStatus.UNKNOWN
            fastFileDeadMarkers.any { body.contains(it, ignoreCase = true) } -> LinkStatus.INVALID
            else -> LinkStatus.VALID
        }

    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_GONE = 410
    private val HTTP_OK_RANGE = 200..299
}
