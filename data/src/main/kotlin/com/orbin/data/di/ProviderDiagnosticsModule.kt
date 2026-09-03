package com.orbin.data.di

import com.orbin.provider.api.InMemoryProviderDiagnostics
import com.orbin.provider.api.ProviderDiagnostics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProviderDiagnosticsModule {
    @Provides
    @Singleton
    fun providesProviderDiagnostics(): ProviderDiagnostics = InMemoryProviderDiagnostics()
}
