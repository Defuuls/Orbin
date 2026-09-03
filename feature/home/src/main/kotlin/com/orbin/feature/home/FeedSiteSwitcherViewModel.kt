package com.orbin.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.ProviderId
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class FeedSiteOption(
    val id: String,
    val label: String,
)

@HiltViewModel
internal class FeedSiteSwitcherViewModel
    @Inject
    constructor(
        registry: ProviderRegistry,
        observeActiveProvider: ObserveActiveProviderUseCase,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        val sites: ImmutableList<FeedSiteOption> =
            registry
                .all()
                .map { provider ->
                    FeedSiteOption(
                        id = provider.metadata.id.value,
                        label = provider.metadata.displayName,
                    )
                }
                .toImmutableList()

        private val defaultProviderId = registry.default().metadata.id.value

        val activeProviderId: StateFlow<String> =
            observeActiveProvider()
                .map { provider -> provider.metadata.id.value }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), defaultProviderId)

        fun selectSite(id: String) {
            if (id == activeProviderId.value) return
            viewModelScope.launch { settingsRepository.setActiveProviderId(ProviderId(id)) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
