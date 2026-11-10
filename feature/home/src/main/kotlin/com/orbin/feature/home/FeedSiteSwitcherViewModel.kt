package com.orbin.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

internal data class FeedSiteOption(
    val id: String,
    val label: String,
)

/** Filtering Home never changes the independently selected site in the Boards directory. */
@HiltViewModel
internal class FeedSiteSwitcherViewModel
    @Inject
    constructor(
        registry: ProviderRegistry,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val sites: ImmutableList<FeedSiteOption> =
            registry
                .all()
                .map { provider ->
                    FeedSiteOption(
                        id = provider.metadata.id.value,
                        label = provider.metadata.displayName,
                    )
                }.toImmutableList()

        /** Null selects the combined feed; the selected source survives process recreation. */
        val activeProviderId: StateFlow<String?> = savedStateHandle.getStateFlow(SITE_FILTER_KEY, null)

        fun selectSite(id: String?) {
            if (id != null && sites.none { it.id == id }) return
            savedStateHandle[SITE_FILTER_KEY] = id
        }

        private companion object {
            const val SITE_FILTER_KEY = "homeSiteFilter"
        }
    }
