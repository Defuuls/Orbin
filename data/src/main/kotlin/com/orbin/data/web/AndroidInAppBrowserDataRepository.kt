package com.orbin.data.web

import android.content.Context
import com.orbin.core.common.link.InAppBrowser
import com.orbin.domain.repository.InAppBrowserDataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The browser's data lives in its own process's WebView directory, which only that process can
 * reach, so the clear is handed to the browser's service there ([InAppBrowser.clearDataIntent]).
 * A refused start (the system can refuse while the app is in the background) throws, so the
 * caller reports the clear as failed instead of claiming success.
 */
class AndroidInAppBrowserDataRepository internal constructor(
    private val start: () -> Unit,
) : InAppBrowserDataRepository {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this({
        checkNotNull(context.startService(InAppBrowser.clearDataIntent(context))) { "Browser data service unavailable" }
    })

    override suspend fun clear() = start()
}
