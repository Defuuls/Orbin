package com.orbin.data.provider

import com.orbin.core.model.ProviderId
import com.orbin.domain.repository.SettingsRepository
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.InstrumentedImageBoardProvider
import com.orbin.provider.api.ProviderDiagnostics
import com.orbin.provider.api.ProviderRegistry
import com.orbin.provider.api.ViolentMediaCoverProvider
import kotlinx.coroutines.flow.first

/**
 * [ProviderRegistry] backed by a set of providers.
 *
 * Every provider is wrapped once at this seam with contract validation and privacy-safe timing
 * diagnostics, then with the violent-media cover the reader can turn off in Settings. Features
 * and repositories therefore get the same behavior without engine-specific
 * instrumentation code.
 */
class ProviderRegistryImpl(
    providers: Set<ImageBoardProvider>,
    diagnostics: ProviderDiagnostics,
    settingsRepository: SettingsRepository,
) : ProviderRegistry {
    private val providers: List<ImageBoardProvider> =
        providers
            .map { InstrumentedImageBoardProvider(it, diagnostics) }
            .map { provider ->
                ViolentMediaCoverProvider(provider) { settingsRepository.settings.first().coverViolentMedia }
            }.sortedBy { it.metadata.displayName }

    private val byId: Map<ProviderId, ImageBoardProvider> =
        this.providers.associateBy { it.metadata.id }

    override fun all(): List<ImageBoardProvider> = providers

    override fun get(id: ProviderId): ImageBoardProvider? = byId[id]

    override fun default(): ImageBoardProvider =
        providers.firstOrNull() ?: error("No image board providers are registered")
}
