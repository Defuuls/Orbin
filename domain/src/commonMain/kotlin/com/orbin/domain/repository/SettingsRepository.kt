package com.orbin.domain.repository

import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.ProviderId
import kotlinx.coroutines.flow.Flow

/** Reads and updates persisted [AppSettings]. Implemented in :data over DataStore. */
@Suppress("TooManyFunctions")
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setHideNsfwBoards(enabled: Boolean)

    suspend fun setDeepMediaScan(enabled: Boolean)

    suspend fun setThemeMode(mode: AppThemeMode)

    suspend fun setAmoled(enabled: Boolean)

    suspend fun setBiometricLockEnabled(enabled: Boolean)

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setActiveProviderId(id: ProviderId)
}
