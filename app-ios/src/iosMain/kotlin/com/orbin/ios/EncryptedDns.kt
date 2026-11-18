package com.orbin.ios

import com.orbin.core.model.DohProvider

/**
 * Applies the saved DNS-over-HTTPS provider to the process-wide Network.framework privacy
 * context. Swift registers the applicator with [setApplicator] and calls [applyDefault] before
 * [AppGraph] is touched, so the Cloudflare resolver is in place for the first request. Settings
 * then update both this and the Ktor [com.orbin.data.settings.FixedNetworkConfigProvider].
 *
 * Encrypted resolution is always required here, whatever "Strict encrypted DNS" says. The system
 * API has no "try encrypted first, then fall back" mode: asking for encryption without requiring
 * it means the device's ordinary resolver is used and the chosen DoH resolver is never consulted.
 * Passing the strict flag through, as this once did, therefore left a default install resolving
 * every site in the clear. Requiring it is the only setting under which the app's own requests
 * are looked up over DoH at all; the cost is that a network which blocks the resolver stops the
 * app loading rather than being quietly allowed to see the lookups.
 *
 * On iOS the strict setting instead governs what the privacy context cannot reach: see
 * [SystemMediaPolicy].
 */
fun interface EncryptedDnsApplicator {
    fun apply(
        resolverUrl: String,
        bootstrapIps: List<String>,
        required: Boolean,
    )
}

object EncryptedDns {
    var applicator: EncryptedDnsApplicator? = null

    /**
     * Swift calls this with a three-argument block (URL, comma-separated bootstrap IPs, and
     * "true"/"false") so List and Boolean interop stay on the Kotlin side.
     */
    fun setApplicator(block: (resolverUrl: String, bootstrapIpsCsv: String, requiredFlag: String) -> Unit) {
        applicator =
            EncryptedDnsApplicator { url, ips, required ->
                block(url, ips.joinToString(","), if (required) "true" else "false")
            }
    }

    /** The shipped default resolver, Cloudflare, before settings have loaded. */
    fun applyDefault() {
        apply(DohProvider.CLOUDFLARE)
    }

    fun apply(provider: DohProvider) {
        val request = iosEncryptedDnsRequest(provider)
        applicator?.apply(
            resolverUrl = request.resolverUrl,
            bootstrapIps = request.bootstrapIps,
            required = request.required,
        )
    }
}
