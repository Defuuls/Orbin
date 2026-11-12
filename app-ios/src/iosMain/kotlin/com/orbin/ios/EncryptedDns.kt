package com.orbin.ios

import com.orbin.core.model.DohProvider
import com.orbin.data.settings.encryptedDnsRequest

/**
 * Applies the saved DNS-over-HTTPS provider and "strict encrypted DNS" flag to the process-wide
 * Network.framework privacy context. Swift registers the applicator with [setApplicator] and
 * calls [applyDefault] before [AppGraph] is touched, so a Cloudflare resolver with fallback
 * allowed is in place for the first request. Settings then update both this and the Ktor
 * [com.orbin.data.settings.FixedNetworkConfigProvider].
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

    /** Android's shipped default: Cloudflare, system-DNS fallback allowed. */
    fun applyDefault() {
        apply(DohProvider.CLOUDFLARE, strict = false)
    }

    fun apply(
        provider: DohProvider,
        strict: Boolean,
    ) {
        val request = encryptedDnsRequest(provider, strict)
        applicator?.apply(
            resolverUrl = request.resolverUrl,
            bootstrapIps = request.bootstrapIps,
            required = request.required,
        )
    }
}
