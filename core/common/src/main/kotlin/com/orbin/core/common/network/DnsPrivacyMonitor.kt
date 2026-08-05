package com.orbin.core.common.network

import kotlinx.coroutines.flow.Flow

/**
 * Reports whether DNS lookups are actually being encrypted.
 *
 * Encrypted DNS is always on — there is no switch to turn it off. Some networks block the
 * well-known DoH resolvers outright, though, and refusing to resolve at all would leave the app
 * unable to load anything with no way out from inside it. So a blocked resolver falls back to the
 * system resolver, and this reports that it happened: a user whose DNS has quietly stopped being
 * private is worse off than one who knows and can switch networks or resolvers.
 */
interface DnsPrivacyMonitor {
    /**
     * Emits true once a lookup has fallen back to the system resolver, and false again once an
     * encrypted lookup succeeds. Reflects the last lookup, not a permanent verdict — moving to a
     * network that permits DoH clears it without a restart.
     */
    val usingSystemFallback: Flow<Boolean>
}
