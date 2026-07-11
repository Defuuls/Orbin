package com.orbin.provider.lynxchan.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.network.di.BaseOkHttp
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.lynxchan.LynxChanProvider
import com.orbin.provider.lynxchan.LynxChanSite
import com.orbin.provider.lynxchan.api.LynxChanApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Registers the bundled LynxChan instances into the app-wide `Set<ImageBoardProvider>` via Hilt
 * multibinding. To add another LynxChan site, contribute another `@IntoSet` provider for a
 * different [LynxChanSite] - no other module changes.
 */
@Module
@InstallIn(SingletonComponent::class)
object LynxChanProviderModule {
    @Provides
    @IntoSet
    @Singleton
    fun providesBbwChanProvider(
        @BaseOkHttp client: OkHttpClient,
        json: Json,
        @Dispatcher(OrbinDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): ImageBoardProvider = buildProvider(LynxChanSite.BbwChan, client, json, ioDispatcher)

    @Provides
    @IntoSet
    @Singleton
    fun providesEightChanProvider(
        @BaseOkHttp client: OkHttpClient,
        json: Json,
        @Dispatcher(OrbinDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): ImageBoardProvider = buildProvider(LynxChanSite.EightChan, client, json, ioDispatcher)

    private fun buildProvider(
        site: LynxChanSite,
        client: OkHttpClient,
        json: Json,
        ioDispatcher: CoroutineDispatcher,
    ): ImageBoardProvider {
        val retrofit =
            Retrofit
                .Builder()
                .baseUrl(site.apiBaseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
                .build()
        return LynxChanProvider(
            site = site,
            api = retrofit.create(LynxChanApi::class.java),
            ioDispatcher = ioDispatcher,
        )
    }

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
}
