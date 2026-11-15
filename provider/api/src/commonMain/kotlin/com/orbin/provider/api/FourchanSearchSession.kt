package com.orbin.provider.api

import com.orbin.core.model.AppSettings
import com.orbin.core.model.Board
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PosterInfo
import com.orbin.core.model.ThreadStats
import com.orbin.core.model.UserContentFilters
import com.orbin.core.model.isPermanentlyFiltered
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fail closed until board metadata is available, including each board's NSFW classification. */
fun WebSearchHit.isVisibleInOrbin(
    settings: AppSettings,
    boards: List<Board>,
): Boolean {
    val board = boards.firstOrNull { it.id == link.key.board } ?: return false
    if (board.isPermanentlyFiltered() || settings.nsfwContentHidden && board.isNsfw) return false
    val post =
        Post(
            id = link.post ?: PostId(link.key.thread.value),
            board = link.key.board,
            threadId = link.key.thread,
            isOriginalPost = link.post == null || link.post.value == link.key.thread.value,
            subject = subject,
            comment = PostComment(comment, persistentListOf()),
            poster = PosterInfo(name = name, tripcode = tripcode, countryCode = countryCode, countryName = countryName),
        )
    val filters = UserContentFilters(settings.postFilterRules, settings.hiddenThreadPatterns)
    return !post.isPermanentlyFiltered() &&
        filters.reasonFor(post) == null &&
        !filters.hidesThread(CatalogThread(link.key, post, ThreadStats()))
}

data class FourchanSearchState(
    val query: String = "",
    val requestUrl: String? = null,
    val requestId: Long = 0,
    val offset: Int = 0,
    val hits: List<WebSearchHit> = emptyList(),
    val loading: Boolean = false,
    val verifying: Boolean = false,
    val error: String? = null,
    val nextOffset: Int? = null,
)

/** Shared request identity, pagination and late-response guards for both native browser hosts. */
class FourchanSearchSession(
    val provider: ProviderMetadata,
    private val accepts: (WebSearchHit) -> Boolean,
) {
    private val mutable = MutableStateFlow(FourchanSearchState())
    val state = mutable.asStateFlow()
    private var raw = emptyList<WebSearchHit>()

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed == state.value.query && state.value.requestUrl != null) return
        raw = emptyList()
        mutable.value =
            FourchanSearchState(
                query = trimmed,
                requestUrl = fourchanWebSearchUrl(trimmed),
                requestId = state.value.requestId + 1,
                loading = trimmed.isNotEmpty(),
            )
    }

    /** Explicit submission repeats the same query, while re-entering a screen only observes it. */
    fun submit(query: String) {
        if (query.trim().isEmpty()) return
        if (query.trim() == state.value.query && state.value.requestUrl != null) retry() else search(query)
    }

    fun retry() {
        if (state.value.requestUrl == null) return
        mutable.value =
            state.value.copy(loading = true, verifying = false, error = null, requestId = state.value.requestId + 1)
    }

    fun more() {
        val current = state.value
        if (current.loading || current.verifying) return
        val next = current.nextOffset ?: return
        mutable.value =
            current.copy(
                requestUrl = fourchanWebSearchUrl(current.query, next),
                offset = next,
                loading = true,
                verifying = false,
                error = null,
                requestId = current.requestId + 1,
            )
    }

    fun started(
        url: String?,
        requestId: Long? = null,
    ) {
        if (matches(url, requestId)) mutable.value = state.value.copy(loading = true, verifying = false, error = null)
    }

    fun page(
        url: String?,
        payload: String,
        requestId: Long? = null,
    ) {
        if (!matches(url, requestId)) return
        val page = parseFourchanWebSearchPage(payload, provider)
        if (page == null) {
            fail(url, "4chan returned an unrecognized search page. Retry the search.", requestId)
        } else if (page.verifying) {
            mutable.value = state.value.copy(loading = false, verifying = true, error = null)
        } else {
            raw = (if (state.value.offset == 0) page.hits else raw + page.hits).distinctBy { it.link }
            mutable.value =
                state.value.copy(
                    hits = raw.filter(accepts),
                    loading = false,
                    verifying = false,
                    error = null,
                    nextOffset = page.nextOffset?.takeIf { it > state.value.offset },
                )
        }
    }

    fun refilter() {
        mutable.value = state.value.copy(hits = raw.filter(accepts))
    }

    fun fail(
        url: String?,
        message: String,
        requestId: Long? = null,
    ) {
        if (matches(url, requestId)) {
            mutable.value =
                state.value.copy(loading = false, verifying = false, error = message)
        }
    }

    private fun matches(
        url: String?,
        requestId: Long? = null,
    ): Boolean {
        if (requestId != null && requestId != state.value.requestId) return false
        if (url == null || !isFourchanWebSearchUrl(url)) return false
        val parameters =
            url.substringAfter('?', "").substringBefore('#').split('&').associate {
                it.substringBefore('=') to it.substringAfter('=', "")
            }
        val encoded = parameters["q"]?.replace("+", "%20") ?: return false
        val decoded = UriParts.parse("https://find.4chan.org/$encoded")?.path?.removePrefix("/")
        return decoded == state.value.query && (parameters["o"]?.toIntOrNull() ?: 0) == state.value.offset
    }
}
