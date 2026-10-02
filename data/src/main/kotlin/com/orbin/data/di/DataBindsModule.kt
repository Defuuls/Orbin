package com.orbin.data.di

import com.orbin.data.notification.AndroidThreadNotifier
import com.orbin.data.settings.FixedNetworkConfigProvider
import com.orbin.domain.notification.ThreadNotifier
import com.orbin.network.NetworkConfigProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataBindsModule {
    @Binds
    @Singleton
    fun bindsThreadNotifier(impl: AndroidThreadNotifier): ThreadNotifier

    @Binds
    @Singleton
    fun bindsNetworkConfigProvider(impl: FixedNetworkConfigProvider): NetworkConfigProvider
}
