package com.orbin.data.di

import com.orbin.data.provider.ProviderRegistryImpl
import com.orbin.data.repository.BoardRepositoryImpl
import com.orbin.data.repository.CatalogRepositoryImpl
import com.orbin.data.repository.ThreadRepositoryImpl
import com.orbin.data.settings.SettingsRepositoryImpl
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.CatalogRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.repository.ThreadRepository
import com.orbin.network.NetworkConfigProvider
import com.orbin.provider.api.ProviderRegistry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds data-layer implementations to the domain and infrastructure contracts they satisfy. */
@Module
@InstallIn(SingletonComponent::class)
interface DataBindsModule {
    @Binds
    @Singleton
    fun bindsProviderRegistry(impl: ProviderRegistryImpl): ProviderRegistry

    @Binds
    @Singleton
    fun bindsBoardRepository(impl: BoardRepositoryImpl): BoardRepository

    @Binds
    @Singleton
    fun bindsCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository

    @Binds
    @Singleton
    fun bindsThreadRepository(impl: ThreadRepositoryImpl): ThreadRepository

    @Binds
    @Singleton
    fun bindsSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    fun bindsNetworkConfigProvider(impl: SettingsRepositoryImpl): NetworkConfigProvider
}
