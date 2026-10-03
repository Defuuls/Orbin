package com.orbin.data.di

import com.orbin.data.diagnostics.DiagnosticsRepositoryImpl
import com.orbin.data.notification.AndroidThreadNotifier
import com.orbin.data.repository.AndroidDownloadQueue
import com.orbin.data.repository.DownloadPlatformQueue
import com.orbin.data.update.AppUpdaterImpl
import com.orbin.data.version.VersionGuardRepositoryImpl
import com.orbin.domain.notification.ThreadNotifier
import com.orbin.domain.repository.AppUpdater
import com.orbin.domain.repository.DiagnosticsRepository
import com.orbin.domain.repository.VersionGuardRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds platform-specific data-layer implementations to their contracts. */
@Module
@InstallIn(SingletonComponent::class)
interface DataBindsModule {
    @Binds
    @Singleton
    fun bindsDiagnosticsRepository(impl: DiagnosticsRepositoryImpl): DiagnosticsRepository

    @Binds
    @Singleton
    fun bindsAppUpdater(impl: AppUpdaterImpl): AppUpdater

    @Binds
    @Singleton
    fun bindsThreadNotifier(impl: AndroidThreadNotifier): ThreadNotifier

    @Binds
    @Singleton
    fun bindsVersionGuardRepository(impl: VersionGuardRepositoryImpl): VersionGuardRepository

    @Binds
    @Singleton
    fun bindsDownloadPlatformQueue(impl: AndroidDownloadQueue): DownloadPlatformQueue
}
