package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.BackupBoardRef
import com.orbin.core.model.BackupBookmark
import com.orbin.core.model.BackupDocument
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.decodeBackupDocument
import com.orbin.core.model.portableExport
import com.orbin.core.model.toBackupHistory
import com.orbin.core.model.toHistoryEntry
import com.orbin.data.backup.PortableBackupArchive
import com.orbin.data.util.defaultFileSystem
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.ManagedMediaFiles
import com.orbin.domain.repository.MediaLibraryRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.repository.ThreadMemoryRepository
import com.orbin.domain.repository.restorePortableSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.time.Clock

/**
 * The platform half of a backup: hand a file to the share sheet (Save to Files, AirDrop, …) and
 * pick one to restore. Each returns once the reader is done with the sheet.
 */
interface BackupFiles {
    /** Offers [contents] as a file named [fileName]; true when the reader saved or sent it. */
    suspend fun share(
        fileName: String,
        contents: String,
    ): Boolean

    /** The text of a backup file the reader picked, or null when they cancelled. */
    suspend fun pick(): String?

    val supportsArchives: Boolean get() = false

    /** The caller owns and deletes these local files after the system sheet completes. */
    suspend fun shareFile(
        fileName: String,
        sourcePath: String,
    ): Boolean = error("Archive sharing is unavailable")

    suspend fun pickFile(): String? = error("Archive picking is unavailable")
}

/** What the settings rows say about the last export or import. */
sealed interface BackupState {
    data object Idle : BackupState

    data object Working : BackupState

    data object Exported : BackupState

    data class Imported(
        val boards: Int,
        val bookmarks: Int,
        val mediaFiles: Int = 0,
    ) : BackupState

    data class Failed(
        val message: String,
    ) : BackupState
}

/**
 * Export and import in Android's backup format ([BackupDocument]), so a file exported on either
 * platform restores on the other. It restores what Android's `BackupService` restores, the same
 * way: settings, then followed and favourite boards and bookmarks added — never removed — so an
 * import merges into what is here. Saved searches are Android's alone; iOS keeps and exports none,
 * and skips them on import. The app lock is never restored, as on Android: a backup file must not
 * weaken, or unexpectedly turn on, this device's lock.
 */
