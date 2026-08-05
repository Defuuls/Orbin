package com.orbin.network

import com.orbin.core.common.network.DnsPrivacyMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records whether the last DNS lookup was encrypted, so the UI can say when it was not.
 *
 * Written from OkHttp's `Dns.lookup`, which runs on arbitrary connection-pool threads, hence the
 * [MutableStateFlow] rather than anything that assumes a single writer.
 */
@Singleton
class DohFallbackTracker
    @Inject
    constructor() : DnsPrivacyMonitor {
        private val _usingSystemFallback = MutableStateFlow(false)

        override val usingSystemFallback: StateFlow<Boolean> = _usingSystemFallback.asStateFlow()

        /** The chosen resolver answered: DNS is encrypted. */
        fun recordEncrypted() {
            _usingSystemFallback.value = false
        }

        /** The chosen resolver was unreachable and the system resolver answered instead. */
        fun recordFallback() {
            _usingSystemFallback.value = true
        }
    }
