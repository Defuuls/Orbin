package com.orbin.ios

internal fun createDeviceBackup(
    graph: AppGraph,
    lock: AppLock,
    media: IosManagedMediaFiles,
): IosBackup =
    IosBackup(
        settings = graph.settingsStore,
        boardPreferences = graph.boardPreferences,
        bookmarks = graph.bookmarks,
        providers = graph.providers.map { it.metadata.id },
        files = DeviceBackupFiles(),
        scope = graph.scope,
        appVersion = graph.appVersion,
        isAuthorized = { !lock.state.value.locked && !lock.state.value.obscured },
        isOwnMediaUrl = { id, url ->
            graph.providers
                .firstOrNull { it.metadata.id == id }
                ?.metadata
                ?.isOwnMediaUrl(url) == true
        },
        history = graph.history,
        threadMemory = graph.threadMemory,
        mediaLibrary = graph.mediaLibrary,
        managedMediaFiles = media,
        temporaryDirectory = platform.Foundation.NSTemporaryDirectory() + "OrbinBackup",
    )

internal fun createDeviceCloudSync(
    graph: AppGraph,
    lock: AppLock,
    backup: IosBackup,
    media: IosManagedMediaFiles,
): CloudSyncController {
    val repositories =
        CloudRepositories(
            graph.settingsStore,
            graph.boardPreferences,
            graph.bookmarks,
            graph.history,
            graph.threadMemory,
            graph.mediaLibrary,
        )
    val sync =
        deviceCloudSync(
            local =
                PortableCloudStore(
                    backup = backup,
                    repositories = repositories,
                    files = media,
                    assets = DeviceCloudAssetFiles(),
                    revisions = DeviceCloudRevisionStore(),
                    providers = graph.providers.map { it.metadata },
                    isAuthorized = { !lock.state.value.locked && !lock.state.value.obscured },
                ),
            scope = graph.scope,
            isAuthorized = { !lock.state.value.locked && !lock.state.value.obscured },
        )
    observeCloudChanges(sync, graph.scope, repositories, graph.providers.map { it.metadata.id })
    return sync
}
