package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.BackupBoardRef
import com.orbin.core.model.BackupBookmark
import com.orbin.core.model.BackupDocument
import com.orbin.core.model.BackupHistoryEntry
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.MediaLibraryRecord
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadMemory
import com.orbin.core.model.portableExport
import com.orbin.core.model.toHistoryEntry
import com.orbin.domain.repository.ManagedMediaFiles
import com.orbin.domain.repository.restorePortableSettings
import com.orbin.domain.repository.validManagedMediaKey
import com.orbin.provider.api.ProviderMetadata
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.Clock

@Serializable
data class CloudRevisionState(
    val origin: String,
    val clock: Long = 0,
    val items: List<CloudSyncItem> = emptyList(),
    val deferredIds: Set<String> = emptySet(),
    val pendingFileDeletes: Set<String> = emptySet(),
)

/** This state belongs to one installation; a backup must not clone its identity or opt-in. */
interface CloudRevisionStore {
    suspend fun read(): CloudRevisionState

    suspend fun write(state: CloudRevisionState)
}

interface CloudAssetFiles {
    suspend fun localPath(storageKey: String): String?

    /** Only files staged by the native CloudKit inbox can be accepted as incoming assets. */
    suspend fun validateIncoming(
        path: String,
        expectedBytes: Long? = null,
    )

    suspend fun discardIncoming(path: String)
}

