package com.orbin.provider.vichan.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the vichan / 4chan-compatible JSON API. These mirror the server response exactly;
 * mapping to domain models happens in [com.orbin.provider.vichan.VichanMapper]. Unknown fields are
 * ignored by the shared lenient [kotlinx.serialization.json.Json], so engine variations are
 * tolerated.
 */

@Serializable
data class VichanBoardsResponse(
    val boards: List<VichanBoard> = emptyList(),
)

@Serializable
data class VichanBoard(
    val board: String,
    val title: String = "",
    @SerialName("meta_description") val metaDescription: String = "",
    /** 1 if the board is "worksafe" (SFW). */
    @SerialName("ws_board") val workSafe: Int = 0,
    val pages: Int? = null,
    @SerialName("bump_limit") val bumpLimit: Int? = null,
    @SerialName("image_limit") val imageLimit: Int? = null,
    @SerialName("max_comment_chars") val maxCommentChars: Int? = null,
)

/** Catalog endpoint returns an array of pages, each holding thread OPs. */
@Serializable
data class VichanCatalogPage(
    val page: Int = 0,
    val threads: List<VichanPost> = emptyList(),
)

/** Thread endpoint returns the OP followed by all replies. */
@Serializable
data class VichanThreadResponse(
    val posts: List<VichanPost> = emptyList(),
)

/**
 * A post as returned by the API. The same shape is used for OPs (where [resto] == 0) and replies.
 * File fields are only present when the post has an attachment.
 */
@Serializable
data class VichanPost(
    val no: Long,
    /** "Reply to" — 0 for the OP, otherwise the thread number. */
    val resto: Long = 0,
    val time: Long = 0,
    val name: String? = null,
    val trip: String? = null,
    val id: String? = null,
    val capcode: String? = null,
    val country: String? = null,
    @SerialName("country_name") val countryName: String? = null,
    val sub: String? = null,
    val com: String? = null,
    // File fields:
    val tim: String? = null,
    val filename: String? = null,
    val ext: String? = null,
    val fsize: Long = 0,
    val w: Int = 0,
    val h: Int = 0,
    @SerialName("tn_w") val tnW: Int = 0,
    @SerialName("tn_h") val tnH: Int = 0,
    val spoiler: Int = 0,
    // Stats (present on OPs):
    val replies: Int = 0,
    val images: Int = 0,
    @SerialName("unique_ips") val uniqueIps: Int = 0,
    val sticky: Int = 0,
    val closed: Int = 0,
    val locked: Int = 0,
    val archived: Int = 0,
    @SerialName("last_modified") val lastModified: Long = 0,
    /** Multi-file posts (vichan extension). */
    @SerialName("extra_files") val extraFiles: List<VichanFile> = emptyList(),
)

@Serializable
data class VichanFile(
    val tim: String? = null,
    val filename: String? = null,
    val ext: String? = null,
    val fsize: Long = 0,
    val w: Int = 0,
    val h: Int = 0,
    @SerialName("tn_w") val tnW: Int = 0,
    @SerialName("tn_h") val tnH: Int = 0,
    val spoiler: Int = 0,
)
