package com.orbin.domain.usecase

import com.orbin.core.model.ProviderId
import com.orbin.domain.repository.SettingsRepository
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Streams the user's chosen active provider, following [SettingsRepository]'s persisted
 * `activeProviderId` and falling back to [ProviderRegistry.default] when unset or when the saved
 * id no longer matches a registered provider (e.g. after an app update removes one).
 */
class ObserveActiveProviderUseCase
    @Inject
    constructor(
        private val registry: ProviderRegistry,
        private val settingsRepository: SettingsRepository,
    ) {
        operator fun invoke(): Flow<ImageBoardProvider> =
            settingsRepository.settings
                .map { it.activeProviderId }
                .distinctUntilChanged()
                .map { id -> registry.get(ProviderId(id)) ?: registry.default() }
    }