/** Real repository adapter: every collection record merges independently, including deletions. */
class PortableCloudStore(
    private val backup: IosBackup,
    repositories: CloudRepositories,
    private val files: ManagedMediaFiles,
    private val assets: CloudAssetFiles,
    private val revisions: CloudRevisionStore,
    providers: List<ProviderMetadata>,
    private val isAuthorized: () -> Boolean,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : CloudSyncLocalStore {
    private val settings = repositories.settings
    private val boardPreferences = repositories.boardPreferences
    private val bookmarks = repositories.bookmarks
    private val history = repositories.history
    private val memory = repositories.memory
    private val library = repositories.media
    private val providers = providers.associateBy { it.id.value }
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun snapshot(): List<CloudSyncItem> = mutex.withLock { capture() }

    private suspend fun capture(clockFloor: Long = 0): List<CloudSyncItem> {
        authorize()
        drainFileCleanup()
        val document = backup.exportDocument()
        val options = settings.settings.first()
        val previous = revisions.read()
        val snapshot = CloudSnapshotBuilder(previous, maxOf(now(), clockFloor))
        captureRecords(document, options, snapshot)
        val ids = snapshot.items.mapTo(mutableSetOf()) { it.id }
        val withheldLedger = mutableListOf<CloudSyncItem>()
        val deferred = (previous.deferredIds - ids).toMutableSet()
        for (old in previous.items) {
            if (old.id in ids) continue
            val unavailable = isUnavailable(old)
            if (options.historySessionOnly && old.id.startsWith("history/") && !unavailable) {
                val history = json.decodeFromString<BackupHistoryEntry>(old.payload)
                snapshot.delete(old, json.encodeToString(BackupHistoryEntry(key = history.key)))
            } else if (shouldWithhold(old, options, deferred, unavailable)) {
                withheldLedger += old.withoutLocalPaths()
                if (unavailable) deferred += old.id
            } else {
                snapshot.delete(old)
            }
        }
        authorize()
        if (settings.settings.first().activityPolicy() != options.activityPolicy() ||
            document.settings.activityPolicy() != options.activityPolicy()
        ) {
            return capture(clockFloor)
        }
        writeLedger(
            previous.copy(
                clock = snapshot.clock,
                items = snapshot.items.map { it.withoutLocalPaths() } + withheldLedger,
                deferredIds = deferred,
            ),
        )
        return snapshot.items
    }

    private fun shouldWithhold(
        item: CloudSyncItem,
        options: AppSettings,
        deferred: Set<String>,
        unavailable: Boolean,
    ): Boolean {
        val privateActivity = options.historySessionOnly && item.isActivity()
        return privateActivity || item.id in deferred || unavailable
    }

    private suspend fun captureRecords(
        document: BackupDocument,
        options: AppSettings,
        snapshot: CloudSnapshotBuilder,
    ) {
        snapshot.record("preferences", "portable", json.encodeToString(document.settings.forCloud()))
        document.subscribedBoards.forEach { snapshot.record("follow", boardKey(it), json.encodeToString(it)) }
        document.favoriteBoards.forEach { snapshot.record("favorite", boardKey(it), json.encodeToString(it)) }
        document.bookmarks.filter { it.providerId in providers }.forEach {
            snapshot.record("bookmark", "${it.providerId}/${it.boardId}/${it.threadId}", json.encodeToString(it))
        }
        if (!options.historySessionOnly) {
            document.history.filter { it.key.provider.value in providers }.forEach {
                snapshot.record("history", threadKey(it.key), json.encodeToString(it))
            }
            document.threadMemory.filter { it.key.provider.value in providers }.forEach {
                snapshot.record("memory", threadKey(it.key), json.encodeToString(it))
            }
        }
        for (media in document.savedMedia.filter { it.providerId in providers }) {
            val path = assets.localPath(media.storageKey)
            val thumbnail = media.thumbnailStorageKey?.let { assets.localPath(it) }
            val canonical =
                media.copy(
                    storageKey = cloudMediaKey(media.id),
                    thumbnailStorageKey = media.thumbnailStorageKey?.let { cloudThumbnailKey(media.id) },
                )
            snapshot.record("media", "${media.providerId}/${media.id}", json.encodeToString(canonical), path, thumbnail)
        }
    }

    override suspend fun apply(items: List<CloudSyncItem>) {
        mutex.withLock {
            items.forEach(::validateEnvelope)
            val options = settings.settings.first()
            val incoming = items.filterNot { options.historySessionOnly && it.isActivity() }
            val unavailable = incoming.filter(::isUnavailable)
            val validated = incoming.filterNot(::isUnavailable).map { validate(it) }
            // A local edit made during fetch must compete as a new revision, rather than being
            // silently overwritten by the response to an older snapshot.
            capture(items.maxOfOrNull { it.revision } ?: 0)
            val previous = revisions.read()
            val known = previous.items.associateBy { it.id }.toMutableMap()
            var clock = previous.clock
            val deferredIds = previous.deferredIds.toMutableSet()
            for (item in unavailable) {
                known[item.id] =
                    mergeCloudItems(listOfNotNull(known[item.id]), listOf(item)).single().withoutLocalPaths()
                deferredIds += item.id
                clock = maxOf(clock, item.revision)
            }
            authorize()
            writeLedger(previous.copy(clock = clock, items = known.values.toList(), deferredIds = deferredIds.toSet()))
            for ((item, value) in validated) {
                if (item.isActivity() && settings.settings.first().historySessionOnly) continue
                val local = known[item.id]
                val winner = mergeCloudItems(listOfNotNull(local), listOf(item)).single()
                if (winner.withoutLocalPaths() != item.withoutLocalPaths()) continue
                authorize()
                applyValue(item, value)
                deferredIds.remove(item.id)
                // Each successfully applied entity is durable before acknowledging the next one.
                known[item.id] = item.withoutLocalPaths()
                clock = maxOf(clock, item.revision)
                writeLedger(
                    previous.copy(clock = clock, items = known.values.toList(), deferredIds = deferredIds.toSet()),
                )
            }
        }
    }

    private suspend fun applyValue(
        item: CloudSyncItem,
        value: Any,
    ) {
        when (value) {
            is AppSettings -> if (!item.deleted) applyPreferences(value)
            is BackupBoardRef -> applyBoard(item, value)
            is BackupBookmark -> applyBookmark(item, value)
            is BackupHistoryEntry -> applyHistory(item, value)
            is ThreadMemory -> if (item.deleted) memory.forget(value.key) else memory.update(value.key) { value }
            is MediaLibraryRecord -> applyMedia(item, value)
        }
    }

    private suspend fun applyPreferences(value: AppSettings) {
        settings.restorePortableSettings(value, beforeEach = ::authorize, preserveLocalPrivacy = true)
    }

    private suspend fun applyBoard(
        item: CloudSyncItem,
        value: BackupBoardRef,
    ) {
        val provider = ProviderId(value.providerId)
        val board = BoardId(value.boardId)
        if (item.id.startsWith("follow/")) {
            boardPreferences.setSubscribedBoard(provider, board, !item.deleted)
        } else {
            boardPreferences.setFavoriteBoard(provider, board, !item.deleted)
        }
    }

    private suspend fun applyBookmark(
        item: CloudSyncItem,
        value: BackupBookmark,
    ) {
        val key = ThreadKey(ProviderId(value.providerId), BoardId(value.boardId), ThreadId(value.threadId))
        if (item.deleted) {
            bookmarks.removeBookmark(key)
        } else {
            bookmarks.addBookmark(
                Bookmark(
                    key,
                    value.title,
                    safeThumbnail(value.providerId, value.thumbnailUrl),
                    value.createdAtMillis,
                    value.isWatched,
                    value.lastSeenReplyCount,
                    value.latestReplyCount,
                    value.isThreadDead,
                    value.availability,
                    value.statusCheckedAtMillis,
                ),
            )
        }
    }

    private suspend fun applyHistory(
        item: CloudSyncItem,
        value: BackupHistoryEntry,
    ) {
        if (item.deleted) {
            history.remove(value.key)
        } else {
            val entry =
                value
                    .copy(
                        thumbnailUrl = safeThumbnail(value.key.provider.value, value.thumbnailUrl),
                    ).toHistoryEntry()
            history.record(entry)
            entry.lastReadPostId?.let { history.updateScrollPosition(entry.key, it, entry.lastReadOffsetPx) }
        }
    }

    private suspend fun applyMedia(
        item: CloudSyncItem,
        value: MediaLibraryRecord,
    ) {
        if (item.deleted) {
            val old = library.get(value.id)
            enqueueFileCleanup(listOfNotNull(old?.storageKey, old?.thumbnailStorageKey))
            library.remove(value.id)
            drainFileCleanup()
        } else if (item.assetPath != null) {
            importMedia(item, value)
        } else {
            val old = library.get(value.id)
            check(old != null && files.contains(old.storageKey)) { "The cloud media file is missing" }
            library.put(value.copy(storageKey = old.storageKey, thumbnailStorageKey = old.thumbnailStorageKey))
        }
    }

    private suspend fun importMedia(
        item: CloudSyncItem,
        value: MediaLibraryRecord,
    ) {
        val path = checkNotNull(item.assetPath)
        // A new revision never replaces bytes still referenced by previous metadata.
        val storage = cloudRevisionMediaKey(item)
        val thumbnail = item.thumbnailAssetPath?.let { "$storage.thumb" }
        val old = library.get(value.id)
        var committed = false
        try {
            files.importFile(storage, path)
            item.thumbnailAssetPath?.let { files.importFile(checkNotNull(thumbnail), it) }
            authorize()
            library.put(value.copy(storageKey = storage, thumbnailStorageKey = thumbnail))
            committed = true
        } finally {
            if (!committed) withContext(NonCancellable) { cleanupUncommittedMedia(storage, thumbnail, old) }
        }
        enqueueFileCleanup(
            listOfNotNull(
                old?.storageKey?.takeIf { it != storage },
                old?.thumbnailStorageKey?.takeIf { it != thumbnail },
            ),
        )
        assets.discardIncoming(path)
        item.thumbnailAssetPath?.let { assets.discardIncoming(it) }
        drainFileCleanup()
    }

    private suspend fun cleanupUncommittedMedia(
        storage: String,
        thumbnail: String?,
        previous: MediaLibraryRecord?,
    ) {
        if (previous?.storageKey != storage) files.remove(storage)
        if (thumbnail != null && previous?.thumbnailStorageKey != thumbnail) files.remove(thumbnail)
    }

    private suspend fun writeLedger(state: CloudRevisionState) {
        revisions.write(state.copy(pendingFileDeletes = revisions.read().pendingFileDeletes))
    }

    private suspend fun enqueueFileCleanup(keys: List<String>) {
        if (keys.isEmpty()) return
        require(keys.all(::validManagedMediaKey))
        val state = revisions.read()
        revisions.write(state.copy(pendingFileDeletes = state.pendingFileDeletes + keys))
    }

    private suspend fun drainFileCleanup() {
        val state = revisions.read()
        if (state.pendingFileDeletes.isEmpty()) return
        val referenced =
            library
                .observeAll()
                .first()
                .flatMap {
                    listOfNotNull(
                        it.storageKey,
                        it.thumbnailStorageKey,
                    )
                }.toSet()
        val completed = mutableSetOf<String>()
        for (key in state.pendingFileDeletes) {
            if (key in referenced) continue
            require(validManagedMediaKey(key))
            authorize()
            files.remove(key)
            completed += key
        }
        revisions.write(state.copy(pendingFileDeletes = state.pendingFileDeletes - completed))
    }

    private fun validateEnvelope(item: CloudSyncItem) {
        require(
            item.revision in 0 until Long.MAX_VALUE - MAX_DEFERRED_ITEMS &&
                item.id.length <= MAX_CLOUD_ID_LENGTH &&
                item.payload.length <= MAX_CLOUD_PAYLOAD_LENGTH &&
                item.origin.length <= MAX_CLOUD_ORIGIN_LENGTH,
        )
    }

    /** Unknown source/feature records remain opaque until this installation can apply them. */
    private fun isUnavailable(item: CloudSyncItem): Boolean {
        val provider =
            when (item.id.substringBefore('/')) {
                "preferences" -> return false
                "follow", "favorite" -> json.decodeFromString<BackupBoardRef>(item.payload).providerId
                "bookmark" -> json.decodeFromString<BackupBookmark>(item.payload).providerId
                "history" ->
                    json
                        .decodeFromString<BackupHistoryEntry>(item.payload)
                        .key.provider.value
                "memory" ->
                    json
                        .decodeFromString<ThreadMemory>(item.payload)
                        .key.provider.value
                "media" -> json.decodeFromString<MediaLibraryRecord>(item.payload).providerId
                else -> return true
            }
        return provider !in providers
    }

    override suspend fun rememberSynced(items: List<CloudSyncItem>) {
        mutex.withLock {
            // Re-capture catches user edits made while the upload was suspended. Never mark a
            // different local payload as sent, or an interrupted transfer as complete.
            capture()
        }
    }

    private suspend fun validate(item: CloudSyncItem): Pair<CloudSyncItem, Any> {
        validateEnvelope(item)
        val kind = item.id.substringBefore('/')
        val value: Any
        val key: String
        when (kind) {
            "preferences" -> {
                value = json.decodeFromString<AppSettings>(item.payload)
                key = "portable"
            }
            "follow", "favorite" -> {
                value = json.decodeFromString<BackupBoardRef>(item.payload)
                validateBoard(value.providerId, value.boardId)
                key = boardKey(value)
            }
            "bookmark" -> {
                value = json.decodeFromString<BackupBookmark>(item.payload)
                validateBoard(value.providerId, value.boardId)
                require(value.threadId > 0)
                key = "${value.providerId}/${value.boardId}/${value.threadId}"
            }
            "history" -> {
                value = json.decodeFromString<BackupHistoryEntry>(item.payload)
                validateThread(value.key)
                key = threadKey(value.key)
            }
            "memory" -> {
                value = json.decodeFromString<ThreadMemory>(item.payload)
                validateThread(value.key)
                require(
                    value.offsetPx >= 0 &&
                        value.foldedPaths.size <= MAX_CLOUD_READING_IDS &&
                        value.seenPostIds.size <= MAX_CLOUD_READING_IDS,
                )
                key = threadKey(value.key)
            }
            "media" -> {
                value = json.decodeFromString<MediaLibraryRecord>(item.payload)
                validateMedia(item, value)
                key = "${value.providerId}/${value.id}"
            }
            else -> error("Unsupported cloud entity")
        }
        require(
            item.id == "$kind/${key.encodeUtf8().base64Url()}",
        ) { "Cloud entity identity does not match its payload" }
        return item to value
    }

    private suspend fun validateMedia(
        item: CloudSyncItem,
        value: MediaLibraryRecord,
    ) {
        require(providers.containsKey(value.providerId) && value.byteCount in 0..MAX_CLOUD_MEDIA_BYTES)
        require(value.storageKey == cloudMediaKey(value.id) && validManagedMediaKey(value.storageKey))
        require(value.thumbnailStorageKey == null || value.thumbnailStorageKey == cloudThumbnailKey(value.id))
        require(value.sourceUrl.isBlank() || providers[value.providerId]?.isOwnMediaUrl(value.sourceUrl) == true)
        if (!item.deleted) {
            item.assetPath?.let { assets.validateIncoming(it, value.byteCount.takeIf { count -> count > 0 }) }
            item.thumbnailAssetPath?.let { assets.validateIncoming(it) }
        }
    }

    private fun safeThumbnail(
        provider: String,
        url: String?,
    ): String? = url?.takeIf { providers[provider]?.isOwnMediaUrl(it) == true }

    private fun validateBoard(
        provider: String,
        board: String,
    ) {
        require(
            providers.containsKey(provider) &&
                board.isNotBlank() &&
                board.length <= 100 &&
                board.all { it.isLetterOrDigit() || it == '_' || it == '-' },
        )
    }

    private fun validateThread(key: ThreadKey) {
        validateBoard(key.provider.value, key.board.value)
        require(key.thread.value > 0)
    }

    private fun authorize() {
        check(isAuthorized()) { "Unlock Orbin before syncing" }
    }
}

private class CloudSnapshotBuilder(
    private val previous: CloudRevisionState,
    clockFloor: Long,
) {
    var clock = maxOf(previous.clock, clockFloor)
        private set
    val items = mutableListOf<CloudSyncItem>()
    private val before = previous.items.associateBy { it.id }

    fun record(
        kind: String,
        key: String,
        payload: String,
        asset: String? = null,
        thumbnail: String? = null,
    ) {
        val id = "$kind/${key.encodeUtf8().base64Url()}"
        val old = before[id]
        items +=
            if (old != null && !old.deleted && old.payload == payload) {
                old.copy(assetPath = asset, thumbnailAssetPath = thumbnail)
            } else {
                CloudSyncItem(id, ++clock, previous.origin, payload, assetPath = asset, thumbnailAssetPath = thumbnail)
            }
    }

    fun delete(
        old: CloudSyncItem,
        payload: String = old.payload,
    ) {
        items +=
            if (old.deleted && old.payload == payload) {
                old
            } else {
                old.copy(
                    revision = ++clock,
                    origin = previous.origin,
                    payload = payload,
                    deleted = true,
                    assetPath = null,
                    thumbnailAssetPath = null,
                )
            }
    }
}

private fun boardKey(ref: BackupBoardRef): String = "${ref.providerId}/${ref.boardId}"

private fun threadKey(key: ThreadKey): String = "${key.provider.value}/${key.board.value}/${key.thread.value}"

private fun CloudSyncItem.isActivity(): Boolean = id.startsWith("history/") || id.startsWith("memory/")

private fun cloudMediaKey(id: String): String = "cloud-${id.encodeUtf8().sha256().hex()}.media"

private fun cloudThumbnailKey(id: String): String = "cloud-${id.encodeUtf8().sha256().hex()}.thumb"

private fun cloudRevisionMediaKey(item: CloudSyncItem): String =
    "cloud-${"${item.id}/${item.revision}/${item.origin}".encodeUtf8().sha256().hex()}.media"

private fun CloudSyncItem.withoutLocalPaths(): CloudSyncItem = copy(assetPath = null, thumbnailAssetPath = null)

/** These device privacy choices must not bounce between devices or accidentally opt another in. */
private fun AppSettings.forCloud(): AppSettings =
    portableExport().copy(
        nsfwEachSession = false,
        hideNsfwBoards = false,
        coverNsfwMedia = false,
        coverViolentMedia = AppSettings.Default.coverViolentMedia,
        deepMediaScan = AppSettings.Default.deepMediaScan,
        historySessionOnly = false,
        saveBrowsingHistory = false,
        saveSearchHistory = false,
        askBeforePhotos = false,
        screenPrivacyEnabled = false,
        dohProvider = AppSettings.Default.dohProvider,
        strictDohEnabled = false,
    )

internal const val MAX_CLOUD_MEDIA_BYTES: Long = 1_073_741_824L

private const val MAX_DEFERRED_ITEMS = 100_000L

private const val MAX_CLOUD_ID_LENGTH = 1024
private const val MAX_CLOUD_PAYLOAD_LENGTH = 1_048_576
private const val MAX_CLOUD_ORIGIN_LENGTH = 128

private const val MAX_CLOUD_READING_IDS = 100_000

private fun AppSettings.activityPolicy(): Pair<Boolean, Boolean> = saveBrowsingHistory to historySessionOnly
