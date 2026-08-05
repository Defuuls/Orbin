package com.orbin.feature.settings

import com.orbin.core.model.AppSettings
import com.orbin.core.model.BackupBoardRef
import com.orbin.core.model.BackupDocument
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.provider.api.ProviderRegistry
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Builds and restores [BackupDocument]s. Serialization only — callers own the file IO. */
class BackupService
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val boardPreferences: BoardPreferencesRepository,
        private val registry: ProviderRegistry,
    ) {
        suspend fun exportToJson(appVersionName: String): String {
            val providers = registry.all().map { it.metadata.id }
            val document =
                BackupDocument(
                    exportedAt = timestamp(),
                    exportedByAppVersion = appVersionName,
                    settings = settingsRepository.settings.first(),
                    subscribedBoards = providers.flatMap { it.refs(subscribed = true) },
                    favoriteBoards = providers.flatMap { it.refs(subscribed = false) },
                )
            return json.encodeToString(BackupDocument.serializer(), document)
        }

        /**
         * Restores [backupJson]. Boards are added, never removed, so importing merges into whatever
         * is already subscribed rather than destroying it.
         */
        suspend fun importFromJson(backupJson: String): Result<BackupSummary> =
            runCatching {
                val document = json.decodeFromString(BackupDocument.serializer(), backupJson)
                require(document.formatVersion <= BackupDocument.CURRENT_FORMAT_VERSION) {
                    "This backup was written by a newer version of Orbin " +
                        "(format ${document.formatVersion})."
                }

                restoreSettings(document.settings)
                document.subscribedBoards.forEach { ref ->
                    boardPreferences.setSubscribedBoard(ProviderId(ref.providerId), BoardId(ref.boardId), true)
                }
                document.favoriteBoards.forEach { ref ->
                    boardPreferences.setFavoriteBoard(ProviderId(ref.providerId), BoardId(ref.boardId), true)
                }

                BackupSummary(
                    exportedAt = document.exportedAt,
                    subscribedBoards = document.subscribedBoards.size,
                    favoriteBoards = document.favoriteBoards.size,
                )
            }

        private suspend fun ProviderId.refs(subscribed: Boolean): List<BackupBoardRef> {
            val boards =
                if (subscribed) {
                    boardPreferences.observeSubscribedBoards(this).first()
                } else {
                    boardPreferences.observeFavoriteBoards(this).first()
                }
            return boards.map { BackupBoardRef(providerId = value, boardId = it.value) }
        }

        @Suppress("CyclomaticComplexMethod", "LongMethod")
        private suspend fun restoreSettings(settings: AppSettings) =
            with(settingsRepository) {
                setPersonalizedHomeFeed(settings.personalizedHomeFeed)
                setHiddenTags(settings.hiddenTags)
                setMutedTags(settings.mutedTags)
                setHideNsfwBoards(settings.hideNsfwBoards)
                setHideTextOnlyThreads(settings.hideTextOnlyThreads)
                setRefreshFeedOnReturn(settings.refreshFeedOnReturn)
                setThemeMode(settings.themeMode)
                setColorTheme(settings.colorTheme)
                setDynamicColor(settings.dynamicColor)
                setAmoled(settings.amoled)
                setFontScale(settings.fontScale)
                setAppIconVariant(settings.appIconVariant)
                setFullScreenFeedChrome(settings.fullScreenFeedChrome)
                setThumbnailSize(settings.thumbnailSize)
                setAutoplayVideos(settings.autoplayVideos)
                setMuteByDefault(settings.muteByDefault)
                setFullscreenVideoPlayback(settings.fullscreenVideoPlayback)
                setAutoRotateVideoFullscreen(settings.autoRotateVideoFullscreen)
                setPreloadImages(settings.preloadImages)
                setPreloadOption(settings.preloadOption)
                setPreloadThrottleMode(settings.preloadThrottleMode)
                setFeedThreadLimit(settings.feedThreadLimit)
                setUserAgent(settings.userAgent)
                setDohEnabled(settings.dohEnabled)
                setDohProvider(settings.dohProvider)
                setBiometricLockEnabled(settings.biometricLockEnabled)
                setSaveRecentSearches(settings.saveRecentSearches)
                setInternalUpdaterEnabled(settings.internalUpdaterEnabled)
                setThreadWatchNotificationsEnabled(settings.threadWatchNotificationsEnabled)
                setQuietHoursStart(settings.quietHoursStart)
                setQuietHoursEnd(settings.quietHoursEnd)
                setMediaScrollThreadView(settings.mediaScrollThreadView)
                setMediaScrollBoardView(settings.mediaScrollBoardView)
                if (settings.activeProviderId.isNotBlank()) {
                    setActiveProviderId(ProviderId(settings.activeProviderId))
                }
                // Skip onboarding for a restored install — the user has already been through it.
                setOnboardingCompleted(settings.onboardingCompleted)
                // downloadFolderUri is deliberately not restored: a SAF permission grant belongs to
                // the install that requested it, so the path would be unreadable after a reinstall.
                // The user re-picks the folder, which re-grants access.
            }

        private fun timestamp(): String =
            java.time.Instant
                .now()
                .toString()

        private companion object {
            val json =
                Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
        }
    }

/** What an import actually restored, for the confirmation shown to the user. */
data class BackupSummary(
    val exportedAt: String,
    val subscribedBoards: Int,
    val favoriteBoards: Int,
)
