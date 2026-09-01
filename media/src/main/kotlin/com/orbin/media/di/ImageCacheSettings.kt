package com.orbin.media.di

import com.orbin.core.common.dispatchers.ApplicationScope
import com.orbin.core.model.AppSettings
import com.orbin.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-scoped non-blocking view of the setting needed while Coil builds its disk cache.
 *
 * The old provider bridged the settings Flow with runBlocking. Keeping an eagerly collected state
 * snapshot preserves the configured limit while making image-loader construction synchronous and
 * non-blocking. The AppSettings default covers the brief startup window before DataStore emits.
 */
@Singleton
class ImageCacheSettings
    @Inject
    constructor(
        settingsRepository: SettingsRepository,
        @ApplicationScope scope: CoroutineScope,
    ) {
        private val settings =
            settingsRepository.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings.Default)

        val limitMb: Long
            get() = settings.value.imageCacheLimitMb.toLong()
    }
