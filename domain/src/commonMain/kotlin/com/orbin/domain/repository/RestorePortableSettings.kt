package com.orbin.domain.repository

import com.orbin.core.model.AppSettings
import com.orbin.core.model.FormFactor
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.ProviderId
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

/** Portable data cannot change local privacy, DNS security, the app lock, private storage or setup. */
suspend fun SettingsRepository.restorePortableSettings(
    restored: AppSettings,
    beforeEach: suspend () -> Unit = { currentCoroutineContext().ensureActive() },
    preserveLocalPrivacy: Boolean = false,
) {
    val oldOverrides = settings.first().playbackPreferences.overrides
    for (entry in oldOverrides) {
        beforeEach()
        setPlaybackSettings(entry.scope, null)
    }
    beforeEach()
    setPlaybackSettings(PlaybackScope(), restored.playbackPreferences.defaults)
    for (entry in restored.playbackPreferences.overrides) {
        beforeEach()
        setPlaybackSettings(entry.scope, entry.settings)
    }
    beforeEach()
    setAccentTheme(restored.accentTheme)
    beforeEach()
    setThreadLayout(restored.threadLayout)
    beforeEach()
    setAutoScrollToLastSeen(restored.autoScrollToLastSeen)
    beforeEach()
    setShowStickyThreads(restored.showStickyThreads)
    beforeEach()
    setCollectionNewestFirst(restored.collectionNewestFirst)
    beforeEach()
    setShowSavedMediaTab(restored.showSavedMediaTab)
    beforeEach()
    setFullscreenAutoAdvance(restored.fullscreenAutoAdvance)
    beforeEach()
    setHiddenThreadPatterns(restored.hiddenThreadPatterns)
    beforeEach()
    setPostFilterRules(restored.postFilterRules)
    if (!preserveLocalPrivacy) {
        beforeEach()
        setHideNsfwBoards(restored.hideNsfwBoards)
        beforeEach()
        setCoverViolentMedia(restored.coverViolentMedia)
        beforeEach()
        setDeepMediaScan(restored.deepMediaScan)
    }
    beforeEach()
    setOpenThreadComments(restored.openThreadComments)
    beforeEach()
    setThemeMode(restored.themeMode)
    beforeEach()
    setAmoled(restored.amoled)
    beforeEach()
    setCatalogMode(restored.catalogMode)
    beforeEach()
    setVideoCacheMegabytes(restored.videoCacheMegabytes)
    beforeEach()
    setFeedColumns(FormFactor.FOLDABLE, restored.unfoldedFeedColumns)
    beforeEach()
    setFeedColumns(FormFactor.TABLET, restored.tabletFeedColumns)
    if (restored.activeProviderId.isNotBlank()) {
        beforeEach()
        setActiveProviderId(ProviderId(restored.activeProviderId))
    }
}
