package com.orbin.ios

import com.orbin.uinext.ReaderFailureCause

internal fun BrowserFailureCause.toReaderCause(): ReaderFailureCause =
    when (this) {
        BrowserFailureCause.NETWORK -> ReaderFailureCause.NETWORK
        BrowserFailureCause.NOT_FOUND -> ReaderFailureCause.NOT_FOUND
        BrowserFailureCause.SERVER -> ReaderFailureCause.SERVER
        BrowserFailureCause.UNKNOWN -> ReaderFailureCause.UNKNOWN
    }
