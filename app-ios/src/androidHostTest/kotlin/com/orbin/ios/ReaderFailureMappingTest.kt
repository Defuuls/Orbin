package com.orbin.ios

import com.orbin.provider.api.ProviderException
import com.orbin.uinext.ReaderFailureCause
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderFailureMappingTest {
    @Test
    fun typedProviderFailuresRetainTheCauseUsedForInitialAndRefreshFeedback() {
        val cases =
            listOf(
                ProviderException.Network("Offline") to BrowserFailureCause.NETWORK,
                ProviderException.NotFound("Deleted") to BrowserFailureCause.NOT_FOUND,
                ProviderException.Http(404, "Missing") to BrowserFailureCause.NOT_FOUND,
                ProviderException.Http(503, "Unavailable") to BrowserFailureCause.SERVER,
                ProviderException.RateLimited() to BrowserFailureCause.SERVER,
                ProviderException.Parse("Invalid response") to BrowserFailureCause.UNKNOWN,
                IllegalStateException("Unexpected failure") to BrowserFailureCause.UNKNOWN,
            )
        cases.forEach { (error, expected) -> assertEquals(expected, error.browserFailureCause()) }
    }

    @Test
    fun everyHostCauseMapsToTheEquivalentSharedStateCause() {
        BrowserFailureCause.entries.forEach { cause ->
            assertEquals(ReaderFailureCause.valueOf(cause.name), cause.toReaderCause())
        }
    }
}
