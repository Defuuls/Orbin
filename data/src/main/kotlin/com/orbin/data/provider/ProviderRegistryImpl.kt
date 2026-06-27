package com.orbin.data.provider

import com.orbin.core.model.ProviderId
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ProviderRegistry] backed by the set of providers contributed via Hilt multibinding. Provider
 * modules register themselves with `@IntoSet`, so this implementation needs no changes when an
 * engine is added.
 */
@Singleton
class ProviderRegistryImpl
    @Inject
    constructor(
        private val providers: Set<@JvmSuppressWildcards ImageBoardProvider>,
    ) : ProviderRegistry {
        private val byId: Map<ProviderId, ImageBoardProvider> =
            providers.associateBy { it.metadata.id }

        override fun all(): List<ImageBoardProvider> = providers.sortedBy { it.metadata.displayName }

        override fun get(id: ProviderId): ImageBoardProvider? = byId[id]

        override fun default(): ImageBoardProvider =
            all().firstOrNull() ?: error("No image board providers are registered")
    }
