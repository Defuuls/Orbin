package com.orbin.network

import com.orbin.core.common.network.DnsPrivacyMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records whether the last DNS lookup was encrypted, so the UI can say when it was not, and holds
 * the reader's strict encrypted DNS choice that `DynamicDns` reads on every lookup.
 *
 * Written from OkHttp's `Dns.lookup`, which runs on arbitrary connection-pool threads, hence the
 * [MutableStateFlow] rather than anything that assumes a single writer.
 */
@Singleton
class DohFallbackTracker
    @Inject
    constructor(
        private val strictStore: StrictDnsStore,
    ) : DnsPrivacyMonitor {
        /** Without a store the choice is kept in memory, off to start with. */
        constructor() : this(StrictDnsStore.InMemory())

        private val _usingSystemFallback = MutableStateFlow(false)
        private val _strictMode = MutableStateFlow(strictStore.load())

        override val usingSystemFallback: StateFlow<Boolean> = _usingSystemFallback.asStateFlow()

        override val strictMode: StateFlow<Boolean> = _strictMode.asStateFlow()

        override fun setStrictMode(enabled: Boolean) {
            strictStore.save(enabled)
            _strictMode.value = enabled
            // The fallback that was showing no longer happens, so stop reporting it.
            if (enabled) _usingSystemFallback.value = false
        }

        /** The chosen resolver answered: DNS is encrypted. */
        fun recordEncrypted() {
            _usingSystemFallback.value = false
        }

        /** The chosen resolver was unreachable and the system resolver answered instead. */
        fun recordFallback() {
            _usingSystemFallback.value = true
        }
    }
