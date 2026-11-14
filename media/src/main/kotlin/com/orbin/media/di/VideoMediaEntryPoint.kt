package com.orbin.media.di

import com.orbin.media.video.VideoServer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VideoMediaEntryPoint {
    fun videoServer(): VideoServer
}
