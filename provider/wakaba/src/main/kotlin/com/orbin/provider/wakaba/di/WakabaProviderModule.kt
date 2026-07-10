package com.orbin.provider.wakaba.di

import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.network.di.BaseOkHttp
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.wakaba.WakabaProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WakabaProviderModule {
    @Provides @IntoSet @Singleton
    fun providesTranchanProvider(
        @BaseOkHttp client: OkHttpClient,
        @Dispatcher(OrbinDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): ImageBoardProvider = WakabaProvider(client, ioDispatcher)
}
