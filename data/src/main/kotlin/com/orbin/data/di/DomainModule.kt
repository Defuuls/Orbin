package com.orbin.data.di

import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.repository.ThreadRepository
import com.orbin.domain.usecase.BuildReplyGraphUseCase
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.domain.usecase.ObserveBookmarksUseCase
import com.orbin.domain.usecase.ObserveThreadUseCase
import com.orbin.domain.usecase.SetThreadWatchedUseCase
import com.orbin.domain.usecase.ToggleBookmarkUseCase
import com.orbin.provider.api.ProviderRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt bindings for the domain use cases.
 *
 * `:domain` is shared with iOS and so carries no DI annotations; the Android graph builds its use
 * cases here instead of through `@Inject` constructors. Each is stateless, so none is scoped.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    fun providesObserveBookmarks(repository: BookmarkRepository) = ObserveBookmarksUseCase(repository)

    @Provides
    fun providesToggleBookmark(repository: BookmarkRepository) = ToggleBookmarkUseCase(repository)

    @Provides
    fun providesSetThreadWatched(repository: BookmarkRepository) = SetThreadWatchedUseCase(repository)

    @Provides
    fun providesObserveActiveProvider(
        registry: ProviderRegistry,
        settingsRepository: SettingsRepository,
    ) = ObserveActiveProviderUseCase(registry, settingsRepository)

    @Provides
    fun providesObserveThread(threadRepository: ThreadRepository) = ObserveThreadUseCase(threadRepository)

    @Provides
    fun providesBuildReplyGraph() = BuildReplyGraphUseCase()
}
