package com.orbin.provider.vichan.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.network.di.BaseOkHttp
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.vichan.VichanProvider
import com.orbin.provider.vichan.VichanSite
import com.orbin.provider.vichan.api.VichanApi
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
 * Registers the example vichan provider into the app-wide `Set<ImageBoardProvider>` via Hilt
 * multibinding. To add another instance, contribute another `@IntoSet` provider for a different
 * [VichanSite] — no other module changes.
 */
@Module
@InstallIn(SingletonComponent::class)
object VichanProviderModule {

    @Provides
    @IntoSet
    @Singleton
    fun providesExampleVichanProvider(
        @BaseOkHttp client: OkHttpClient,
        json: Json,
        @Dispatcher(OrbinDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): ImageBoardProvider {
        val site = VichanSite.Example
        val retrofit = Retrofit.Builder()
            .baseUrl(site.apiBaseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()
        return VichanProvider(
            site = site,
            api = retrofit.create(VichanApi::class.java),
            ioDispatcher = ioDispatcher,
        )
    }

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
}
