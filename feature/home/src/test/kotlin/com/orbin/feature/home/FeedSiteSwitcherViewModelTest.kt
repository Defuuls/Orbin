package com.orbin.feature.home

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.orbin.core.testing.repository.FakeProviderRegistry
import org.junit.Test

class FeedSiteSwitcherViewModelTest {
    @Test
    fun `Home defaults to all sites and retains its source filter in saved state`() {
        val state = SavedStateHandle()
        val model = FeedSiteSwitcherViewModel(FakeProviderRegistry(), state)
        assertThat(model.activeProviderId.value).isNull()

        val source = model.sites.single().id
        model.selectSite(source)
        val restored = FeedSiteSwitcherViewModel(FakeProviderRegistry(), state)
        assertThat(restored.activeProviderId.value).isEqualTo(source)

        restored.selectSite(null)
        assertThat(restored.activeProviderId.value).isNull()
    }

    @Test
    fun `unregistered site filters are ignored`() {
        val model = FeedSiteSwitcherViewModel(FakeProviderRegistry(), SavedStateHandle())
        model.selectSite("missing")
        assertThat(model.activeProviderId.value).isNull()
    }
}
