package com.orbin.data.web

import com.orbin.core.common.web.WebViewBrowsingData
import com.orbin.domain.repository.WebSearchDataRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** WebView's cookie and storage managers are main-thread objects, whoever asks for the clear. */
class WebViewSearchDataRepository internal constructor(
    private val mainDispatcher: CoroutineDispatcher,
    private val clearWebViewData: () -> Unit,
) : WebSearchDataRepository {
    @Inject
    constructor() : this(Dispatchers.Main.immediate, { WebViewBrowsingData.clear() })

    override suspend fun clear() = withContext(mainDispatcher) { clearWebViewData() }
}
