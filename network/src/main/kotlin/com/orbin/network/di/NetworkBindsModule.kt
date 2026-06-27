package com.orbin.network.di

import com.orbin.core.common.network.NetworkMonitor
import com.orbin.network.ConnectivityNetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds network-layer implementations to their core contracts. */
@Module
@InstallIn(SingletonComponent::class)
interface NetworkBindsModule {

    @Binds
    @Singleton
    fun bindsNetworkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor
}
