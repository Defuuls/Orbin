package com.orbin.data.di

import com.orbin.data.notification.AndroidThreadNotifier
import com.orbin.data.provider.ProviderRegistryImpl
import com.orbin.data.repository.BoardRepositoryImpl
import com.orbin.data.repository.BookmarkRepositoryImpl
import com.orbin.data.repository.CatalogRepositoryImpl
import com.orbin.data.repository.DownloadRepositoryImpl
import com.orbin.data.repository.HistoryRepositoryImpl
import com.orbin.data.repository.SearchRepositoryImpl
import com.orbin.data.repository.ThreadRepositoryImpl
import com.orbin.data.settings.SettingsRepositoryImpl
import com.orbin.domain.notification.ThreadNotifier
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.CatalogRepository
import com.orbin.domain.repository.DownloadRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.SearchRepository
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
    fun bindsBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository

    @Binds
    @Singleton
    fun bindsHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository

    @Binds
    @Singleton
    fun bindsSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    fun bindsDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    @Singleton
    fun bindsThreadNotifier(impl: AndroidThreadNotifier): ThreadNotifier

    @Binds
    @Singleton
    fun bindsSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    fun bindsNetworkConfigProvider(impl: SettingsRepositoryImpl): NetworkConfigProvider
}