class IosBackup(
    private val settings: SettingsRepository,
    private val boardPreferences: BoardPreferencesRepository,
    private val bookmarks: BookmarkRepository,
    private val providers: List<ProviderId>,
    private val files: BackupFiles,
    private val scope: CoroutineScope,
    private val appVersion: String,
    private val isAuthorized: () -> Boolean,
    private val now: () -> String = { Clock.System.now().toString() },
    /**
     * Whether an imported bookmark thumbnail is on that provider's own https media hosts. A backup
     * file can come from anyone, so anything else is dropped rather than fetched. Defaults to
     * dropping every imported thumbnail.
     */
    private val isOwnMediaUrl: (ProviderId, String) -> Boolean = { _, _ -> false },
    private val history: HistoryRepository? = null,
    private val threadMemory: ThreadMemoryRepository? = null,
    private val mediaLibrary: MediaLibraryRepository? = null,
    private val managedMediaFiles: ManagedMediaFiles? = null,
    private val temporaryDirectory: String? = null,
) {
    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()
    private var pending: Job? = null
    private var generation = 0L

    /** Writes a backup and opens the share sheet for it. */
    fun export() =
        perform {
            if (files.supportsArchives) {
                val suffix = Random.nextLong().toString(RANDOM_NAME_RADIX)
                val directory = checkNotNull(temporaryDirectory).toPath() / "backup-$suffix"
                val path = directory / fileName().removeSuffix(".json").plus(".zip")
                try {
                    archive().export(path, exportDocument(), ::requireAuthorization)
                    requireAuthorization()
                    if (files.shareFile(path.name, path.toString())) BackupState.Exported else BackupState.Idle
                } finally {
                    defaultFileSystem.deleteRecursively(directory, mustExist = false)
                }
            } else {
                val contents = exportToJson()
                requireAuthorization()
                if (files.share(fileName(), contents)) BackupState.Exported else BackupState.Idle
            }
        }

    /** Asks for a backup file and restores it. */
    fun import() =
        perform {
            if (files.supportsArchives) {
                val path = files.pickFile()?.toPath() ?: return@perform BackupState.Idle
                try {
                    val restored =
                        archive().restore(
                            path,
                            acceptsProvider = { it in providers.map { provider -> provider.value } },
                            restoreMetadata = { importDocument(it).getOrThrow() },
                            authorize = ::requireAuthorization,
                        )
                    return@perform BackupState.Imported(
                        restored.metadata.first,
                        restored.metadata.second,
                        restored.mediaFiles,
                    )
                } finally {
                    defaultFileSystem.delete(path, mustExist = false)
                }
            }
            val text = files.pick() ?: return@perform BackupState.Idle
            requireAuthorization()
            importFromJson(text).fold(
                onSuccess = { BackupState.Imported(boards = it.first, bookmarks = it.second) },
                onFailure = { BackupState.Failed(it.message ?: "Not an Orbin backup") },
            )
        }

    suspend fun exportToJson(): String = json.encodeToString(BackupDocument.serializer(), exportDocument())

    suspend fun exportDocument(): BackupDocument {
        val document =
            BackupDocument(
                exportedAt = now(),
                exportedByAppVersion = appVersion,
                settings = settings.settings.first().portableExport(),
                subscribedBoards = providers.flatMap { it.refs(subscribed = true) },
                favoriteBoards = providers.flatMap { it.refs(subscribed = false) },
                bookmarks = bookmarks.observeBookmarks().first().map { it.toBackup() },
                history =
                    history
                        ?.observeAllEntries()
                        ?.first()
                        .orEmpty()
                        .map { it.toBackupHistory() },
                threadMemory = threadMemory?.observeAll()?.first().orEmpty(),
                savedMedia = mediaLibrary?.observeAll()?.first().orEmpty(),
            )
        return document
    }

    /** Restores [backupJson]; the result counts the boards and bookmarks restored. */
    suspend fun importFromJson(backupJson: String): Result<Pair<Int, Int>> =
        runCatching {
            requireAuthorization()
            require(backupJson.length <= MAX_BACKUP_JSON_CHARS) {
                "Backup file is too large to import."
            }
            val document = json.decodeBackupDocument(backupJson)
            require(document.formatVersion <= BackupDocument.CURRENT_FORMAT_VERSION) {
                "This backup was written by a newer version of Orbin (format ${document.formatVersion})."
            }
            importDocument(document).getOrThrow()
        }.onFailure { if (it is CancellationException) throw it }

    suspend fun importDocument(document: BackupDocument): Result<Pair<Int, Int>> =
        runCatching {
            requireAuthorization()
            require(document.formatVersion in 1..BackupDocument.CURRENT_FORMAT_VERSION) { "Unsupported backup version" }
            require(
                document.history.size <= MAX_READING_RECORDS && document.threadMemory.size <= MAX_READING_RECORDS,
            ) { "Too many backup records" }
            restoreSettings(
                document.settings.copy(
                    activeProviderId =
                        document.settings.activeProviderId
                            .takeIf { value ->
                                providers.any {
                                    it.value ==
                                        value
                                }
                            }.orEmpty(),
                ),
            )
            val known = providers.map { it.value }.toSet()
            val subscribed =
                document.subscribedBoards.filter {
                    it.providerId in known && isValidBoardSlug(it.boardId)
                }
            subscribed.forEach {
                requireAuthorization()
                boardPreferences.setSubscribedBoard(
                    ProviderId(it.providerId),
                    BoardId(it.boardId),
                    true,
                )
            }
            val favourites =
                document.favoriteBoards.filter {
                    it.providerId in known && isValidBoardSlug(it.boardId)
                }
            favourites.forEach {
                requireAuthorization()
                boardPreferences.setFavoriteBoard(
                    ProviderId(it.providerId),
                    BoardId(it.boardId),
                    true,
                )
            }
            val restored =
                document.bookmarks.filter {
                    it.providerId in known && isValidBoardSlug(it.boardId) && it.threadId > 0
                }
            restored.forEach {
                requireAuthorization()
                bookmarks.addBookmark(it.toBookmark(isOwnMediaUrl))
            }
            document.history
                .filter {
                    it.key.provider.value in known &&
                        isValidBoardSlug(it.key.board.value) &&
                        it.key.thread.value > 0
                }.forEach { row ->
                    requireAuthorization()
                    history?.record(
                        row
                            .copy(
                                thumbnailUrl =
                                    row.thumbnailUrl?.takeIf {
                                        isOwnMediaUrl(row.key.provider, it)
                                    },
                            ).toHistoryEntry(),
                    )
                }
            document.threadMemory
                .filter {
                    it.key.provider.value in known &&
                        isValidBoardSlug(it.key.board.value) &&
                        it.key.thread.value > 0
                }.forEach { memory ->
                    requireAuthorization()
                    threadMemory?.update(memory.key) { memory }
                }
            subscribed.size to restored.size
        }.onFailure { if (it is CancellationException) throw it }

    private fun archive(): PortableBackupArchive =
        PortableBackupArchive(
            checkNotNull(mediaLibrary) { "Saved media storage is unavailable" },
            checkNotNull(managedMediaFiles) { "Saved files are unavailable" },
            checkNotNull(temporaryDirectory) { "Backup storage is unavailable" }.toPath() / "backup-work",
        )

    private fun perform(block: suspend () -> BackupState) {
        if (_state.value == BackupState.Working) return
        if (!isAuthorized()) return
        val session = ++generation
        _state.value = BackupState.Working
        pending =
            scope.launch {
                try {
                    requireAuthorization()
                    val result =
                        runCatching { block() }
                            .onFailure { if (it is CancellationException) throw it }
                            .getOrElse { BackupState.Failed(it.message ?: "Something went wrong") }
                    if (session == generation) _state.value = result
                } finally {
                    if (session == generation && _state.value == BackupState.Working) _state.value = BackupState.Idle
                }
            }
    }

    /** Relocking revokes this operation even if the owner unlocks again before a callback arrives. */
    fun cancelPending() {
        generation++
        pending?.cancel()
        pending = null
        _state.value = BackupState.Idle
    }

    private suspend fun requireAuthorization() {
        currentCoroutineContext().ensureActive()
        check(isAuthorized()) { "Unlock Orbin before using backups" }
    }

    private suspend fun ProviderId.refs(subscribed: Boolean): List<BackupBoardRef> {
        val boards =
            if (subscribed) {
                boardPreferences.observeSubscribedBoards(
                    this,
                )
            } else {
                boardPreferences.observeFavoriteBoards(this)
            }
        return boards.first().map { BackupBoardRef(providerId = value, boardId = it.value) }
    }

    private suspend fun restoreSettings(restored: AppSettings) {
        settings.restorePortableSettings(restored, beforeEach = ::requireAuthorization)
        requireAuthorization()
        settings.setOnboardingCompleted(true)
    }

    private fun fileName(): String = "orbin-backup-${now().take(DATE_LENGTH)}.json"

    private companion object {
        const val RANDOM_NAME_RADIX = 16
        const val MAX_READING_RECORDS = 100_000
        const val DATE_LENGTH = 10 // yyyy-MM-dd
        const val MAX_BACKUP_JSON_CHARS = 5 * 1024 * 1024
        const val MAX_BOARD_SLUG_LENGTH = 32
        val VALID_BOARD_SLUG = Regex("""^[A-Za-z0-9_-]{1,$MAX_BOARD_SLUG_LENGTH}$""")

        fun isValidBoardSlug(value: String): Boolean = VALID_BOARD_SLUG.matches(value)

        val json =
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }
}

private fun Bookmark.toBackup(): BackupBookmark =
    BackupBookmark(
        providerId = key.provider.value,
        boardId = key.board.value,
        threadId = key.thread.value,
        title = title,
        thumbnailUrl = thumbnailUrl,
        createdAtMillis = createdAtMillis,
        isWatched = isWatched,
        lastSeenReplyCount = lastSeenReplyCount,
        latestReplyCount = latestReplyCount,
        isThreadDead = isThreadDead,
        availability = availability,
        statusCheckedAtMillis = statusCheckedAtMillis,
    )

private fun BackupBookmark.toBookmark(isOwnMediaUrl: (ProviderId, String) -> Boolean): Bookmark =
    Bookmark(
        key = ThreadKey(ProviderId(providerId), BoardId(boardId), ThreadId(threadId)),
        title = title,
        thumbnailUrl = thumbnailUrl?.takeIf { isOwnMediaUrl(ProviderId(providerId), it) },
        createdAtMillis = createdAtMillis,
        isWatched = isWatched,
        lastSeenReplyCount = lastSeenReplyCount,
        latestReplyCount = latestReplyCount,
        isThreadDead = isThreadDead,
        availability = availability,
        statusCheckedAtMillis = statusCheckedAtMillis,
    )
