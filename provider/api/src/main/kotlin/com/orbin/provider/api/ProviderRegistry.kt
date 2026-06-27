package com.orbin.provider.api

import com.orbin.core.model.ProviderId

/**
 * Lookup for the set of providers available at runtime. Implemented in the data layer and
 * populated via DI (Hilt multibinding), so adding a provider module is wiring-only.
 */
interface ProviderRegistry {
    /** All registered providers, in display order. */
    fun all(): List<ImageBoardProvider>

    /** The provider for [id], or null if none is registered. */
    fun get(id: ProviderId): ImageBoardProvider?

    /** The provider selected as the app default (first registered unless overridden). */
    fun default(): ImageBoardProvider
}

/** Convenience that throws a clear error instead of returning null for a required provider. */
fun ProviderRegistry.require(id: ProviderId): ImageBoardProvider =
    get(id) ?: error("No provider registered for id '${id.value}'")
