package com.orbin.core.common.network

import kotlinx.coroutines.flow.Flow

/**
 * Reports whether DNS lookups are actually being encrypted, and holds the one choice the reader
 * has about it.
 *
 * Encrypted DNS is always on — there is no switch to turn it off. Some networks block the
 * well-known DoH resolvers outright, though, and refusing to resolve at all would leave the app
 * unable to load anything with no way out from inside it. So by default a blocked resolver falls
 * back to the system resolver, and this reports that it happened: a user whose DNS has quietly
 * stopped being private is worse off than one who knows and can switch networks or resolvers.
 *
 * A reader who would rather have nothing load than leak which sites they visit to the network
 * turns on [strictMode]: lookups then fail instead of falling back.
 */
interface DnsPrivacyMonitor {
    /**
     * Emits true once a lookup has fallen back to the system resolver, and false again once an
     * encrypted lookup succeeds. Reflects the last lookup, not a permanent verdict — moving to a
     * network that permits DoH clears it without a restart.
     */
    val usingSystemFallback: Flow<Boolean>

    /** True when a blocked resolver makes lookups fail rather than fall back. Off by default. */
    val strictMode: Flow<Boolean>

    /** Persists [strictMode]; it applies from the next lookup, no restart needed. */
    fun setStrictMode(enabled: Boolean)
}
