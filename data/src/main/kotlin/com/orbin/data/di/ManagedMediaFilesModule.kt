package com.orbin.data.di

import com.orbin.data.media.AndroidManagedMediaFiles
import com.orbin.domain.repository.ManagedMediaFiles
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ManagedMediaFilesModule {
    @Binds
    @Singleton
    abstract fun managedMediaFiles(implementation: AndroidManagedMediaFiles): ManagedMediaFiles
}
