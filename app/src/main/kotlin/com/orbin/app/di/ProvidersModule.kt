package com.orbin.app.di

import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.lynxchan.LynxChanProvider
import com.orbin.provider.lynxchan.LynxChanSite
import com.orbin.provider.lynxchan.api.KtorLynxChanApi
import com.orbin.provider.vichan.VichanProvider
import com.orbin.provider.vichan.VichanSite
import com.orbin.provider.vichan.api.KtorVichanApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/**
 * Registers the image-board providers into the app-wide `Set<ImageBoardProvider>`.
 *
 * The provider modules are shared with iOS and carry no DI annotations, so the Android graph
 * builds them here. To add a site, contribute another `@IntoSet` provider for a different
 * [VichanSite] or [LynxChanSite] — no other module changes.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProvidersModule {
    @Provides
    @IntoSet
    @Singleton
    fun providesExampleVichanProvider(
        client: HttpClient,
        json: Json,
        @Dispatcher(OrbinDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): ImageBoardProvider {
        val site = VichanSite.Example
        return VichanProvider(site, KtorVichanApi(client, site.apiBaseUrl, json), ioDispatcher)
    }

    @Provides
    @IntoSet
    @Singleton
    fun providesBbwChanProvider(
        client: HttpClient,
        json: Json,
        @Dispatcher(OrbinDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): ImageBoardProvider {
        val site = LynxChanSite.BbwChan
        return LynxChanProvider(site, KtorLynxChanApi(client, site.apiBaseUrl, json), ioDispatcher)
    }
}
